package de.bimalo.homeauto.control.heatingcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for AutomaticHeatingPolicy.
 * Covers the pure surplus/temperature/battery-priority calculation, isolated
 * from device I/O and from the manual-mode coordination in HeatingControlService.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticHeatingPolicyTest {

    @Mock
    private HeatingControlConfig config;

    @InjectMocks
    private AutomaticHeatingPolicy automaticPolicy;

    @BeforeEach
    void setUp() {
        lenient().when(config.minSurplusPower()).thenReturn(100);
        lenient().when(config.maxHeatingPower()).thenReturn(2900);
        lenient().when(config.batteryPriorityThreshold()).thenReturn(60);
        lenient().when(config.batteryReservedPower()).thenReturn(1000);
        lenient().when(config.solarPowerReductionPercent()).thenReturn(0);
        lenient().when(config.powerIncreaseSmoothingWindow()).thenReturn(Duration.ofSeconds(150));
        lenient().when(config.minPowerChangeThreshold()).thenReturn(0);
        lenient().when(config.temperatureHysteresis()).thenReturn(7.0);
    }

    // ==================== Surplus power ====================

    @Test
    void decide_whenInsufficientSurplus_shouldStopHeating() {
        // production=250, consumption=200, currentHeating=200 -> adjustedSurplus = 250 - (200-200) = 250W
        BatteryStatus batteryStatus = batteryStatus(70, 250, 200, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 200);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(250), power);
    }

    @Test
    void decide_whenSurplusBelowMinimumEvenWithCurrentPower_shouldStop() {
        // production=100, consumption=300, currentHeating=150 -> 100 - (300-150) = -50W -> capped to 0
        BatteryStatus batteryStatus = batteryStatus(70, 100, 300, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 150);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ZERO, power);
    }

    @Test
    void decide_whenSufficientSurplus_shouldAdjustHeating() {
        // production=1900, consumption=200, currentHeating=200 -> 1900 - (200-200) = 1900W
        BatteryStatus batteryStatus = batteryStatus(70, 1900, 200, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 200);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(1900), power);
    }

    @Test
    void decide_whenSurplusExceedsMaximum_shouldLimitToMaxPower() {
        // production=3700, consumption=200, currentHeating=200 -> 3700W, limited to max 2900W
        BatteryStatus batteryStatus = batteryStatus(70, 3700, 200, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 200);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(2900), power);
    }

    @Test
    void decide_whenSurplusExactlyAtMaximum_shouldNotLimit() {
        BatteryStatus batteryStatus = batteryStatus(70, 2900, 200, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 200);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(2900), power);
    }

    @Test
    void decide_withZeroCurrentHeatingPower_shouldCalculateCorrectly() {
        BatteryStatus batteryStatus = batteryStatus(70, 1200, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(1200), power);
    }

    @Test
    void decide_withMinimalValidSurplus_shouldHeat() {
        BatteryStatus batteryStatus = batteryStatus(70, 100, 50, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 50);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(100), power);
    }

    @Test
    void decide_justBelowMinimumSurplus_shouldStop() {
        BatteryStatus batteryStatus = batteryStatus(70, 99, 50, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 50);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ZERO, power);
    }

    @Test
    void decide_whenBatteryDischarging_shouldStopHeating() {
        // PV: 677W, consumption: 3590W, currently heating 1000W, battery discharging 1644W
        BatteryStatus batteryStatus = BatteryStatus.builder()
                .measuredAt(Instant.now())
                .productionPower(Power.ofWatts(677))
                .consumptionPower(Power.ofWatts(3590))
                .batteryPower(Power.ofWatts(-1644))
                .gridPower(Power.ofWatts(1269))
                .batteryStateOfCharge(Percentage.of(70))
                .build();
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 1000);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ZERO, power);
    }

    // ==================== Battery priority ====================

    @Test
    void decide_whenBatteryPriorityActiveAndSocBelowThreshold_shouldReservePower() {
        // production=2000, consumption=0 -> 2000W, minus 1000W reserved = 1000W
        BatteryStatus batteryStatus = batteryStatus(50, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ofWatts(1000), power);
    }

    @Test
    void decide_whenBatteryPriorityActiveAndSocAboveThreshold_shouldNotReservePower() {
        BatteryStatus batteryStatus = batteryStatus(70, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ofWatts(2000), power);
    }

    @Test
    void decide_whenBatteryPriorityInactive_shouldNotReservePower() {
        // batteryPriorityActive=false: full surplus available despite low SOC
        BatteryStatus batteryStatus = batteryStatus(50, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(2000), power);
    }

    @Test
    void decide_whenBatteryPriorityReservesTooMuch_shouldStopHeating() {
        // production=800, consumption=0 -> 800W, minus 1000W reserved -> negative -> 0W
        BatteryStatus batteryStatus = batteryStatus(50, 800, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ZERO, power);
    }

    @Test
    void decide_whenBatteryPriorityActiveAtExactThreshold_shouldNotReservePower() {
        BatteryStatus batteryStatus = batteryStatus(60, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ofWatts(2000), power);
    }

    @Test
    void decide_whenBatteryPriorityJustBelowThreshold_shouldReservePower() {
        BatteryStatus batteryStatus = batteryStatus(59, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ofWatts(1000), power);
    }

    @Test
    void decide_whenBatteryPriorityActive_maxPowerStillApplies() {
        when(config.maxHeatingPower()).thenReturn(3000);
        // production=5000 -> minus 1000 reserved = 4000W, limited to configured max 3000W
        BatteryStatus batteryStatus = batteryStatus(50, 5000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ofWatts(3000), power);
    }

    // ==================== Temperature hysteresis ====================

    @Test
    void decide_whenTargetTemperatureReached_shouldStopHeating() {
        HeatingRodStatus rodStatus = rodStatus(70.0, 68.0, 500);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus(70, 0, 0, 0), false).power();

        assertEquals(Power.ZERO, power);
    }

    @Test
    void decide_inCoolingMode_temperatureAboveRestartThreshold_shouldNotHeat() {
        // First cycle: reach target (70°C, target 68°C) -> enters cooling mode
        automaticPolicy.decide(rodStatus(70.0, 68.0, 500), batteryStatus(70, 0, 0, 0), false);

        // Second cycle: temperature dropped to 65°C, still above restart threshold of 61°C (68-7)
        BatteryStatus batteryStatus = batteryStatus(70, 2000, 0, 0);
        Power power = automaticPolicy.decide(rodStatus(65.0, 68.0, 500), batteryStatus, false).power();

        assertEquals(Power.ZERO, power);
    }

    @Test
    void decide_inCoolingMode_temperatureBelowRestartThreshold_shouldResumeHeating() {
        // First cycle: reach target (70°C, target 68°C) -> enters cooling mode
        automaticPolicy.decide(rodStatus(70.0, 68.0, 500), batteryStatus(70, 0, 0, 0), false);

        // Second cycle: temperature dropped to 60°C, below restart threshold of 61°C (68-7)
        BatteryStatus batteryStatus = batteryStatus(70, 2000, 0, 0);
        Power power = automaticPolicy.decide(rodStatus(60.0, 68.0, 0), batteryStatus, false).power();

        assertEquals(Power.ofWatts(2000), power);
    }

    @Test
    void decide_withHysteresis_normalHeatingWhenFarBelowTarget() {
        BatteryStatus batteryStatus = batteryStatus(70, 1500, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(1500), power);
    }

    @Test
    void decide_hysteresisWithCustomValue_shouldUseConfiguredHysteresis() {
        when(config.temperatureHysteresis()).thenReturn(5.0);

        // First cycle: reach target (70°C, target 68°C) -> enters cooling mode
        automaticPolicy.decide(rodStatus(70.0, 68.0, 500), batteryStatus(70, 0, 0, 0), false);

        // 64°C is above the restart threshold of 63°C (68-5) -> still cooling
        Power stillCooling = automaticPolicy
                .decide(rodStatus(64.0, 68.0, 0), batteryStatus(70, 2000, 0, 0), false)
                .power();
        assertEquals(Power.ZERO, stillCooling);

        // 62°C is below the restart threshold -> resumes heating
        Power resumed = automaticPolicy
                .decide(rodStatus(62.0, 68.0, 0), batteryStatus(70, 2000, 0, 0), false)
                .power();
        assertEquals(Power.ofWatts(2000), resumed);
    }

    // ==================== Solar power reduction ====================

    @Test
    void decide_withSolarPowerReduction_shouldReduceAvailablePower() {
        when(config.solarPowerReductionPercent()).thenReturn(5);
        BatteryStatus batteryStatus = batteryStatus(70, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(1900), power);
    }

    @Test
    void decide_withHighReduction_shouldReduceSignificantly() {
        when(config.solarPowerReductionPercent()).thenReturn(10);
        BatteryStatus batteryStatus = batteryStatus(70, 2000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(1800), power);
    }

    @Test
    void decide_withReductionAndMaxPower_shouldApplyReductionBeforeMax() {
        when(config.solarPowerReductionPercent()).thenReturn(5);
        // 4000W - 5% = 3800W, then limited to max 2900W
        BatteryStatus batteryStatus = batteryStatus(70, 4000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ofWatts(2900), power);
    }

    @Test
    void decide_withReductionResultBelowMinimum_shouldNotHeat() {
        when(config.solarPowerReductionPercent()).thenReturn(40);
        // 150W - 40% = 90W < 100W minimum
        BatteryStatus batteryStatus = batteryStatus(70, 150, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, false).power();

        assertEquals(Power.ZERO, power);
    }

    @Test
    void decide_withReductionAndBatteryPriority_shouldApplyBoth() {
        when(config.solarPowerReductionPercent()).thenReturn(5);
        // 3000W, battery priority reserves 1000W -> 2000W, then 5% reduction -> 1900W
        BatteryStatus batteryStatus = batteryStatus(50, 3000, 0, 0);
        HeatingRodStatus rodStatus = rodStatus(50.0, 68.0, 0);

        Power power = automaticPolicy.decide(rodStatus, batteryStatus, true).power();

        assertEquals(Power.ofWatts(1900), power);
    }

    private HeatingRodStatus rodStatus(double currentTempCelsius, double targetTempCelsius, long currentPowerWatts) {
        return new HeatingRodStatus(
                Temperature.ofCelsius(currentTempCelsius),
                Temperature.ofCelsius(targetTempCelsius),
                Power.ofWatts(currentPowerWatts),
                Instant.now());
    }

    private BatteryStatus batteryStatus(int socPercent, long productionWatts, long consumptionWatts, long batteryWatts) {
        return BatteryStatus.builder()
                .measuredAt(Instant.now())
                .productionPower(Power.ofWatts(productionWatts))
                .consumptionPower(Power.ofWatts(consumptionWatts))
                .batteryPower(Power.ofWatts(batteryWatts))
                .gridPower(Power.ZERO)
                .batteryStateOfCharge(Percentage.of(socPercent))
                .build();
    }
}

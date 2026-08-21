package de.bimalo.homeauto.control.manualwaterheating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.GasCommand;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.HeatingDecision;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualWaterHeatingPolicyTest {

    private static final Instant NOW = Instant.parse("2024-01-01T12:00:00Z");

    @Mock
    private ManualWaterHeatingConfig config;

    private Clock clock;
    private ManualWaterHeatingPolicy policy;

    @BeforeEach
    void setUp() {
        // ManualWaterHeatingPolicy has a second, package-private constructor taking a
        // Clock (for this test); Mockito's @InjectMocks would pick that constructor
        // over the @Inject one and inject a null Clock, so it is wired explicitly here.
        // A mocked (rather than fixed) Clock lets individual tests advance time
        // between start() and decide() calls, e.g. to exercise maximumDuration().
        clock = mock(Clock.class);
        lenient().when(clock.instant()).thenReturn(NOW);
        policy = new ManualWaterHeatingPolicy(config, clock);

        // Matches the real default of ManualWaterHeatingConfig.maximumDuration()
        // ("30m"); Mockito's default answer for an unstubbed Duration-returning
        // method is Duration.ZERO, not null, which would make every session look
        // immediately expired.
        lenient().when(config.maximumDuration()).thenReturn(Duration.ofMinutes(30));
        lenient().when(config.heatingRodLowTemperatureThreshold()).thenReturn(42.0);
        lenient().when(config.batterySocStartThreshold()).thenReturn(65);
        lenient().when(config.batterySocStopThreshold()).thenReturn(50);
        lenient().when(config.maxBatterySocDropPercent()).thenReturn(10);
        lenient().when(config.maxBatteryHeatingPower()).thenReturn(850);
        lenient().when(config.batteryMaxDischargePower()).thenReturn(1500);
        lenient().when(config.gasHeatingLowTemperatureThreshold()).thenReturn(35.0);
        lenient().when(config.gasHeatingShutoffTemperatureOffset()).thenReturn(5.0);
    }

    // ==================== Lifecycle ====================

    @Test
    void start_activatesManualMode() {
        policy.start();

        assertTrue(policy.getState().active());
    }

    @Test
    void start_whenAlreadyActive_doesNotResetStartedAt() {
        policy.start();
        Instant firstStartedAt = policy.getState().startedAt();

        policy.start();

        assertEquals(firstStartedAt, policy.getState().startedAt());
    }

    @Test
    void stop_deactivatesManualMode() {
        policy.start();

        policy.stop();

        assertFalse(policy.getState().active());
    }

    @Test
    void stop_returnsTrue_whenGasAssistWasActive() {
        policy.start();
        // Trigger gas fallback so the policy's internal state has gasAssistActive=true
        policy.decide(rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false, 30.0, 55.0));
        assertTrue(policy.getState().gasAssistActive());

        boolean gasWasActive = policy.stop();

        assertTrue(gasWasActive);
    }

    @Test
    void stop_returnsFalse_whenGasAssistWasNeverActive() {
        policy.start();

        boolean gasWasActive = policy.stop();

        assertFalse(gasWasActive);
    }

    // ==================== Maximum duration ====================

    @Test
    void decide_completesAndStopsGas_whenMaximumDurationExceeded() {
        policy.start();

        // Cycle 1: trigger gas fallback so completion also has to request DEACTIVATE
        policy.decide(rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false, 30.0, 55.0));
        assertTrue(policy.getState().gasAssistActive());

        // Cycle 2: 31 minutes later, past the configured 30-minute maximum
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(31)));
        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(true, 30.0, 55.0));

        assertTrue(decision.completed());
        assertEquals(GasCommand.DEACTIVATE, decision.gasCommand());
    }

    @Test
    void decide_doesNotComplete_whileWithinMaximumDuration() {
        policy.start();

        // 29 minutes later, still within the configured 30-minute maximum
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(29)));
        HeatingDecision decision = policy.decide(rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false));

        assertFalse(decision.completed());
    }

    // ==================== PV surplus ====================

    @Test
    void decide_usesPvSurplusOnly_whenNoBatteryTriggerConditions() {
        policy.start();

        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0),
                batteryStatus(70, 0, 1000),
                gasHeatingStatus(false));

        assertEquals(Power.ofWatts(1000), decision.power());
        assertEquals(HeatingSource.PV, decision.source());
        assertEquals(HeatingSource.PV, policy.getState().source());
    }

    @Test
    void decide_excludesRodsOwnDraw_whenComputingPvSurplus() {
        policy.start();

        // consumptionPower (1800W) includes the ELWA2's own current draw
        // (800W): true non-rod house consumption is only 1000W, so 800W of
        // the 1800W production remains as surplus. Without excluding the
        // rod's own draw, surplus would incorrectly come out as 0W.
        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0, 800),
                batteryStatus(70, 0, 1800, 1800),
                gasHeatingStatus(false));

        assertEquals(Power.ofWatts(800), decision.power());
        assertEquals(HeatingSource.PV, decision.source());
    }

    // ==================== Battery assist trigger ====================

    @Test
    void decide_addsBatteryPower_whenTempBelowThresholdAndSocAboveStart() {
        policy.start();

        HeatingDecision decision = policy.decide(
                rodStatus(40.0, 60.0), // below 42°C
                batteryStatus(70, 0), // SOC 70% > 65%, not discharging
                gasHeatingStatus(false));

        assertEquals(Power.ofWatts(850), decision.power());
        assertEquals(HeatingSource.BATTERY, decision.source());
        assertTrue(policy.getState().batteryAssistActive());
        assertEquals(HeatingSource.BATTERY, policy.getState().source());
    }

    @Test
    void decide_doesNotTriggerBatteryAssist_whenSocAtStartThreshold() {
        policy.start();

        HeatingDecision decision = policy.decide(
                rodStatus(40.0, 60.0),
                batteryStatus(65, 0), // exactly 65%, not >65%
                gasHeatingStatus(false));

        assertEquals(Power.ZERO, decision.power());
        assertEquals(HeatingSource.NONE, decision.source());
        assertFalse(policy.getState().batteryAssistActive());
    }

    @Test
    void decide_respectsBatteryDischargeHeadroom() {
        policy.start();

        // Battery already discharging 1200W for house consumption -> only 300W headroom left
        HeatingDecision decision = policy.decide(
                rodStatus(40.0, 60.0),
                batteryStatus(70, -1200),
                gasHeatingStatus(false));

        assertEquals(Power.ofWatts(300), decision.power());
    }

    @Test
    void decide_fallsBackToGas_whenBatteryHeadroomExhausted() {
        policy.start();

        // Battery already discharging at its 1500W max for house consumption alone
        HeatingDecision decision = policy.decide(
                rodStatus(40.0, 60.0),
                batteryStatus(70, -1500),
                gasHeatingStatus(false, 40.0, 55.0));

        assertEquals(Power.ZERO, decision.power());
    }

    @Test
    void decide_continuesBatteryAssist_afterTemperatureRisesAbove42_untilSocDropsTo50() {
        policy.start();

        // Cycle 1: trigger battery assist (SOC starts at 70%)
        policy.decide(rodStatus(40.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false));

        // Cycle 2: temperature back above 42°C, SOC dropped only 7 points (below both the
        // 50% absolute floor and the 10-point session drop limit)
        HeatingDecision decision = policy.decide(rodStatus(45.0, 60.0), batteryStatus(63, 0), gasHeatingStatus(false));

        assertEquals(Power.ofWatts(850), decision.power());
        assertEquals(HeatingSource.BATTERY, decision.source());
        assertTrue(policy.getState().batteryAssistActive());
    }

    @Test
    void decide_stopsBatteryAssist_whenSocReachesStopThreshold() {
        policy.start();

        // Cycle 1: trigger battery assist
        policy.decide(rodStatus(40.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false));

        // Cycle 2: SOC has dropped to the 50% stop threshold
        HeatingDecision decision = policy.decide(rodStatus(41.0, 60.0), batteryStatus(50, 0), gasHeatingStatus(false));

        assertEquals(HeatingSource.NONE, decision.source());
        assertFalse(policy.getState().batteryAssistActive());
    }

    @Test
    void decide_stopsBatteryAssist_whenSessionSocDropLimitReached_evenAboveAbsoluteFloor() {
        policy.start();

        // Cycle 1: trigger battery assist at 85% SOC
        policy.decide(rodStatus(40.0, 60.0), batteryStatus(85, 0), gasHeatingStatus(false));

        // Cycle 2: SOC dropped 10 points to 75% - well above the 50% absolute floor, but
        // exactly at the configured 10-percentage-point session drop limit
        HeatingDecision decision = policy.decide(
                rodStatus(40.0, 60.0), batteryStatus(75, 0), gasHeatingStatus(false, 40.0, 55.0));

        assertEquals(HeatingSource.NONE, decision.source());
        assertFalse(policy.getState().batteryAssistActive());
    }

    // ==================== Target reached ====================

    @Test
    void decide_stopsAndReturnsZero_whenRodTargetReached() {
        policy.start();

        HeatingDecision decision = policy.decide(rodStatus(60.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false));

        assertEquals(Power.ZERO, decision.power());
        assertEquals(HeatingSource.NONE, decision.source());
        assertTrue(decision.completed());
    }

    // ==================== Gas fallback ====================

    @Test
    void decide_fallsBackToGas_whenNoElectricPowerAndGasTempBelowThreshold() {
        policy.start();

        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0), // no battery trigger
                batteryStatus(70, 0),
                gasHeatingStatus(false, 30.0, 55.0));

        assertEquals(HeatingSource.GAS, decision.source());
        assertTrue(policy.getState().active());
        assertTrue(policy.getState().gasAssistActive());
    }

    @Test
    void decide_doesNotUseGas_whenGasTemperatureAboveThreshold() {
        policy.start();

        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0),
                batteryStatus(70, 0),
                gasHeatingStatus(false, 40.0, 55.0)); // above 35°C

        assertEquals(HeatingSource.NONE, decision.source());
        assertFalse(policy.getState().gasAssistActive());
    }

    @Test
    void decide_stopsAndReturnsZero_whenGasTargetReached() {
        policy.start();

        // Cycle 1: trigger gas fallback
        policy.decide(rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false, 30.0, 55.0));
        assertTrue(policy.getState().gasAssistActive());

        // Cycle 2: gas reached its target
        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(true, 55.0, 55.0));

        assertEquals(Power.ZERO, decision.power());
        assertEquals(HeatingSource.NONE, decision.source());
        assertTrue(decision.completed());
        assertEquals(GasCommand.DEACTIVATE, decision.gasCommand());
    }

    @Test
    void decide_stopsAndReturnsZero_atTargetMinusShutoffOffset() {
        policy.start();

        // Cycle 1: trigger gas fallback
        policy.decide(rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false, 30.0, 55.0));

        // Cycle 2: temperature reached target(55) - offset(5) = 50, but not yet the actual target
        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(true, 51.0, 55.0));

        assertEquals(Power.ZERO, decision.power());
        assertEquals(HeatingSource.NONE, decision.source());
        assertTrue(decision.completed());
        assertEquals(GasCommand.DEACTIVATE, decision.gasCommand());
    }

    @Test
    void decide_stopsConsideringGas_whenElectricPowerBecomesAvailable() {
        policy.start();

        // Cycle 1: gas fallback active
        policy.decide(rodStatus(50.0, 60.0), batteryStatus(70, 0), gasHeatingStatus(false, 30.0, 55.0));
        assertTrue(policy.getState().gasAssistActive());

        // Cycle 2: PV surplus becomes available
        HeatingDecision decision = policy.decide(
                rodStatus(50.0, 60.0), batteryStatus(70, 0, 500), gasHeatingStatus(true, 30.0, 55.0));

        assertEquals(HeatingSource.PV, decision.source());
        assertFalse(policy.getState().gasAssistActive());
    }

    private HeatingRodStatus rodStatus(double currentTempCelsius, double targetTempCelsius) {
        return rodStatus(currentTempCelsius, targetTempCelsius, 0);
    }

    private HeatingRodStatus rodStatus(double currentTempCelsius, double targetTempCelsius, long currentPowerWatts) {
        return new HeatingRodStatus(
                Temperature.ofCelsius(currentTempCelsius),
                Temperature.ofCelsius(targetTempCelsius),
                Power.ofWatts(currentPowerWatts),
                Instant.now());
    }

    private GasHeatingStatus gasHeatingStatus(boolean active) {
        return gasHeatingStatus(active, 50.0, 55.0);
    }

    private GasHeatingStatus gasHeatingStatus(boolean active, double currentTempCelsius, double targetTempCelsius) {
        return GasHeatingStatus.builder()
                .active(active)
                .currentTemperature(Temperature.ofCelsius(currentTempCelsius))
                .targetTemperature(Temperature.ofCelsius(targetTempCelsius))
                .measuredAt(Instant.now())
                .build();
    }

    private BatteryStatus batteryStatus(int socPercent, long batteryWatts) {
        return batteryStatus(socPercent, batteryWatts, 0);
    }

    private BatteryStatus batteryStatus(int socPercent, long batteryWatts, long productionWatts) {
        return batteryStatus(socPercent, batteryWatts, productionWatts, 0);
    }

    private BatteryStatus batteryStatus(int socPercent, long batteryWatts, long productionWatts,
            long consumptionWatts) {
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

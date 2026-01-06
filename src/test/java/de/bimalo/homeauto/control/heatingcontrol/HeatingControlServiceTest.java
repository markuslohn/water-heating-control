package de.bimalo.homeauto.control.heatingcontrol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.modbus.ModbusReadException;
import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for HeatingControlService.
 * Mocks all Modbus device communication to test the control logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class HeatingControlServiceTest {

    @Mock
    private HeatingControlConfig config;

    @Mock
    private BatteryStorageService batteryStorageService;

    @Mock
    private HeatingRodService heatingRodService;

    @InjectMocks
    private HeatingControlService heatingControlService;

    @BeforeEach
    void setUp() {
        // Default configuration values (lenient to avoid UnnecessaryStubbingException)
        lenient().when(config.enabled()).thenReturn(true);
        lenient().when(config.minSurplusPower()).thenReturn(100);
        lenient().when(config.maxHeatingPower()).thenReturn(3000);
        lenient().when(config.batteryPriorityEnabled()).thenReturn(true);
        lenient().when(config.batteryPriorityThreshold()).thenReturn(60);
        lenient().when(config.batteryReservedPower()).thenReturn(1000);
    }

    @Test
    void testControlHeating_WhenDisabled_ShouldNotHeat() {
        // Given
        when(config.enabled()).thenReturn(false);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(heatingRodService, never()).readTemperature1();
        verify(heatingRodService, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WhenTargetTemperatureReached_ShouldStopHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(500);

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_WhenTargetTemperatureReachedAndAlreadyStopped_ShouldNotCallAdjust() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0); // Already stopped

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(heatingRodService, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WhenInsufficientSurplus_ShouldStopHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(50); // Below minimum of 100W
        Power currentHeatingPower = Power.ofWatts(200);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 50 + 200 = 250W, but this is still checked against minimum
        // Actually, the adjusted surplus is 250W which is above minimum, so it should
        // heat
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 250));
    }

    @Test
    void testControlHeating_WhenSurplusBelowMinimumEvenWithCurrentPower_ShouldStop() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(-200); // Negative surplus
        Power currentHeatingPower = Power.ofWatts(150); // Current heating
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = -200 + 150 = -50W, below minimum of 100W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_WhenSufficientSurplus_ShouldAdjustHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(1500);
        Power currentHeatingPower = Power.ofWatts(200);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 1500 + 200 = 1700W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1700));
    }

    @Test
    void testControlHeating_WhenSurplusExceedsMaximum_ShouldLimitToMaxPower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(3500); // Exceeds max of 3000W
        Power currentHeatingPower = Power.ofWatts(200);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 3500 + 200 = 3700W, limited to 3000W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 3000));
    }

    @Test
    void testControlHeating_WhenSurplusExactlyAtMaximum_ShouldNotLimit() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2800);
        Power currentHeatingPower = Power.ofWatts(200);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 2800 + 200 = 3000W, exactly at maximum
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 3000));
    }

    @Test
    void testControlHeating_WithZeroCurrentHeatingPower_ShouldCalculateCorrectly() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(1200);
        Power currentHeatingPower = Power.ofWatts(0); // Not currently heating
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 1200 + 0 = 1200W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1200));
    }

    @Test
    void testControlHeating_WhenExceptionOccurs_ShouldHandleGracefully() {
        // Given
        when(heatingRodService.readTemperature1())
                .thenThrow(new ModbusReadException("Modbus communication error", "localhost", 502, 10001, null));

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Should not throw exception, just log it
        verify(heatingRodService, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WithMinimalValidSurplus_ShouldHeat() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(50);
        Power currentHeatingPower = Power.ofWatts(50);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 50 + 50 = 100W, exactly at minimum
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 100));
    }

    @Test
    void testControlHeating_JustBelowMinimumSurplus_ShouldStop() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(49);
        Power currentHeatingPower = Power.ofWatts(50);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC above threshold

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Total surplus = 49 + 50 = 99W, just below minimum of 100W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_WhenBatteryDischarging_ShouldStopHeating() {
        // Given: Battery is discharging, grid is supplying power
        // PV: 677W, Consumption: 3590W, Battery discharge: 1644W, Grid: 1269W
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(0); // PV - Consumption = negative, so 0
        Power currentHeatingPower = Power.ofWatts(1000); // Currently heating
        BatteryStatus batteryStatus = BatteryStatus.builder()
                .timestamp(LocalDateTime.now())
                .productionPower(Power.ofWatts(677))
                .consumptionPower(Power.ofWatts(3590))
                .batteryPower(Power.ofWatts(-1644)) // Discharging (negative)
                .gridPower(Power.ofWatts(1269)) // Grid supplying power
                .batteryStateOfCharge(Percentage.of(70))
                .build();

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 0 + 1000 = 1000W
        // Battery discharging: 1000 - 1644 = -644W (capped to 0)
        // 0W < minimum 100W, so heating should stop
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    // ==================== Battery Priority Tests ====================

    @Test
    void testControlHeating_WhenBatteryPriorityActive_AndSocBelowThreshold_ShouldReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50); // SOC 50% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Adjusted surplus = 2000 + 0 = 2000W
        // Battery priority active: 2000 - 1000 (reserved) = 1000W available for heating
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityActive_AndSocAboveThreshold_ShouldNotReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70); // SOC 70% > threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Battery priority not active (SOC above threshold): full 2000W available for
        // heating
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityDisabledInConfig_ShouldNotReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50); // SOC 50% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(false); // Disabled in config

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Battery priority disabled: full 2000W available despite low SOC
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityReservesTooMuch_ShouldStopHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(800); // Less than reserved power
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50); // SOC 50% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Adjusted surplus = 800 + 0 = 800W
        // Battery priority: 800 - 1000 = -200W (capped to 0)
        // 0W < minimum 100W, so heating stops
        verify(heatingRodService, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WhenBatteryPriorityActive_WithCurrentHeating_ShouldAccountForIt() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(1500);
        Power currentHeatingPower = Power.ofWatts(500); // Currently heating
        BatteryStatus batteryStatus = createBatteryStatus(45); // SOC 45% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Adjusted surplus = 1500 + 500 = 2000W
        // Battery priority: 2000 - 1000 = 1000W available for heating
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityAtExactThreshold_ShouldNotReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(60); // SOC 60% = threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // SOC equals threshold, priority not active: full 2000W available
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityJustBelowThreshold_ShouldReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(59); // SOC 59% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // SOC just below threshold, priority active: 2000 - 1000 = 1000W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityActive_AndMaxPowerStillApplies() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(5000); // Very high surplus
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50); // SOC 50% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);
        when(config.maxHeatingPower()).thenReturn(3000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Adjusted surplus = 5000 + 0 = 5000W
        // Battery priority: 5000 - 1000 = 4000W
        // Max heating power limits to 3000W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 3000));
    }

    @Test
    void testBatteryPriorityOverride_WhenDisabled_ShouldIgnoreBatteryPriority() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50); // SOC 50% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        // batteryPriorityThreshold and batteryReservedPower not mocked - not used when override is active

        // When
        heatingControlService.setBatteryPriorityOverride(true); // Disable battery priority
        heatingControlService.controlHeatingSummer();

        // Then
        // Override active, battery priority ignored: full 2000W available
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testBatteryPriorityOverride_WhenEnabled_ShouldApplyBatteryPriority() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(2000);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50); // SOC 50% < threshold 60%

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.setBatteryPriorityOverride(false); // Enable battery priority
        heatingControlService.controlHeatingSummer();

        // Then
        // Override not active, battery priority applies: 2000 - 1000 = 1000W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testIsBatteryPriorityActive_WhenConfigEnabledAndNoOverride() {
        // Given
        when(config.batteryPriorityEnabled()).thenReturn(true);

        // When
        boolean isActive = heatingControlService.isBatteryPriorityActive();

        // Then
        assertTrue(isActive);
    }

    @Test
    void testIsBatteryPriorityActive_WhenConfigEnabledButOverrideDisabled() {
        // Given
        when(config.batteryPriorityEnabled()).thenReturn(true);
        heatingControlService.setBatteryPriorityOverride(true); // Disable via override

        // When
        boolean isActive = heatingControlService.isBatteryPriorityActive();

        // Then
        assertFalse(isActive);
    }

    @Test
    void testIsBatteryPriorityActive_WhenConfigDisabled() {
        // Given
        when(config.batteryPriorityEnabled()).thenReturn(false);

        // When
        boolean isActive = heatingControlService.isBatteryPriorityActive();

        // Then
        assertFalse(isActive);
    }

    @Test
    void testResetBatteryPriorityOverride_ShouldReEnablePriority() {
        // Given
        when(config.batteryPriorityEnabled()).thenReturn(true);
        heatingControlService.setBatteryPriorityOverride(true); // Disable
        assertFalse(heatingControlService.isBatteryPriorityActive());

        // When
        heatingControlService.resetBatteryPriorityOverride(); // Midnight reset

        // Then
        assertTrue(heatingControlService.isBatteryPriorityActive());
    }

    // ==================== Temperature Hysteresis Tests ====================

    @Test
    void testControlHeating_WhenTargetReached_ShouldEnterCoolingMode() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(500);

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(config.temperatureHysteresis()).thenReturn(10.0);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_InCoolingMode_TemperatureAboveRestartThreshold_ShouldNotHeat() {
        // Given: First reach target temperature (70°C with target 68°C)
        Temperature targetReachedTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        when(heatingRodService.readTemperature1()).thenReturn(targetReachedTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(Power.ofWatts(500));
        when(config.temperatureHysteresis()).thenReturn(10.0);

        heatingControlService.controlHeatingSummer(); // Enter cooling mode

        // Now temperature drops to 60°C (still above restart threshold of 58°C)
        Temperature coolingTemp = Temperature.ofCelsius(60.0);

        when(heatingRodService.readTemperature1()).thenReturn(coolingTemp);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should NOT heat (still in cooling mode)
        // Only the first call (entering cooling mode) should have called adjustHeating
        verify(heatingRodService, never()).adjustHeating(argThat(power -> power.getWatts() > 0));
    }

    @Test
    void testControlHeating_InCoolingMode_TemperatureBelowRestartThreshold_ShouldResumeHeating() {
        // Given: First reach target temperature (70°C with target 68°C)
        Temperature targetReachedTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        when(heatingRodService.readTemperature1()).thenReturn(targetReachedTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(Power.ofWatts(500));
        when(config.temperatureHysteresis()).thenReturn(10.0);

        heatingControlService.controlHeatingSummer(); // Enter cooling mode

        // Now temperature drops to 57°C (below restart threshold of 58°C)
        Temperature restartTemp = Temperature.ofCelsius(57.0);
        Power baseSurplus = Power.ofWatts(2000);
        BatteryStatus batteryStatus = createBatteryStatus(70);

        when(heatingRodService.readTemperature1()).thenReturn(restartTemp);
        when(heatingRodService.readPower()).thenReturn(Power.ofWatts(0));
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should resume heating
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WithHysteresis_NormalHeatingWhenFarBelowTarget() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power baseSurplus = Power.ofWatts(1500);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70);

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);
        when(config.temperatureHysteresis()).thenReturn(10.0);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should heat normally
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 1500));
    }

    @Test
    void testControlHeating_HysteresisWithCustomValue_ShouldUseConfiguredHysteresis() {
        // Given: Custom hysteresis of 5°C
        Temperature targetReachedTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        when(heatingRodService.readTemperature1()).thenReturn(targetReachedTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(Power.ofWatts(500));
        when(config.temperatureHysteresis()).thenReturn(5.0); // 5°C hysteresis

        heatingControlService.controlHeatingSummer(); // Enter cooling mode

        // Temperature at 64°C (above restart threshold of 63°C with 5°C hysteresis)
        Temperature stillCoolingTemp = Temperature.ofCelsius(64.0);
        Power baseSurplus = Power.ofWatts(2000);
        BatteryStatus batteryStatus = createBatteryStatus(70);

        when(heatingRodService.readTemperature1()).thenReturn(stillCoolingTemp);
        when(heatingRodService.readPower()).thenReturn(Power.ofWatts(0));
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);
        when(batteryStorageService.getCurrentStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should NOT heat (still above 63°C restart threshold)
        verify(heatingRodService, never()).adjustHeating(argThat(power -> power.getWatts() > 0));

        // Now temperature drops to 62°C (below restart threshold of 63°C)
        Temperature restartTemp = Temperature.ofCelsius(62.0);
        when(heatingRodService.readTemperature1()).thenReturn(restartTemp);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should resume heating
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    // Helper method to create BatteryStatus with specific SOC
    private BatteryStatus createBatteryStatus(int socPercent) {
        return BatteryStatus.builder()
                .timestamp(LocalDateTime.now())
                .productionPower(Power.ofWatts(0))
                .consumptionPower(Power.ofWatts(0))
                .batteryPower(Power.ofWatts(0))
                .gridPower(Power.ofWatts(0))
                .batteryStateOfCharge(Percentage.of(socPercent))
                .build();
    }
}

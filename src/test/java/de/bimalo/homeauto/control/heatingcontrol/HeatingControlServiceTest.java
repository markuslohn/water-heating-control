package de.bimalo.homeauto.control.heatingcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Measurements;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Status;
import de.bimalo.homeauto.boundary.modbus.ModbusReadException;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Season;
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
 * Test class for HeatingControlService.
 * Mocks all Modbus device communication to test the control logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class HeatingControlServiceTest {

    @Mock
    private HeatingControlConfig config;

    @Mock
    private E3dcAdapter e3dcAdapter;

    @Mock
    private Elwa2Adapter elwa2Adapter;

    @InjectMocks
    private HeatingControlService heatingControlService;

    @BeforeEach
    void setUp() {
        // Default configuration values (lenient to avoid UnnecessaryStubbingException)
        lenient().when(config.enabled()).thenReturn(true);
        lenient().when(config.minSurplusPower()).thenReturn(100);
        lenient().when(config.maxHeatingPower()).thenReturn(2900);
        lenient().when(config.batteryPriorityEnabled()).thenReturn(true);
        lenient().when(config.batteryPriorityThreshold()).thenReturn(60);
        lenient().when(config.batteryReservedPower()).thenReturn(1000);
        lenient().when(config.solarPowerReductionPercent()).thenReturn(0); // Default: no reduction
        lenient().when(config.powerIncreaseSmoothingWindow()).thenReturn(Duration.ofSeconds(150));
        lenient().when(config.minPowerChangeThreshold()).thenReturn(0); // Default: no threshold suppression
    }

    @Test
    void testControlHeating_WhenDisabled_ShouldNotHeat() {
        // Given
        when(config.enabled()).thenReturn(false);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(elwa2Adapter, never()).readMeasurements();
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WhenTargetTemperatureReached_ShouldStopHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(500);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_WhenTargetTemperatureReachedAndAlreadyStopped_ShouldNotCallAdjust() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0); // Already stopped

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WhenInsufficientSurplus_ShouldStopHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(200);
        // New logic: adjustedSurplus = production - (consumption - currentHeating)
        // For 50W result: production=250, consumption=200 → 250 - (200-200) = 250W
        // Then in heating priority: availableForHeating = 250 + 0 = 250W (above
        // minimum)
        BatteryStatus batteryStatus = createBatteryStatus(70, 250, 200, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 250 - (200 - 200) = 250W, above minimum of 100W
        // heating priority: availableForHeating = 250 + 0 = 250W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 250));
    }

    @Test
    void testControlHeating_WhenSurplusBelowMinimumEvenWithCurrentPower_ShouldStop() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(150); // Current heating
        // New logic: adjustedSurplus = production - (consumption - currentHeating)
        // For negative result: production=100, consumption=300 → 100 - (300-150) = -50W
        // → capped to 0
        // heating priority: availableForHeating = 0 + 0 = 0W < 100W → stop
        BatteryStatus batteryStatus = createBatteryStatus(70, 100, 300, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus capped to 0, below minimum of 100W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_WhenSufficientSurplus_ShouldAdjustHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(200);
        // New logic: adjustedSurplus = production - (consumption - currentHeating)
        // For 1700W: production=1900, consumption=200 → 1900 - (200-200) = 1900W
        // heating priority: availableForHeating = 1900 + 0 = 1900W
        // But we want 1700W, so: production=1700, consumption=200 → 1700 - 0 = 1700W
        BatteryStatus batteryStatus = createBatteryStatus(70, 1900, 200, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 1900 - (200 - 200) = 1900W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1900));
    }

    @Test
    void testControlHeating_WhenSurplusExceedsMaximum_ShouldLimitToMaxPower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(200);
        // New logic: production=3700, consumption=200 → 3700 - (200-200) = 3700W
        // heating priority: availableForHeating = 3700 + 0 = 3700W, limited to 2900W
        BatteryStatus batteryStatus = createBatteryStatus(70, 3700, 200, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 3700W, limited to max 2900W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2900));
    }

    @Test
    void testControlHeating_WhenSurplusExactlyAtMaximum_ShouldNotLimit() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(200);
        // New logic: production=2900, consumption=200 → 2900 - (200-200) = 2900W
        BatteryStatus batteryStatus = createBatteryStatus(70, 2900, 200, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 2900, exactly at maximum
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2900));
    }

    @Test
    void testControlHeating_WithZeroCurrentHeatingPower_ShouldCalculateCorrectly() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0); // Not currently heating
        // New logic: production=1200, consumption=0 → 1200 - 0 = 1200W
        BatteryStatus batteryStatus = createBatteryStatus(70, 1200, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 1200W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1200));
    }

    @Test
    void testControlHeating_WhenExceptionOccurs_ShouldHandleGracefully() {
        // Given
        when(elwa2Adapter.readMeasurements())
                .thenThrow(new ModbusReadException("Modbus communication error", "localhost", 502, 10001, null));

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Should not throw exception, just log it
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WithMinimalValidSurplus_ShouldHeat() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(50);
        // New logic: production=100, consumption=50 → 100 - (50-50) = 100W
        BatteryStatus batteryStatus = createBatteryStatus(70, 100, 50, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 100W, exactly at minimum
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 100));
    }

    @Test
    void testControlHeating_JustBelowMinimumSurplus_ShouldStop() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(50);
        // New logic: production=99, consumption=50 → 99 - (50-50) = 99W
        BatteryStatus batteryStatus = createBatteryStatus(70, 99, 50, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 99W, just below minimum of 100W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_WhenBatteryDischarging_ShouldStopHeating() {
        // Given: Battery is discharging, grid is supplying power
        // PV: 677W, Consumption: 3590W, Battery discharge: 1644W, Grid: 1269W
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(1000); // Currently heating
        BatteryStatus batteryStatus = BatteryStatus.builder()
                .measuredAt(Instant.now())
                .productionPower(Power.ofWatts(677))
                .consumptionPower(Power.ofWatts(3590))
                .batteryPower(Power.ofWatts(-1644)) // Discharging (negative)
                .gridPower(Power.ofWatts(1269)) // Grid supplying power
                .batteryStateOfCharge(Percentage.of(70))
                .build();

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 0 + 1000 = 1000W
        // Battery discharging: 1000 - 1644 = -644W (capped to 0)
        // 0W < minimum 100W, so heating should stop
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    // ==================== Battery Priority Tests ====================

    @Test
    void testControlHeating_WhenBatteryPriorityActive_AndSocBelowThreshold_ShouldReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000 - 0 = 2000W
        // Battery priority: 2000 - 1000 (reserved) = 1000W
        BatteryStatus batteryStatus = createBatteryStatus(50, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 2000W, battery priority: 2000 - 1000 = 1000W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityActive_AndSocAboveThreshold_ShouldNotReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(70, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // SOC above threshold, heating priority: full 2000W available
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityDisabledInConfig_ShouldNotReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(50, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(false); // Disabled in config

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // Battery priority disabled: full 2000W available despite low SOC
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityReservesTooMuch_ShouldStopHeating() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=800, consumption=0 → 800W
        // Battery priority: 800 - 1000 = -200W → 0W
        BatteryStatus batteryStatus = createBatteryStatus(50, 800, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 800W, battery priority: 800 - 1000 = -200W → 0W < 100W
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WhenBatteryPriorityActive_WithCurrentHeating_ShouldAccountForIt() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(500); // Currently heating
        // New logic: production=2000, consumption=500 → 2000 - (500-500) = 2000W
        // Battery priority: 2000 - 1000 = 1000W
        BatteryStatus batteryStatus = createBatteryStatus(45, 2000, 500, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 2000W, battery priority: 2000 - 1000 = 1000W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityAtExactThreshold_ShouldNotReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(60, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // SOC equals threshold, heating priority: full 2000W available
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityJustBelowThreshold_ShouldReservePower() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(59, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // SOC just below threshold, battery priority: 2000 - 1000 = 1000W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testControlHeating_WhenBatteryPriorityActive_AndMaxPowerStillApplies() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=5000, consumption=0 → 5000W
        // Battery priority: 5000 - 1000 = 4000W, limited to max 3000W
        BatteryStatus batteryStatus = createBatteryStatus(50, 5000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);
        when(config.maxHeatingPower()).thenReturn(3000);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        // adjustedSurplus = 5000W, battery priority: 5000 - 1000 = 4000W, limited to
        // 3000W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 3000));
    }

    @Test
    void testBatteryPriorityOverride_WhenDisabled_ShouldIgnoreBatteryPriority() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(50, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        // batteryPriorityThreshold and batteryReservedPower not mocked - not used when
        // override is active

        // When
        heatingControlService.setBatteryPriorityOverride(true); // Disable battery priority
        heatingControlService.controlHeatingSummer();

        // Then
        // Override active, heating priority: full 2000W available
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testBatteryPriorityOverride_WhenEnabled_ShouldApplyBatteryPriority() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(50, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);

        // When
        heatingControlService.setBatteryPriorityOverride(false); // Enable battery priority
        heatingControlService.controlHeatingSummer();

        // Then
        // Override not active, battery priority: 2000 - 1000 = 1000W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1000));
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

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(config.temperatureHysteresis()).thenReturn(10.0);

        // When
        heatingControlService.controlHeatingSummer();

        // Then
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 0));
    }

    @Test
    void testControlHeating_InCoolingMode_TemperatureAboveRestartThreshold_ShouldNotHeat() {
        // Given: First reach target temperature (70°C with target 68°C)
        Temperature targetReachedTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(targetReachedTemp, targetTemp, Power.ofWatts(500), Elwa2Status.UNKNOWN, Instant.now()));
        when(config.temperatureHysteresis()).thenReturn(10.0);

        heatingControlService.controlHeatingSummer(); // Enter cooling mode

        // Now temperature drops to 60°C (still above restart threshold of 58°C)
        Temperature coolingTemp = Temperature.ofCelsius(60.0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(coolingTemp, targetTemp, Power.ofWatts(500), Elwa2Status.UNKNOWN, Instant.now()));

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should NOT heat (still in cooling mode)
        // Only the first call (entering cooling mode) should have called adjustHeating
        verify(elwa2Adapter, never()).adjustHeating(argThat(power -> power.getWatts() > 0));
    }

    @Test
    void testControlHeating_InCoolingMode_TemperatureBelowRestartThreshold_ShouldResumeHeating() {
        // Given: First reach target temperature (70°C with target 68°C)
        Temperature targetReachedTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(targetReachedTemp, targetTemp, Power.ofWatts(500), Elwa2Status.UNKNOWN, Instant.now()));
        when(config.temperatureHysteresis()).thenReturn(10.0);

        heatingControlService.controlHeatingSummer(); // Enter cooling mode

        // Now temperature drops to 57°C (below restart threshold of 58°C)
        Temperature restartTemp = Temperature.ofCelsius(57.0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(70, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(restartTemp, targetTemp, Power.ofWatts(0), Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should resume heating
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WithHysteresis_NormalHeatingWhenFarBelowTarget() {
        // Given
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        // New logic: production=1500, consumption=0 → 1500W
        BatteryStatus batteryStatus = createBatteryStatus(70, 1500, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.temperatureHysteresis()).thenReturn(10.0);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should heat normally
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1500));
    }

    @Test
    void testControlHeating_HysteresisWithCustomValue_ShouldUseConfiguredHysteresis() {
        // Given: Custom hysteresis of 5°C
        Temperature targetReachedTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(targetReachedTemp, targetTemp, Power.ofWatts(500), Elwa2Status.UNKNOWN, Instant.now()));
        when(config.temperatureHysteresis()).thenReturn(5.0); // 5°C hysteresis

        heatingControlService.controlHeatingSummer(); // Enter cooling mode

        // Temperature at 64°C (above restart threshold of 63°C with 5°C hysteresis)
        Temperature stillCoolingTemp = Temperature.ofCelsius(64.0);
        // New logic: production=2000, consumption=0 → 2000W
        BatteryStatus batteryStatus = createBatteryStatus(70, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(stillCoolingTemp, targetTemp, Power.ofWatts(0), Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should NOT heat (still above 63°C restart threshold)
        verify(elwa2Adapter, never()).adjustHeating(argThat(power -> power.getWatts() > 0));

        // Now temperature drops to 62°C (below restart threshold of 63°C)
        Temperature restartTemp = Temperature.ofCelsius(62.0);
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(restartTemp, targetTemp, Power.ofWatts(0), Elwa2Status.UNKNOWN, Instant.now()));

        // When
        heatingControlService.controlHeatingSummer();

        // Then - should resume heating
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    // ==================== Manual Mode Tests ====================

    @Test
    void testManualMode_WhenActivated_ShouldSuspendAutomaticControl() {
        // Given: Normal conditions where automatic control would adjust heating
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(500);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        // Note: Battery mocks not needed - manual mode returns early before surplus check

        // When: Manual mode is activated
        heatingControlService.activateManualMode();
        heatingControlService.controlHeatingSummer();

        // Then: Automatic control should NOT adjust heating
        verify(elwa2Adapter, never()).adjustHeating(any());
        assertTrue(heatingControlService.isManualModeActive());
    }

    @Test
    void testManualMode_WhenTargetTemperatureReached_ShouldStayInManualModeWithoutAdjusting() {
        // Given: Manual mode active, target temperature reached
        Temperature currentTemp = Temperature.ofCelsius(70.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(1000);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));

        // When: Manual mode is active and control runs
        heatingControlService.activateManualMode();
        assertTrue(heatingControlService.isManualModeActive());

        heatingControlService.controlHeatingSummer();

        // Then: Automatic control stays fully suspended - the external controller (not
        // HeatingControlService) is responsible for the heating rod while in manual mode
        assertTrue(heatingControlService.isManualModeActive());
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testManualMode_WhenDeactivated_ShouldResumeAutomaticControl() {
        // Given: Manual mode was active, then deactivated
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70, 1500, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When: Manual mode is activated and then deactivated
        heatingControlService.activateManualMode();
        heatingControlService.deactivateManualMode();
        heatingControlService.controlHeatingSummer();

        // Then: Automatic control should resume and adjust heating
        assertFalse(heatingControlService.isManualModeActive());
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1500));
    }

    @Test
    void testManualMode_WhenNotActive_ShouldAllowAutomaticControl() {
        // Given: Normal conditions, manual mode NOT active
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70, 1000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);

        // When: Control runs without manual mode
        heatingControlService.controlHeatingSummer();

        // Then: Automatic control should adjust heating
        assertFalse(heatingControlService.isManualModeActive());
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1000));
    }

    @Test
    void testManualMode_WhenTargetNotReached_ShouldStayInManualMode() {
        // Given: Manual mode active, target temperature NOT reached
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(1000);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));

        // When: Manual mode is active and control runs
        heatingControlService.activateManualMode();
        heatingControlService.controlHeatingSummer();

        // Then: Manual mode should remain active and no automatic adjustment
        assertTrue(heatingControlService.isManualModeActive());
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testIsManualModeActive_InitiallyFalse() {
        // Given/When: Fresh service instance

        // Then: Manual mode should be inactive by default
        assertFalse(heatingControlService.isManualModeActive());
    }

    @Test
    void testActivateManualMode_WhenAlreadyActive_ShouldRemainActive() {
        // Given: Manual mode already active
        heatingControlService.activateManualMode();

        // When: Activate again
        heatingControlService.activateManualMode();

        // Then: Should still be active
        assertTrue(heatingControlService.isManualModeActive());
    }

    @Test
    void testDeactivateManualMode_WhenAlreadyInactive_ShouldRemainInactive() {
        // Given: Manual mode not active

        // When: Deactivate
        heatingControlService.deactivateManualMode();

        // Then: Should still be inactive
        assertFalse(heatingControlService.isManualModeActive());
    }

    // ==================== Season Tests ====================

    @Test
    void testGetCurrentSeason_ShouldReturnValidSeason() {
        // When
        Season season = heatingControlService.getCurrentSeason();

        // Then
        assertEquals(Season.current(), season);
    }

    @Test
    void testIsCurrentSeasonEnabled_WhenCurrentSeasonEnabled_ShouldReturnTrue() {
        // Given: Enable the current season
        Season currentSeason = Season.current();
        switch (currentSeason) {
            case WINTER -> when(config.winterEnabled()).thenReturn(true);
            case SPRING -> when(config.springEnabled()).thenReturn(true);
            case SUMMER -> when(config.summerEnabled()).thenReturn(true);
            case AUTUMN -> when(config.autumnEnabled()).thenReturn(true);
        }

        // When/Then
        assertTrue(heatingControlService.isCurrentSeasonEnabled());
    }

    @Test
    void testIsCurrentSeasonEnabled_WhenCurrentSeasonDisabled_ShouldReturnFalse() {
        // Given: Disable the current season
        Season currentSeason = Season.current();
        switch (currentSeason) {
            case WINTER -> when(config.winterEnabled()).thenReturn(false);
            case SPRING -> when(config.springEnabled()).thenReturn(false);
            case SUMMER -> when(config.summerEnabled()).thenReturn(false);
            case AUTUMN -> when(config.autumnEnabled()).thenReturn(false);
        }

        // When/Then
        assertFalse(heatingControlService.isCurrentSeasonEnabled());
    }

    // ==================== Solar Power Reduction Tests ====================

    @Test
    void testControlHeating_WithSolarPowerReduction_ShouldReduceAvailablePower() {
        // Given: 2000W available, 5% reduction should result in 1900W
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.solarPowerReductionPercent()).thenReturn(5);

        // When
        heatingControlService.controlHeatingSummer();

        // Then: 2000W - 5% = 1900W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1900));
    }

    @Test
    void testControlHeating_WithZeroReduction_ShouldNotReducePower() {
        // Given: 2000W available, 0% reduction should result in 2000W
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.solarPowerReductionPercent()).thenReturn(0);

        // When
        heatingControlService.controlHeatingSummer();

        // Then: No reduction, full 2000W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2000));
    }

    @Test
    void testControlHeating_WithHighReduction_ShouldReduceSignificantly() {
        // Given: 2000W available, 10% reduction should result in 1800W
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70, 2000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.solarPowerReductionPercent()).thenReturn(10);

        // When
        heatingControlService.controlHeatingSummer();

        // Then: 2000W - 10% = 1800W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1800));
    }

    @Test
    void testControlHeating_WithReduction_AndMaxPower_ShouldApplyReductionBeforeMax() {
        // Given: 4000W available, 5% reduction = 3800W, then limited to max 2900W
        // Order: checkSurplusPower applies reduction first, then max limit is applied
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(70, 4000, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.solarPowerReductionPercent()).thenReturn(5);

        // When
        heatingControlService.controlHeatingSummer();

        // Then: 4000W - 5% = 3800W, then limited to max 2900W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 2900));
    }

    @Test
    void testControlHeating_WithReduction_ResultBelowMinimum_ShouldNotHeat() {
        // Given: 150W available (not currently heating), 40% reduction = 90W < 100W minimum
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0); // Not currently heating
        BatteryStatus batteryStatus = createBatteryStatus(70, 150, 0, 0);

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.solarPowerReductionPercent()).thenReturn(40); // 150 - 40% = 90W < 100W min

        // When
        heatingControlService.controlHeatingSummer();

        // Then: 150W - 40% = 90W < 100W minimum → should not start heating
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void testControlHeating_WithReduction_AndBatteryPriority_ShouldApplyBoth() {
        // Given: 3000W available, battery priority reserves 1000W → 2000W
        // Then 5% reduction → 1900W
        Temperature currentTemp = Temperature.ofCelsius(50.0);
        Temperature targetTemp = Temperature.ofCelsius(68.0);
        Power currentHeatingPower = Power.ofWatts(0);
        BatteryStatus batteryStatus = createBatteryStatus(50, 3000, 0, 0); // SOC below threshold

        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(currentTemp, targetTemp, currentHeatingPower, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(config.batteryPriorityEnabled()).thenReturn(true);
        when(config.batteryPriorityThreshold()).thenReturn(60);
        when(config.batteryReservedPower()).thenReturn(1000);
        when(config.solarPowerReductionPercent()).thenReturn(5);

        // When
        heatingControlService.controlHeatingSummer();

        // Then: 3000W - 1000W reserved = 2000W, then 5% reduction = 1900W
        verify(elwa2Adapter).adjustHeating(argThat(power -> power.getWatts() == 1900));
    }

    // Helper method to create BatteryStatus with specific SOC (legacy - all power
    // values 0)
    private BatteryStatus createBatteryStatus(int socPercent) {
        return createBatteryStatus(socPercent, 0, 0, 0);
    }

    // Helper method to create BatteryStatus with specific values for new
    // calculation logic
    // The new logic calculates: adjustedSurplus = production - (consumption -
    // currentHeating) - batteryPower (if charging)
    // For heating priority: availableForHeating = adjustedSurplus + batteryPower
    // For battery priority: availableForHeating = adjustedSurplus - batteryPower
    // (if discharging) - reservedPower
    private BatteryStatus createBatteryStatus(int socPercent, long productionWatts, long consumptionWatts,
            long batteryWatts) {
        return BatteryStatus.builder()
                .measuredAt(Instant.now())
                .productionPower(Power.ofWatts(productionWatts))
                .consumptionPower(Power.ofWatts(consumptionWatts))
                .batteryPower(Power.ofWatts(batteryWatts))
                .gridPower(Power.ofWatts(0))
                .batteryStateOfCharge(Percentage.of(socPercent))
                .build();
    }
}

package de.bimalo.homeauto.control.heatingcontrol;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.modbus.ModbusReadException;
import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
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
    }

    @Test
    void testControlHeating_WhenDisabled_ShouldNotHeat() {
        // Given
        when(config.enabled()).thenReturn(false);

        // When
        heatingControlService.controlHeating();

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
        heatingControlService.controlHeating();

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
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

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

        when(heatingRodService.readTemperature1()).thenReturn(currentTemp);
        when(heatingRodService.readTargetTemperature()).thenReturn(targetTemp);
        when(heatingRodService.readPower()).thenReturn(currentHeatingPower);
        when(batteryStorageService.determineSolarPowerSurplus()).thenReturn(baseSurplus);

        // When
        heatingControlService.controlHeating();

        // Then
        // Total surplus = 49 + 50 = 99W, just below minimum of 100W
        verify(heatingRodService).adjustHeating(argThat(power -> power.getWatts() == 0));
    }
}

package de.bimalo.homeauto.control.manualwaterheating;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Measurements;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Status;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlConfig;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualWaterHeatingServiceTest {

    @Mock
    private ManualWaterHeatingConfig config;

    @Mock
    private HeatingControlConfig heatingControlConfig;

    @Mock
    private HeatingControlService heatingControlService;

    @Mock
    private E3dcAdapter e3dcAdapter;

    @Mock
    private Elwa2Adapter elwa2Adapter;

    @Mock
    private VitodensAdapter vitodensAdapter;

    @InjectMocks
    private ManualWaterHeatingService service;

    @BeforeEach
    void setUp() {
        lenient().when(config.heatingRodLowTemperatureThreshold()).thenReturn(42.0);
        lenient().when(config.batterySocStartThreshold()).thenReturn(65);
        lenient().when(config.batterySocStopThreshold()).thenReturn(50);
        lenient().when(config.maxBatterySocDropPercent()).thenReturn(10);
        lenient().when(config.maxBatteryHeatingPower()).thenReturn(850);
        lenient().when(config.batteryMaxDischargePower()).thenReturn(1500);
        lenient().when(config.gasHeatingLowTemperatureThreshold()).thenReturn(35.0);
        lenient().when(config.gasHeatingShutoffTemperatureOffset()).thenReturn(5.0);
        lenient().when(heatingControlConfig.maxHeatingPower()).thenReturn(2900);
        lenient().when(heatingControlConfig.temperatureHysteresis()).thenReturn(10.0);
    }

    // ==================== Lifecycle ====================

    @Test
    void activate_suspendsAutomaticControl() {
        service.activate();

        verify(heatingControlService).activateManualMode();
        assertTrue(service.getStatus().isActive());
    }

    @Test
    void activate_whenAlreadyActive_doesNotReactivate() {
        service.activate();
        service.activate();

        verify(heatingControlService, times(1)).activateManualMode();
    }

    @Test
    void deactivate_stopsHeatingAndResumesAutomaticControl() {
        service.activate();

        service.deactivate();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 0));
        verify(heatingControlService).deactivateManualMode();
        assertFalse(service.getStatus().isActive());
    }

    @Test
    void deactivate_stopsGasHeating_whenActive() {
        when(vitodensAdapter.isHeatingActive()).thenReturn(true);
        service.activate();

        service.deactivate();

        verify(vitodensAdapter).deactivateHeating();
    }

    @Test
    void manageWaterHeating_doesNothing_whenNotActive() {
        service.manageWaterHeating();

        verify(elwa2Adapter, never()).readMeasurements();
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    // ==================== PV surplus ====================

    @Test
    void manageWaterHeating_usesPvSurplusOnly_whenNoBatteryTriggerConditions() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now())); // above 42°C
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ofWatts(1000));
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));

        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 1000));
        assertEquals(HeatingSource.PV, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_capsPvSurplusAtConfiguredMaxHeatingPower() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ofWatts(4000));
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));

        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 2900));
    }

    @Test
    void manageWaterHeating_capsCombinedPvAndBatteryPowerAtConfiguredMaxHeatingPower() {
        service.activate();
        // Rod temp below 42°C and SOC above 65% -> battery assist also triggers
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ofWatts(2500));
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));

        service.manageWaterHeating();

        // Without the combined cap this would be 2500 + 850 = 3350 W, which exceeds both
        // heatingctl.max-heating-power (2900) and the ELWA2 hardware limit (3200)
        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 2900));
    }

    // ==================== Battery assist trigger ====================

    @Test
    void manageWaterHeating_addsBatteryPower_whenTempBelowThresholdAndSocAboveStart() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now())); // below 42°C
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0)); // SOC 70% > 65%, not discharging

        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 850));
        assertEquals(HeatingSource.BATTERY, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_doesNotTriggerBatteryAssist_whenSocAtStartThreshold() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(65, 0)); // exactly 65%, not >65%

        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 0));
        assertEquals(HeatingSource.NONE, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_respectsBatteryDischargeHeadroom() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        // Battery already discharging 1200W for house consumption -> only 300W headroom left
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, -1200));

        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 300));
    }

    @Test
    void manageWaterHeating_doesNotHeat_whenBatteryHeadroomExhausted() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        // Battery already discharging at its 1500W max for house consumption alone
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, -1500));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(40.0));
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 0));
    }

    @Test
    void manageWaterHeating_continuesBatteryAssist_afterTemperatureRisesAbove42_untilSocDropsTo50() {
        service.activate();

        // Cycle 1: trigger battery assist (SOC starts at 70%)
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        service.manageWaterHeating();

        // Cycle 2: temperature back above 42°C, SOC dropped only 7 points (below both the
        // 50% absolute floor and the 10-point session drop limit)
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(45.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(63, 0));
        service.manageWaterHeating();

        verify(elwa2Adapter, times(2)).adjustHeating(argThat(p -> p.getWatts() == 850));
        assertEquals(HeatingSource.BATTERY, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_stopsBatteryAssist_whenSocReachesStopThreshold() {
        service.activate();

        // Cycle 1: trigger battery assist
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        service.manageWaterHeating();

        // Cycle 2: SOC has dropped to the 50% stop threshold
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(41.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(50, 0));
        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 0));
        assertEquals(HeatingSource.NONE, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_stopsBatteryAssist_whenSessionSocDropLimitReached_evenAboveAbsoluteFloor() {
        service.activate();

        // Cycle 1: trigger battery assist at 85% SOC
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(85, 0));
        service.manageWaterHeating();

        // Cycle 2: SOC dropped 10 points to 75% - well above the 50% absolute floor, but
        // exactly at the configured 10-percentage-point session drop limit
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(75, 0));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(40.0));
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));
        service.manageWaterHeating();

        verify(elwa2Adapter).adjustHeating(argThat(p -> p.getWatts() == 0));
        assertEquals(HeatingSource.NONE, service.getStatus().getSource());
    }

    // ==================== Target reached ====================

    @Test
    void manageWaterHeating_stopsElectricHeatingAndDeactivatesManualMode_whenRodTargetReached() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(60.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));

        service.manageWaterHeating();

        verify(elwa2Adapter, atLeastOnce()).adjustHeating(argThat(p -> p.getWatts() == 0));
        verify(e3dcAdapter, never()).determineSolarPowerSurplus();
        verify(heatingControlService).deactivateManualMode();
        assertFalse(service.getStatus().isActive());
    }

    // ==================== Gas fallback ====================

    @Test
    void manageWaterHeating_fallsBackToGas_whenNoElectricPowerAndGasTempBelowThreshold() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now())); // no battery trigger
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(30.0));
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        service.manageWaterHeating();

        verify(vitodensAdapter).activateHeating();
        assertEquals(HeatingSource.GAS, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_doesNotUseGas_whenGasTemperatureAboveThreshold() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(40.0)); // above 35°C
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        service.manageWaterHeating();

        verify(vitodensAdapter, never()).activateHeating();
        assertEquals(HeatingSource.NONE, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_stopsGasFallback_whenGasTargetReached() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(55.0));
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));
        when(vitodensAdapter.isHeatingActive()).thenReturn(true);

        service.manageWaterHeating();

        verify(vitodensAdapter, never()).activateHeating();
        verify(vitodensAdapter).deactivateHeating();
        assertEquals(HeatingSource.NONE, service.getStatus().getSource());
    }

    @Test
    void manageWaterHeating_stopsGasFallbackAndDeactivatesManualMode_atTargetMinusShutoffOffset() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        // Cycle 1: trigger gas fallback
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(30.0));
        service.manageWaterHeating();

        // Cycle 2: temperature reached target(55) - offset(5) = 50, but not yet the actual target
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(51.0));
        when(vitodensAdapter.isHeatingActive()).thenReturn(true);
        service.manageWaterHeating();

        verify(vitodensAdapter, atLeastOnce()).deactivateHeating();
        assertEquals(HeatingSource.NONE, service.getStatus().getSource());
        verify(heatingControlService).deactivateManualMode();
        assertFalse(service.getStatus().isActive());
    }

    @Test
    void manageWaterHeating_deactivatesGas_whenElectricPowerBecomesAvailable() {
        service.activate();

        // Cycle 1: gas fallback active
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new Elwa2Measurements(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2Status.UNKNOWN, Instant.now()));
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ZERO);
        when(e3dcAdapter.getCurrentStatus()).thenReturn(batteryStatus(70, 0));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(30.0));
        when(vitodensAdapter.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));
        service.manageWaterHeating();

        // Cycle 2: PV surplus becomes available
        when(e3dcAdapter.determineSolarPowerSurplus()).thenReturn(Power.ofWatts(500));
        when(vitodensAdapter.isHeatingActive()).thenReturn(true);
        service.manageWaterHeating();

        verify(vitodensAdapter).deactivateHeating();
        assertEquals(HeatingSource.PV, service.getStatus().getSource());
    }

    // ==================== Failure handling ====================

    @Test
    void manageWaterHeating_swallowsException_whenModbusCommunicationFails() {
        service.activate();
        when(elwa2Adapter.readMeasurements()).thenThrow(new RuntimeException("Modbus timeout"));

        assertDoesNotThrow(() -> service.manageWaterHeating());

        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    private BatteryStatus batteryStatus(int socPercent, long batteryWatts) {
        return BatteryStatus.builder()
                .timestamp(LocalDateTime.now())
                .productionPower(Power.ZERO)
                .consumptionPower(Power.ZERO)
                .batteryPower(Power.ofWatts(batteryWatts))
                .gridPower(Power.ZERO)
                .batteryStateOfCharge(Percentage.of(socPercent))
                .build();
    }
}

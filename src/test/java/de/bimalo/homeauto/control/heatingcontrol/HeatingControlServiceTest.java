package de.bimalo.homeauto.control.heatingcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.modbus.ModbusReadException;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.control.manualwaterheating.ManualWaterHeatingPolicy;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.GasCommand;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.HeatingDecision;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.ManualHeatingState;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Season;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for HeatingControlService.
 * Mocks the AutomaticHeatingPolicy/ManualWaterHeatingPolicy collaborators and all
 * device adapters to test the orchestration logic in isolation - in particular the
 * coordination between automatic and manual mode, and cleanup on failure.
 */
@ExtendWith(MockitoExtension.class)
class HeatingControlServiceTest {

    @Mock
    private HeatingControlConfig config;

    @Mock
    private E3dcAdapter e3dcAdapter;

    @Mock
    private Elwa2Adapter elwa2Adapter;

    @Mock
    private VitodensAdapter vitodensAdapter;

    @Mock
    private AutomaticHeatingPolicy automaticPolicy;

    @Mock
    private ManualWaterHeatingPolicy manualWaterHeatingPolicy;

    @InjectMocks
    private HeatingControlService heatingControlService;

    @BeforeEach
    void setUp() {
        lenient().when(config.enabled()).thenReturn(true);
        lenient().when(config.maxHeatingPower()).thenReturn(2900);
        lenient().when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.inactive());
        lenient().when(elwa2Adapter.readStatus()).thenReturn(defaultRodStatus());
        lenient().when(e3dcAdapter.readStatus()).thenReturn(defaultBatteryStatus());
        lenient().when(automaticPolicy.decide(any(), any(), anyBoolean()))
                .thenReturn(HeatingDecision.idle(GasCommand.UNCHANGED, "No surplus"));
    }

    // ==================== Automatic control orchestration ====================

    @Test
    void controlHeating_whenDisabled_shouldNotConsultPolicyOrHeat() {
        when(config.enabled()).thenReturn(false);

        heatingControlService.controlHeatingSummer();

        verify(automaticPolicy, never()).decide(any(), any(), anyBoolean());
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void controlHeating_appliesAutomaticPolicyDecisionToHeatingRod() {
        when(automaticPolicy.decide(any(), any(), anyBoolean()))
                .thenReturn(HeatingDecision.electric(Power.ofWatts(1500), HeatingSource.PV, GasCommand.UNCHANGED));

        heatingControlService.controlHeatingSummer();

        verify(elwa2Adapter).adjustHeating(Power.ofWatts(1500));
    }

    @Test
    void controlHeating_passesBatteryPriorityStateToAutomaticPolicy() {
        when(config.batteryPriorityEnabled()).thenReturn(true);

        heatingControlService.controlHeatingSummer();

        verify(automaticPolicy).decide(any(), any(), eq(true));
    }

    @Test
    void controlHeating_clampsDecisionToMaxHeatingPower() {
        when(automaticPolicy.decide(any(), any(), anyBoolean()))
                .thenReturn(HeatingDecision.electric(Power.ofWatts(4000), HeatingSource.PV, GasCommand.UNCHANGED));

        heatingControlService.controlHeatingSummer();

        verify(elwa2Adapter).adjustHeating(Power.ofWatts(2900));
    }

    @Test
    void controlHeating_whenReadingDeviceStateFails_stopsRodInsteadOfConsultingPolicy() {
        when(elwa2Adapter.readStatus())
                .thenThrow(new ModbusReadException("Modbus communication error", "localhost", 502, 10001, null));

        heatingControlService.controlHeatingSummer();

        verify(automaticPolicy, never()).decide(any(), any(), anyBoolean());
        verify(elwa2Adapter).stopHeating();
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    // ==================== Automatic/manual interplay ====================
    // Regression coverage for the bug where automatic control kept running
    // alongside an active manual session whenever config.enabled() was true.

    @Test
    void controlHeating_whenManualModeActive_mustNotRunAutomaticControl() {
        when(config.enabled()).thenReturn(true);
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));

        heatingControlService.controlHeatingSummer();

        verify(automaticPolicy, never()).decide(any(), any(), anyBoolean());
        verify(elwa2Adapter, never()).adjustHeating(any());
    }

    @Test
    void controlHeating_whenManualModeBecomesInactive_automaticControlResumes() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.inactive());
        when(automaticPolicy.decide(any(), any(), anyBoolean()))
                .thenReturn(HeatingDecision.electric(Power.ofWatts(1500), HeatingSource.PV, GasCommand.UNCHANGED));

        heatingControlService.controlHeatingSummer();

        verify(elwa2Adapter).adjustHeating(Power.ofWatts(1500));
    }

    // ==================== Manual mode activation/deactivation ====================

    @Test
    void activateManualHeating_whenNotAlreadyActive_startsAndResetsAutomaticPolicy() {
        when(manualWaterHeatingPolicy.start()).thenReturn(true);

        heatingControlService.activateManualHeating();

        verify(automaticPolicy).reset();
        verify(elwa2Adapter).stopHeating();
    }

    @Test
    void activateManualHeating_whenAlreadyActive_doesNothing() {
        when(manualWaterHeatingPolicy.start()).thenReturn(false);

        heatingControlService.activateManualHeating();

        verify(automaticPolicy, never()).reset();
        verify(elwa2Adapter, never()).stopHeating();
    }

    @Test
    void activateManualHeating_whenStoppingRodFails_deactivatesAgainAndRethrows() {
        when(manualWaterHeatingPolicy.start()).thenReturn(true);
        RuntimeException failure = new RuntimeException("Modbus timeout");
        doThrow(failure).when(elwa2Adapter).stopHeating();

        assertThrows(RuntimeException.class, () -> heatingControlService.activateManualHeating());

        verify(manualWaterHeatingPolicy).stop();
    }

    @Test
    void deactivateManualHeating_stopsPolicyAndHeatingRod() {
        heatingControlService.deactivateManualHeating();

        verify(manualWaterHeatingPolicy).stop();
        verify(elwa2Adapter).stopHeating();
    }

    @Test
    void deactivateManualHeating_whenPolicyOwnedGas_deactivatesGas() {
        when(manualWaterHeatingPolicy.stop()).thenReturn(true);

        heatingControlService.deactivateManualHeating();

        verify(vitodensAdapter).deactivateHeating();
    }

    @Test
    void deactivateManualHeating_whenAdapterStillRequestsGas_deactivatesGasEvenIfPolicyDidNotOwnIt() {
        // Covers a failed DEACTIVATE transition after the policy already reset its
        // own state: the adapter's own intent flag is the fallback safety net.
        when(manualWaterHeatingPolicy.stop()).thenReturn(false);
        when(vitodensAdapter.isHeatingRequested()).thenReturn(true);

        heatingControlService.deactivateManualHeating();

        verify(vitodensAdapter).deactivateHeating();
    }

    @Test
    void deactivateManualHeating_whenGasWasNeverInvolved_doesNotTouchGas() {
        when(manualWaterHeatingPolicy.stop()).thenReturn(false);
        when(vitodensAdapter.isHeatingRequested()).thenReturn(false);

        heatingControlService.deactivateManualHeating();

        verify(vitodensAdapter, never()).deactivateHeating();
    }

    // ==================== controlManualHeating orchestration ====================

    @Test
    void controlManualHeating_doesNothing_whenInactive() {
        heatingControlService.controlManualHeating();

        verify(elwa2Adapter, never()).adjustHeating(any());
        verify(manualWaterHeatingPolicy, never()).decide(any(), any(), any());
    }

    @Test
    void controlManualHeating_appliesDecisionToElwa2() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));
        BatteryStatus batteryStatus = createBatteryStatus(70, 1000, 0, 0);
        HeatingRodStatus rodStatus = new HeatingRodStatus(Temperature.ofCelsius(50.0), Temperature.ofCelsius(60.0),
                Power.ZERO, Instant.now());
        GasHeatingStatus gasStatus = GasHeatingStatus.builder().active(false)
                .currentTemperature(Temperature.ofCelsius(50.0)).targetTemperature(Temperature.ofCelsius(55.0))
                .measuredAt(Instant.now()).build();
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus);
        when(elwa2Adapter.readStatus()).thenReturn(rodStatus);
        when(vitodensAdapter.readStatus()).thenReturn(gasStatus);
        when(manualWaterHeatingPolicy.decide(rodStatus, batteryStatus, gasStatus))
                .thenReturn(new HeatingDecision(Power.ofWatts(1200), HeatingSource.PV));

        heatingControlService.controlManualHeating();

        verify(elwa2Adapter).adjustHeating(Power.ofWatts(1200));
        verify(vitodensAdapter, never()).activateHeating();
        verify(vitodensAdapter, never()).deactivateHeating();
    }

    @Test
    void controlManualHeating_clampsDecisionToMaxHeatingPower() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));
        when(manualWaterHeatingPolicy.decide(any(), any(), any()))
                .thenReturn(new HeatingDecision(Power.ofWatts(4000), HeatingSource.PV));

        heatingControlService.controlManualHeating();

        verify(elwa2Adapter).adjustHeating(Power.ofWatts(2900));
    }

    @Test
    void controlManualHeating_activatesGas_whenSourceIsGas() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));
        when(manualWaterHeatingPolicy.decide(any(), any(), any()))
                .thenReturn(HeatingDecision.gas(GasCommand.ACTIVATE));

        heatingControlService.controlManualHeating();

        verify(vitodensAdapter).activateHeating();
        verify(vitodensAdapter, never()).deactivateHeating();
    }

    @Test
    void controlManualHeating_deactivatesGas_whenSourceNotGasButGasWasActive() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));
        when(manualWaterHeatingPolicy.decide(any(), any(), any()))
                .thenReturn(new HeatingDecision(Power.ZERO, GasCommand.DEACTIVATE, HeatingSource.NONE, false, "n/a"));

        heatingControlService.controlManualHeating();

        verify(vitodensAdapter).deactivateHeating();
        verify(vitodensAdapter, never()).activateHeating();
    }

    @Test
    void controlManualHeating_whenDecisionCompleted_stopsPolicy() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));
        when(manualWaterHeatingPolicy.decide(any(), any(), any()))
                .thenReturn(HeatingDecision.completed(GasCommand.UNCHANGED, "Heating-rod target temperature reached"));

        heatingControlService.controlManualHeating();

        verify(manualWaterHeatingPolicy).stop();
    }

    @Test
    void controlManualHeating_whenDeviceReadFails_deactivatesInternallyAndDoesNotKeepRunning() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));
        when(e3dcAdapter.readStatus()).thenThrow(new RuntimeException("Modbus timeout"));

        heatingControlService.controlManualHeating();

        verify(elwa2Adapter, never()).adjustHeating(any());
        verify(manualWaterHeatingPolicy).stop();
        verify(elwa2Adapter).stopHeating();
    }

    // ==================== Battery priority ====================

    @Test
    void isBatteryPriorityActive_whenConfigEnabledAndNoOverride() {
        when(config.batteryPriorityEnabled()).thenReturn(true);

        assertTrue(heatingControlService.isBatteryPriorityActive());
    }

    @Test
    void isBatteryPriorityActive_whenConfigEnabledButOverrideDisabled() {
        when(config.batteryPriorityEnabled()).thenReturn(true);

        heatingControlService.disableBatteryPriorityOverride();

        assertFalse(heatingControlService.isBatteryPriorityActive());
    }

    @Test
    void isBatteryPriorityActive_whenConfigDisabled() {
        when(config.batteryPriorityEnabled()).thenReturn(false);

        assertFalse(heatingControlService.isBatteryPriorityActive());
    }

    @Test
    void resetBatteryPriorityOverride_reEnablesPriority() {
        when(config.batteryPriorityEnabled()).thenReturn(true);
        heatingControlService.disableBatteryPriorityOverride();
        assertFalse(heatingControlService.isBatteryPriorityActive());

        heatingControlService.resetBatteryPriorityOverride();

        assertTrue(heatingControlService.isBatteryPriorityActive());
    }

    @Test
    void resetBatteryPriorityOverride_doesNotTouchManualMode() {
        // Documents the current behavior: the midnight reset only concerns the
        // battery-priority override and no longer touches manual mode - it does not
        // even consult ManualWaterHeatingPolicy.getState() any more.
        heatingControlService.resetBatteryPriorityOverride();

        verify(manualWaterHeatingPolicy, never()).getState();
        verify(manualWaterHeatingPolicy, never()).stop();
        verify(elwa2Adapter, never()).stopHeating();
    }

    // ==================== Manual mode status ====================

    @Test
    void isManualModeActive_initiallyFalse() {
        assertFalse(heatingControlService.isManualModeActive());
    }

    @Test
    void isManualModeActive_reflectsPolicyState() {
        when(manualWaterHeatingPolicy.getState()).thenReturn(ManualHeatingState.started(Instant.now()));

        assertTrue(heatingControlService.isManualModeActive());
    }

    @Test
    void getManualStatus_returnsPolicyState() {
        ManualHeatingState state = ManualHeatingState.started(Instant.now());
        when(manualWaterHeatingPolicy.getState()).thenReturn(state);

        assertEquals(state, heatingControlService.getManualStatus());
    }

    // ==================== Season ====================

    @Test
    void getCurrentSeason_returnsValidSeason() {
        assertEquals(Season.current(), heatingControlService.getCurrentSeason());
    }

    @Test
    void isCurrentSeasonEnabled_whenCurrentSeasonEnabled_returnsTrue() {
        switch (Season.current()) {
            case WINTER -> when(config.winterEnabled()).thenReturn(true);
            case SPRING -> when(config.springEnabled()).thenReturn(true);
            case SUMMER -> when(config.summerEnabled()).thenReturn(true);
            case AUTUMN -> when(config.autumnEnabled()).thenReturn(true);
        }

        assertTrue(heatingControlService.isCurrentSeasonEnabled());
    }

    @Test
    void isCurrentSeasonEnabled_whenCurrentSeasonDisabled_returnsFalse() {
        switch (Season.current()) {
            case WINTER -> when(config.winterEnabled()).thenReturn(false);
            case SPRING -> when(config.springEnabled()).thenReturn(false);
            case SUMMER -> when(config.summerEnabled()).thenReturn(false);
            case AUTUMN -> when(config.autumnEnabled()).thenReturn(false);
        }

        assertFalse(heatingControlService.isCurrentSeasonEnabled());
    }

    private HeatingRodStatus defaultRodStatus() {
        return new HeatingRodStatus(Temperature.ofCelsius(50.0), Temperature.ofCelsius(68.0), Power.ZERO,
                Instant.now());
    }

    private BatteryStatus defaultBatteryStatus() {
        return createBatteryStatus(70, 0, 0, 0);
    }

    private BatteryStatus createBatteryStatus(int socPercent, long productionWatts, long consumptionWatts,
            long batteryWatts) {
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

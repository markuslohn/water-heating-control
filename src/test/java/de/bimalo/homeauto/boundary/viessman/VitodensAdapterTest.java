package de.bimalo.homeauto.boundary.viessman;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for VitodensAdapter.
 * Mocks Modbus device communication to test the control logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class VitodensAdapterTest {

    @Mock
    private VitodensConfig config;

    @Mock
    private VitodensModbusClient modbusClient;

    private VitodensAdapter service;

    @BeforeEach
    void setUp() {
        service = new VitodensAdapter(config, modbusClient);
    }

    @Test
    void readStatus_returnsActiveStatus_whenExternalRequestConnectedAndProgramIsFlowTemperatureSetpoint() {
        when(modbusClient.readExternalRequestStatus()).thenReturn(ExternalRequestMode.MODBUS_CONNECTION);
        when(modbusClient.readHotWaterHeatingProgramCurrentStatus())
                .thenReturn(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);
        when(modbusClient.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(52.5));
        when(modbusClient.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        Instant before = Instant.now();
        GasHeatingStatus status = service.readStatus();
        Instant after = Instant.now();

        assertTrue(status.active());
        assertEquals(Temperature.ofCelsius(52.5), status.currentTemperature());
        assertEquals(Temperature.ofCelsius(55.0), status.targetTemperature());
        assertFalse(status.measuredAt().isBefore(before));
        assertFalse(status.measuredAt().isAfter(after));
    }

    @Test
    void readStatus_returnsInactiveStatus_whenExternalRequestNotConnected() {
        when(modbusClient.readExternalRequestStatus()).thenReturn(ExternalRequestMode.NO_CONNECTION);
        when(modbusClient.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(45.0));
        when(modbusClient.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        GasHeatingStatus status = service.readStatus();

        assertFalse(status.active());
    }

    @Test
    void activateHeating_requestsModbusConnectionAndSetsFlowTemperatureSetpointProgram() {
        service.activateHeating();

        verify(modbusClient).writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
        verify(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);
        assertTrue(service.isHeatingRequested());
    }

    @Test
    void activateHeating_doesNothing_whenAlreadyActive() {
        service.activateHeating();

        service.activateHeating();

        verify(modbusClient, times(1)).writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
        verify(modbusClient, times(1)).writeHotWaterHeatingProgram(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);
    }

    @Test
    void activateHeating_rollsBackAndRethrows_whenProgramWriteFails() {
        RuntimeException programFailure = new RuntimeException("program write failed");
        doThrow(programFailure).when(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.activateHeating());

        assertSame(programFailure, thrown);
        verify(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        verify(modbusClient).writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
        assertFalse(service.isHeatingRequested());
    }

    @Test
    void deactivateHeating_releasesModbusConnectionAndInternalShouldValueProgram() {
        service.activateHeating();

        service.deactivateHeating();

        verify(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        verify(modbusClient).writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
        assertFalse(service.isHeatingRequested());
    }

    @Test
    void deactivateHeating_stillWritesExternalRequest_whenProgramWriteFails() {
        doThrow(new RuntimeException("program write failed")).when(modbusClient)
                .writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);

        assertThrows(RuntimeException.class, () -> service.deactivateHeating());

        verify(modbusClient).writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
    }

    @Test
    void deactivateHeating_throwsCombinedFailure_whenBothWritesFail() {
        RuntimeException programFailure = new RuntimeException("program write failed");
        RuntimeException requestFailure = new RuntimeException("request write failed");
        doThrow(programFailure).when(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        doThrow(requestFailure).when(modbusClient).writeExternalRequest(ExternalRequestMode.NO_CONNECTION);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.deactivateHeating());

        assertSame(programFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(requestFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void isHeatingRequested_returnsFalse_initially() {
        assertFalse(service.isHeatingRequested());
    }

    @Test
    void isHeatingRequested_returnsTrue_afterActivation() {
        service.activateHeating();

        assertTrue(service.isHeatingRequested());
    }

    @Test
    void keepExternalRequestAlive_doesNothingWhenNotActive() {
        service.keepExternalRequestAlive();

        verify(modbusClient, never()).writeExternalRequest(any(ExternalRequestMode.class));
    }

    @Test
    void keepExternalRequestAlive_refreshesConnectionWhileActive() {
        service.activateHeating();

        service.keepExternalRequestAlive();

        verify(modbusClient, times(2)).writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
    }

    @Test
    void shutdown_doesNotDeactivate_whenNotRequested() {
        service.shutdown();

        verify(modbusClient, never()).writeHotWaterHeatingProgram(any());
        verify(modbusClient).shutdown();
    }

    @Test
    void shutdown_deactivatesHeating_whenRequested() {
        service.activateHeating();

        service.shutdown();

        verify(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        verify(modbusClient).writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
        verify(modbusClient).shutdown();
    }

    @Test
    void shutdown_stillShutsDownModbusClient_whenDeactivationFails() {
        service.activateHeating();
        doThrow(new RuntimeException("deactivation failed")).when(modbusClient)
                .writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);

        assertDoesNotThrow(() -> service.shutdown());

        verify(modbusClient).shutdown();
    }
}

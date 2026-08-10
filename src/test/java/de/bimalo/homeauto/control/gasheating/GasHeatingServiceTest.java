package de.bimalo.homeauto.control.gasheating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.viessman.ExternalRequestMode;
import de.bimalo.homeauto.boundary.viessman.HotWaterProgram;
import de.bimalo.homeauto.boundary.viessman.VitodensModbusClient;
import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.Volume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for GasHeatingService.
 * Mocks Modbus device communication to test the control logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class GasHeatingServiceTest {

    @Mock
    private GasHeatingConfig config;

    @Mock
    private VitodensModbusClient modbusClient;

    private GasHeatingService service;

    @BeforeEach
    void setUp() {
        service = new GasHeatingService(config, modbusClient);
    }

    @Test
    void readHotWaterCurrentTemperature_returnsTemperatureFromModbusClient() {
        when(modbusClient.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(52.5));

        assertEquals(Temperature.ofCelsius(52.5), service.readHotWaterCurrentTemperature());
    }

    @Test
    void readOutsideTemperature_returnsTemperatureFromModbusClient() {
        when(modbusClient.readOutsideTemperature()).thenReturn(Temperature.ofCelsius(8.3));

        assertEquals(Temperature.ofCelsius(8.3), service.readOutsideTemperature());
    }

    @Test
    void readHotWaterTargetTemperature_returnsTemperatureFromModbusClient() {
        when(modbusClient.readHotWaterTargetTemperature()).thenReturn(Temperature.ofCelsius(55.0));

        assertEquals(Temperature.ofCelsius(55.0), service.readHotWaterTargetTemperature());
    }

    @Test
    void readHotWaterGasConsumptionToday_returnsVolumeFromModbusClient() {
        when(modbusClient.readHotWaterGasConsumptionToday()).thenReturn(Volume.ofCubicMeters(3.2));

        assertEquals(Volume.ofCubicMeters(3.2), service.readHotWaterGasConsumptionToday());
    }

    @Test
    void readHotWaterGasConsumptionThisMonth_returnsVolumeFromModbusClient() {
        when(modbusClient.readHotWaterGasConsumptionThisMonth()).thenReturn(Volume.ofCubicMeters(41.7));

        assertEquals(Volume.ofCubicMeters(41.7), service.readHotWaterGasConsumptionThisMonth());
    }

    @Test
    void continueHeating_requestsModbusConnection() {
        service.continueHeating();

        verify(modbusClient).writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
    }

    @Test
    void activateHeating_requestsModbusConnectionAndSetsFlowTemperatureSetpointProgram() {
        service.activateHeating();

        verify(modbusClient).writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
        verify(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);
    }

    @Test
    void deactivateHeating_releasesModbusConnectionAndInternalShouldValueProgram() {
        service.deactivateHeating();

        verify(modbusClient).writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        verify(modbusClient).writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
    }

    @Test
    void isHeatingActive_returnsTrueWhenDeviceReportsModbusConnection() {
        when(modbusClient.readExternalRequestStatus()).thenReturn(ExternalRequestMode.MODBUS_CONNECTION);
        when(modbusClient.readHotWaterHeatingProgramCurrentStatus())
                .thenReturn(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);

        assertTrue(service.isHeatingActive());
    }

    @Test
    void isHeatingActive_returnsFalseWhenDeviceReportsNoConnection() {
        when(modbusClient.readExternalRequestStatus()).thenReturn(ExternalRequestMode.NO_CONNECTION);

        assertFalse(service.isHeatingActive());
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
}

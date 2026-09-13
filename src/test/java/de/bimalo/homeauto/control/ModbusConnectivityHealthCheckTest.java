package de.bimalo.homeauto.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.goecharger.GoEchargerAdapter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for ModbusConnectivityHealthCheck.
 * Verifies that only the required devices (E3DC, ELWA2) affect the overall
 * readiness status, while the optional devices (Vitodens, go-eCharger) are
 * reported for visibility only.
 */
@ExtendWith(MockitoExtension.class)
class ModbusConnectivityHealthCheckTest {

    @Mock
    private E3dcAdapter e3dcAdapter;

    @Mock
    private Elwa2Adapter elwa2Adapter;

    @Mock
    private VitodensAdapter vitodensAdapter;

    @Mock
    private GoEchargerAdapter goEchargerAdapter;

    @InjectMocks
    private ModbusConnectivityHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        lenient().when(e3dcAdapter.isConnected()).thenReturn(true);
        lenient().when(elwa2Adapter.isConnected()).thenReturn(true);
        lenient().when(vitodensAdapter.isConnected()).thenReturn(true);
        lenient().when(goEchargerAdapter.isConnected()).thenReturn(true);
    }

    @Test
    void call_reportsUp_whenAllDevicesConnected() {
        HealthCheckResponse response = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void call_reportsDown_whenRequiredE3dcIsDisconnected() {
        when(e3dcAdapter.isConnected()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }

    @Test
    void call_reportsDown_whenRequiredElwa2IsDisconnected() {
        when(elwa2Adapter.isConnected()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }

    @Test
    void call_staysUp_whenOnlyOptionalVitodensIsDisconnected() {
        when(vitodensAdapter.isConnected()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void call_staysUp_whenOnlyOptionalGoEchargerIsDisconnected() {
        when(goEchargerAdapter.isConnected()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void call_reportsPerDeviceDataForAllFourDevices() {
        when(vitodensAdapter.isConnected()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertEquals(Boolean.TRUE, response.getData().orElseThrow().get("e3dc"));
        assertEquals(Boolean.TRUE, response.getData().orElseThrow().get("elwa2"));
        assertEquals(Boolean.FALSE, response.getData().orElseThrow().get("vitodens"));
        assertEquals(Boolean.TRUE, response.getData().orElseThrow().get("goecharger"));
    }
}

package de.bimalo.homeauto.control;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.goecharger.GoEchargerAdapter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether the Modbus connections to the configured devices are
 * currently up. A device being down does not crash the application (see
 * {@code AbstractModbusClient}'s on-demand reconnect), but should be visible
 * to operators rather than only showing up as stale data in the REST API.
 *
 * <p>
 * Only the battery storage (E3DC) and heating rod (ELWA2) are required for
 * the core PV-surplus water heating function; the gas heating fallback
 * (Vitodens) and the wallbox (go-eCharger) are optional and are reported for
 * visibility only, without affecting the overall readiness status.
 */
@Readiness
public class ModbusConnectivityHealthCheck implements HealthCheck {

    @Inject
    E3dcAdapter e3dcAdapter;

    @Inject
    Elwa2Adapter elwa2Adapter;

    @Inject
    VitodensAdapter vitodensAdapter;

    @Inject
    GoEchargerAdapter goEchargerAdapter;

    @Override
    public HealthCheckResponse call() {
        boolean e3dcConnected = e3dcAdapter.isConnected();
        boolean elwa2Connected = elwa2Adapter.isConnected();
        boolean vitodensConnected = vitodensAdapter.isConnected();
        boolean goEchargerConnected = goEchargerAdapter.isConnected();

        boolean requiredDevicesConnected = e3dcConnected && elwa2Connected;

        return HealthCheckResponse.named("modbus-devices")
                .status(requiredDevicesConnected)
                .withData("e3dc", e3dcConnected)
                .withData("elwa2", elwa2Connected)
                .withData("vitodens", vitodensConnected)
                .withData("goecharger", goEchargerConnected)
                .build();
    }
}

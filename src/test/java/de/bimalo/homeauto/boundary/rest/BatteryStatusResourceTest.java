package de.bimalo.homeauto.boundary.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the repeated "read live status, fall back to last known status
 * (marked stale) on failure, 503 if nothing usable is known" pattern shared
 * by BatteryStatusResource, WallboxStatusResource, GasHeatingStatusResource
 * and HeatingControlResource, using BatteryStatusResource as the
 * representative case. The age-limited fallback logic itself is unit-tested
 * once, in isolation, by StatusFallbackTest.
 */
@QuarkusTest
class BatteryStatusResourceTest {

    private E3dcAdapter e3dcAdapter;
    private HeatingControlService heatingControlService;

    @BeforeEach
    void setUp() {
        e3dcAdapter = mock(E3dcAdapter.class);
        QuarkusMock.installMockForType(e3dcAdapter, E3dcAdapter.class);

        heatingControlService = mock(HeatingControlService.class);
        QuarkusMock.installMockForType(heatingControlService, HeatingControlService.class);
    }

    @Test
    void getStatus_returnsLiveStatus_whenDeviceReachable() {
        when(e3dcAdapter.readStatus()).thenReturn(batteryStatus(2000, 500, 300, 70, Instant.now()));

        given()
                .when().get("/api/battery/status")
                .then()
                .statusCode(200)
                .body("productionPower.watts", equalTo(2000))
                .body("batteryStateOfCharge.value", equalTo(70))
                .body("stale", equalTo(false));
    }

    @Test
    void getStatus_fallsBackToLastKnownStatus_whenDeviceReadFailsAndCacheIsRecent() {
        when(e3dcAdapter.readStatus()).thenThrow(new RuntimeException("Modbus timeout"));
        when(e3dcAdapter.getLastKnownStatus())
                .thenReturn(Optional.of(batteryStatus(1500, 400, 0, 60, Instant.now().minusSeconds(30))));

        given()
                .when().get("/api/battery/status")
                .then()
                .statusCode(200)
                .body("productionPower.watts", equalTo(1500))
                .body("stale", equalTo(true));
    }

    @Test
    void getStatus_returns503_whenDeviceReadFailsAndNothingIsKnown() {
        when(e3dcAdapter.readStatus()).thenThrow(new RuntimeException("Modbus timeout"));
        when(e3dcAdapter.getLastKnownStatus()).thenReturn(Optional.empty());

        given()
                .when().get("/api/battery/status")
                .then()
                .statusCode(503);
    }

    @Test
    void getStatus_returns503_whenLastKnownStatusIsTooOld() {
        when(e3dcAdapter.readStatus()).thenThrow(new RuntimeException("Modbus timeout"));
        when(e3dcAdapter.getLastKnownStatus())
                .thenReturn(Optional.of(batteryStatus(1500, 400, 0, 60, Instant.now().minus(Duration.ofMinutes(5)))));

        given()
                .when().get("/api/battery/status")
                .then()
                .statusCode(503);
    }

    @Test
    void getBatteryPriorityStatus_reflectsHeatingControlService() {
        when(heatingControlService.isBatteryPriorityActive()).thenReturn(true);

        given()
                .when().get("/api/battery/priority")
                .then()
                .statusCode(200)
                .body(equalTo("{\"active\": true}"));
    }

    @Test
    void setBatteryPriority_whenDisabled_callsDisableOverride() {
        when(heatingControlService.isBatteryPriorityActive()).thenReturn(false);

        given()
                .queryParam("disabled", true)
                .when().post("/api/battery/priority")
                .then()
                .statusCode(200);

        verify(heatingControlService).disableBatteryPriorityOverride();
    }

    @Test
    void setBatteryPriority_whenIllegalState_returns400() {
        doThrow(new IllegalStateException("Battery priority feature is disabled"))
                .when(heatingControlService).enableBatteryPriorityOverride();

        given()
                .queryParam("disabled", false)
                .when().post("/api/battery/priority")
                .then()
                .statusCode(400);
    }

    private static BatteryStatus batteryStatus(long production, long consumption, long battery, int soc,
            Instant measuredAt) {
        return BatteryStatus.builder()
                .measuredAt(measuredAt)
                .productionPower(Power.ofWatts(production))
                .consumptionPower(Power.ofWatts(consumption))
                .batteryPower(Power.ofWatts(battery))
                .gridPower(Power.ZERO)
                .batteryStateOfCharge(Percentage.of(soc))
                .build();
    }
}

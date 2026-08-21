package de.bimalo.homeauto.boundary.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.ManualHeatingState;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ManualWaterHeatingResourceTest {

    private HeatingControlService heatingControlService;

    @BeforeEach
    void setUp() {
        heatingControlService = mock(HeatingControlService.class);
        QuarkusMock.installMockForType(heatingControlService, HeatingControlService.class);
    }

    @Test
    void getStatus_reflectsCurrentManualHeatingState() {
        when(heatingControlService.getManualStatus())
                .thenReturn(ManualHeatingState.started(Instant.now()).withSource(HeatingSource.PV));

        given()
                .when().get("/api/manual-water-heating/status")
                .then()
                .statusCode(200)
                .body("active", equalTo(true))
                .body("source", equalTo("PV"));
    }

    @Test
    void start_activatesManualHeatingAndReturnsUpdatedStatus() {
        when(heatingControlService.getManualStatus()).thenReturn(ManualHeatingState.started(Instant.now()));

        given()
                .when().post("/api/manual-water-heating/start")
                .then()
                .statusCode(200)
                .body("active", equalTo(true));

        verify(heatingControlService).activateManualHeating();
    }

    @Test
    void stop_deactivatesManualHeatingAndReturnsUpdatedStatus() {
        when(heatingControlService.getManualStatus()).thenReturn(ManualHeatingState.inactive());

        given()
                .when().post("/api/manual-water-heating/stop")
                .then()
                .statusCode(200)
                .body("active", equalTo(false));

        verify(heatingControlService).deactivateManualHeating();
    }
}

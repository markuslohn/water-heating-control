package de.bimalo.homeauto.boundary.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.control.VersionService;
import de.bimalo.homeauto.entity.VersionInfo;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VersionResourceTest {

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = mock(VersionService.class);
        QuarkusMock.installMockForType(versionService, VersionService.class);
    }

    @Test
    void getVersion_returnsVersionInfoFromService() {
        when(versionService.getVersionInfo())
                .thenReturn(new VersionInfo("1.2.3", "abc1234", "main", "2026-01-01 00:00:00"));

        given()
                .when().get("/api/version")
                .then()
                .statusCode(200)
                .body("version", equalTo("1.2.3"))
                .body("gitCommit", equalTo("abc1234"))
                .body("gitBranch", equalTo("main"));
    }
}

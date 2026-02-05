package de.bimalo.homeauto.control;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import de.bimalo.homeauto.entity.VersionInfo;
import jakarta.inject.Inject;

@Liveness
public class VersionHealthCheck implements HealthCheck {

    @Inject
    VersionService versionService;

    @Override
    public HealthCheckResponse call() {
        VersionInfo info = versionService.getVersionInfo();
        return HealthCheckResponse
                .named("version")
                .up()
                .withData("version", info.version())
                .withData("commit", info.gitCommit())
                .withData("branch", info.gitBranch())
                .withData("buildTime", info.buildTime())
                .build();
    }
}

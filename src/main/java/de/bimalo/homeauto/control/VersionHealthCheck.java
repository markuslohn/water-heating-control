package de.bimalo.homeauto.control;

import de.bimalo.homeauto.entity.VersionInfo;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
public class VersionHealthCheck implements HealthCheck {

    private final VersionService versionService;

    @Inject
    public VersionHealthCheck(VersionService versionService) {
        this.versionService = versionService;
    }

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

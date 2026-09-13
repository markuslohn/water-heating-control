package de.bimalo.homeauto.control;

import de.bimalo.homeauto.entity.VersionInfo;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

@Slf4j
@ApplicationScoped
public class VersionService {

    private final VersionInfo versionInfo;

    public VersionService() {
        this.versionInfo = loadVersionInfo();
    }

    private VersionInfo loadVersionInfo() {
        Properties gitProps = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/git.properties")) {
            if (in != null) {
                gitProps.load(in);
            }
        } catch (IOException e) {
            log.warn("Could not load git.properties", e);
        }

        return new VersionInfo(
                ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class)
                        .orElse(gitProps.getProperty("git.commit.id.describe", "unknown")),
                gitProps.getProperty("git.commit.id.abbrev", "unknown"),
                gitProps.getProperty("git.branch", "unknown"),
                gitProps.getProperty("git.commit.time", "unknown"));
    }

    public VersionInfo getVersionInfo() {
        return versionInfo;
    }

    // Besserer Logger: Lifecycle-Event nutzen
    void logVersionOnStartup(@Observes @Priority(1) StartupEvent event) {
        log.info("""
                ========================================
                Application Version Information
                ----------------------------------------
                Version:    {}
                Git Commit: {}
                Git Branch: {}
                Build Time: {}
                ========================================
                """,
                versionInfo.version(),
                versionInfo.gitCommit(),
                versionInfo.gitBranch(),
                versionInfo.buildTime());
    }
}

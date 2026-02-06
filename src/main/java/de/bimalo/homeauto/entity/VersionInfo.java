package de.bimalo.homeauto.entity;

public record VersionInfo(
        String version,
        String gitCommit,
        String gitBranch,
        String buildTime) {
}

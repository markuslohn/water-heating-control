package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.control.VersionService;
import de.bimalo.homeauto.entity.VersionInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/version")
public class VersionResource {

    @Inject
    VersionService versionService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public VersionInfo getVersion() {
        return versionService.getVersionInfo();
    }
}

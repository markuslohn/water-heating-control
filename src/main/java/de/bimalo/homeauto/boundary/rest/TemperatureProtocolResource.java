package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for downloading the daily temperature protocol (heating rod
 * vs. gas heating hot water temperature).
 */
@Slf4j
@Path("/api/temperature-protocol")
public class TemperatureProtocolResource {

    @Inject
    TemperatureProtocolFileWriter fileWriter;

    /**
     * Returns the temperature protocol CSV for the given date (default: today,
     * UTC - matching the date the entries are filed under).
     *
     * @param date optional date in YYYY-MM-DD format
     */
    @GET
    @Produces("text/csv")
    public Response getProtocol(@QueryParam("date") String date) {
        LocalDate requestedDate;
        try {
            requestedDate = date != null ? LocalDate.parse(date) : LocalDate.now(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid date format, expected YYYY-MM-DD")
                    .build();
        }

        log.debug("REST: Getting temperature protocol for {}", requestedDate);
        String content = fileWriter.read(requestedDate);

        return Response.ok(content)
                .header("Content-Disposition",
                        "attachment; filename=\"temperature-protocol-" + requestedDate + ".csv\"")
                .build();
    }
}

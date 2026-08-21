package de.bimalo.homeauto.boundary.temperatureprotocol;

import de.bimalo.homeauto.entity.TemperatureLogEntry;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists temperature protocol entries to a daily CSV file and reads them
 * back. One file per day, named after the entry's date (UTC).
 */
@Slf4j
@ApplicationScoped
public class TemperatureProtocolFileWriter {

    private static final String HEADER = "timestamp;heating_rod_temperature_celsius;gas_heating_temperature_celsius";

    private final Path baseDirectory;

    public TemperatureProtocolFileWriter() {
        this(Path.of("data", "temperature-protocol"));
    }

    TemperatureProtocolFileWriter(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * Appends the given entry as a CSV line to that day's protocol file,
     * creating the file (with header) if it does not exist yet.
     */
    public void append(TemperatureLogEntry entry) {
        Path file = fileFor(LocalDate.ofInstant(entry.getTimestamp(), ZoneOffset.UTC));
        String line = String.format(Locale.ROOT, "%s;%.1f;%.1f%s",
                entry.getTimestamp(),
                entry.getHeatingRodTemperature().celsius(),
                entry.getGasHeatingTemperature().celsius(),
                System.lineSeparator());

        try {
            Files.createDirectories(baseDirectory);
            boolean isNewFile = Files.notExists(file);
            String content = isNewFile ? HEADER + System.lineSeparator() + line : line;
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write temperature protocol entry to " + file, e);
        }
    }

    /**
     * Reads the protocol file content for the given date.
     *
     * @return the file content, or an empty string if no entries exist for that date
     */
    public String read(LocalDate date) {
        Path file = fileFor(date);
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read temperature protocol file " + file, e);
        }
    }

    private Path fileFor(LocalDate date) {
        return baseDirectory.resolve(date + ".csv");
    }
}

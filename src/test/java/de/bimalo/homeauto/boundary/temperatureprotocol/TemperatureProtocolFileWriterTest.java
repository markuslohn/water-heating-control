package de.bimalo.homeauto.boundary.temperatureprotocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.TemperatureLogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemperatureProtocolFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void append_createsFileWithHeader_onFirstEntry() throws IOException {
        TemperatureProtocolFileWriter writer = new TemperatureProtocolFileWriter(tempDir);
        Instant timestamp = Instant.parse("2026-08-17T10:00:00Z");

        writer.append(TemperatureLogEntry.builder()
                .timestamp(timestamp)
                .heatingRodTemperature(Temperature.ofCelsius(62.5))
                .gasHeatingTemperature(Temperature.ofCelsius(58.0))
                .build());

        Path file = tempDir.resolve("2026-08-17.csv");
        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        assertEquals(
                "timestamp;heating_rod_temperature_celsius;gas_heating_temperature_celsius"
                        + System.lineSeparator()
                        + "2026-08-17T10:00:00Z;62.5;58.0" + System.lineSeparator(),
                content);
    }

    @Test
    void append_appendsWithoutRepeatingHeader_onSubsequentEntries() {
        TemperatureProtocolFileWriter writer = new TemperatureProtocolFileWriter(tempDir);

        writer.append(TemperatureLogEntry.builder()
                .timestamp(Instant.parse("2026-08-17T10:00:00Z"))
                .heatingRodTemperature(Temperature.ofCelsius(62.5))
                .gasHeatingTemperature(Temperature.ofCelsius(58.0))
                .build());
        writer.append(TemperatureLogEntry.builder()
                .timestamp(Instant.parse("2026-08-17T10:05:00Z"))
                .heatingRodTemperature(Temperature.ofCelsius(63.0))
                .gasHeatingTemperature(Temperature.ofCelsius(58.2))
                .build());

        String content = writer.read(LocalDate.of(2026, 8, 17));
        assertEquals(1, content.lines().filter(line -> line.startsWith("timestamp;")).count());
        assertEquals(3, content.lines().count()); // header + 2 entries
    }

    @Test
    void read_returnsEmptyString_whenNoFileExistsForDate() {
        TemperatureProtocolFileWriter writer = new TemperatureProtocolFileWriter(tempDir);

        String content = writer.read(LocalDate.of(2020, 1, 1));

        assertEquals("", content);
    }

    @Test
    void append_filesEntryUnderUtcDateOfTimestamp() {
        TemperatureProtocolFileWriter writer = new TemperatureProtocolFileWriter(tempDir);

        writer.append(TemperatureLogEntry.builder()
                .timestamp(Instant.parse("2026-08-17T23:30:00Z"))
                .heatingRodTemperature(Temperature.ofCelsius(60.0))
                .gasHeatingTemperature(Temperature.ofCelsius(55.0))
                .build());

        assertTrue(Files.exists(tempDir.resolve("2026-08-17.csv")));
    }
}

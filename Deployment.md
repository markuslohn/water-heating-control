# Deployment Guide

This guide describes how to run the published container image in a Docker/Compose
environment such as OpenMediaVault (OMV), including how the settings from
`src/main/resources/application.properties` (see [README.md](README.md#configuration)
for the full reference) map to container environment variables.

## Container Image

- Image: `ghcr.io/markuslohn/water-heating-control:<tag>`
- Listens on port `8080` (web UI, REST API, health endpoints)
- Runs as a fixed, non-root user (UID `185`) with working directory `/deployments`,
  per Quarkus's default container-image conventions. This image does **not**
  implement the `PUID`/`PGID` convention used by some other container images
  (e.g. linuxserver.io) — see [Notes and caveats](#notes-and-caveats) below.

## How configuration properties map to environment variables

Any property from `application.properties` can be overridden with an environment
variable. Quarkus (via SmallRye Config) derives the expected environment variable
name from the property name using this rule:

> Replace every character that is not alphanumeric or `_` with `_`, then convert
> the whole name to upper case.

Both `.` **and** `-` count as "not alphanumeric" and are replaced the same way. So
a property such as `heatingctl.max-heating-power` becomes `HEATINGCTL_MAX_HEATING_POWER`
— the dash inside `max-heating-power` is converted just like the dots, **not** kept
as a literal dash.

Sources:
[Quarkus Configuration reference guide](https://quarkus.io/guides/config-reference/#environment-variables),
[SmallRye Config – Environment Variables](https://smallrye.io/smallrye-config/Main/config/environment-variables/)

A property with a quoted map key, such as the log category override already
present in `application.properties`
(`quarkus.log.category."de.bimalo.homeauto.control".level`), follows the same
rule but represents the quotes with a *double* underscore around the quoted
segment: `QUARKUS_LOG_CATEGORY__DE_BIMALO_HOMEAUTO_CONTROL__LEVEL`.

### Reference table

| `application.properties` key | Environment variable |
|---|---|
| `quarkus.http.host` | `QUARKUS_HTTP_HOST` |
| `quarkus.http.port` | `QUARKUS_HTTP_PORT` |
| `quarkus.profile` | `QUARKUS_PROFILE` |
| `quarkus.log.level` | `QUARKUS_LOG_LEVEL` |
| `heatingctl.enabled` | `HEATINGCTL_ENABLED` |
| `heatingctl.temperature-hysteresis` | `HEATINGCTL_TEMPERATURE_HYSTERESIS` |
| `heatingctl.max-heating-power` | `HEATINGCTL_MAX_HEATING_POWER` |
| `heatingctl.solar-power-reduction-percent` | `HEATINGCTL_SOLAR_POWER_REDUCTION_PERCENT` |
| `heatingctl.battery-priority-enabled` | `HEATINGCTL_BATTERY_PRIORITY_ENABLED` |
| `heatingctl.battery-priority-threshold` | `HEATINGCTL_BATTERY_PRIORITY_THRESHOLD` |
| `heatingctl.battery-reserved-power` | `HEATINGCTL_BATTERY_RESERVED_POWER` |
| `elwa2.modbus.host` (heating rod) | `ELWA2_MODBUS_HOST` |
| `elwa2.modbus.port` (heating rod) | `ELWA2_MODBUS_PORT` |
| `e3dc.modbus.host` (battery storage) | `E3DC_MODBUS_HOST` |
| `e3dc.modbus.port` (battery storage) | `E3DC_MODBUS_PORT` |
| `vitodens.modbus.host` (gas heating) | `VITODENS_MODBUS_HOST` |
| `vitodens.modbus.port` (gas heating) | `VITODENS_MODBUS_PORT` |

For any other property not listed here, apply the rule above yourself, or check
the full list in [README.md](README.md#configuration).

## Persistent data

When `tempprotocol.enabled=true` (the default), the daily temperature protocol
CSV files are written to `data/temperature-protocol/` relative to the JVM's
working directory, which is `/deployments` inside this container image — i.e.
`/deployments/data/temperature-protocol/*.csv`. Mount a volume there if you want
this history to survive container recreation/updates; otherwise it is lost every
time the container is replaced.

## Example `docker-compose.yml`

The block below lists **every** configurable property from `application.properties`
(plus `TZ` and the two `quarkus.http.*` runtime settings), each pre-filled with
its default value. The handful of entries already using `${VARIABLE}` are kept
as OMV stack variables, matching the ones in the original draft; every other
line can be parametrized the same way if you want to tune it per-deployment,
or just edited in place.

```yaml
services:
  hot-water-control:
    image: ghcr.io/markuslohn/water-heating-control:${RELEASE_TAG}
    container_name: water-heating-control
    restart: unless-stopped
    environment:
      - TZ=${{ tz }}

      # --- HTTP ---
      - QUARKUS_HTTP_HOST=0.0.0.0
      - QUARKUS_HTTP_PORT=8080

      # --- Modbus Connection Settings ---
      - E3DC_MODBUS_HOST=192.168.1.1
      - E3DC_MODBUS_PORT=502
      - ELWA2_MODBUS_HOST=192.168.1.2
      - ELWA2_MODBUS_PORT=502
      - VITODENS_MODBUS_HOST=192.168.1.3
      - VITODENS_MODBUS_PORT=502
      - VITODENS_KEEP_ALIVE_INTERVAL=20s
      - GOECHARGER_MODBUS_HOST=192.168.1.4
      - GOECHARGER_MODBUS_PORT=502

      # --- Temperature Protocol (daily heating-rod/gas-heating CSV log) ---
      - TEMPPROTOCOL_ENABLED=true

      # --- REST Status Endpoint fallback ---
      - RESTSTATUS_MAX_CACHE_AGE=2m

      # --- Heating Control (automatic PV-surplus mode) ---
      - HEATINGCTL_ENABLED=true
      - HEATINGCTL_MIN_SURPLUS_POWER=100
      - HEATINGCTL_MAX_HEATING_POWER=2900
      - HEATINGCTL_TEMPERATURE_HYSTERESIS=7.0
      - HEATINGCTL_SOLAR_POWER_REDUCTION_PERCENT=5
      - HEATINGCTL_POWER_INCREASE_SMOOTHING_WINDOW=150s
      - HEATINGCTL_MIN_POWER_CHANGE_THRESHOLD=100

      # --- Battery Priority ---
      - HEATINGCTL_BATTERY_PRIORITY_ENABLED=true
      - HEATINGCTL_BATTERY_PRIORITY_THRESHOLD=50
      - HEATINGCTL_BATTERY_RESERVED_POWER=1000

      # --- Manual Water Heating Mode ---
      - MANUALWATERHEATING_MAXIMUM_DURATION=30m
      - MANUALWATERHEATING_HEATING_ROD_LOW_TEMPERATURE_THRESHOLD=42.0
      - MANUALWATERHEATING_BATTERY_SOC_START_THRESHOLD=65
      - MANUALWATERHEATING_BATTERY_SOC_STOP_THRESHOLD=50
      - MANUALWATERHEATING_MAX_BATTERY_SOC_DROP_PERCENT=10
      - MANUALWATERHEATING_MAX_BATTERY_HEATING_POWER=800
      - MANUALWATERHEATING_BATTERY_MAX_DISCHARGE_POWER=1400
      - MANUALWATERHEATING_GAS_HEATING_LOW_TEMPERATURE_THRESHOLD=35.0
      - MANUALWATERHEATING_GAS_HEATING_SHUTOFF_TEMPERATURE_OFFSET=2.0

      # --- Seasonal Operating Hours (leading "*" in cron expressions requires quoting) ---
      - HEATINGCTL_WINTER_ENABLED=true
      - "HEATINGCTL_WINTER_CRON=*/40 * 9-15 * 11,12,1,2 ?"
      - HEATINGCTL_SPRING_ENABLED=true
      - "HEATINGCTL_SPRING_CRON=*/40 * 8-17 * 3,4 ?"
      - HEATINGCTL_AUTUMN_ENABLED=true
      - "HEATINGCTL_AUTUMN_CRON=*/40 * 8-17 * 9,10 ?"
      - HEATINGCTL_SUMMER_ENABLED=true
      - "HEATINGCTL_SUMMER_CRON=*/40 * 7-19 * 5-8 ?"

      # --- Quarkus Scheduler ---
      - QUARKUS_SCHEDULER_ENABLED=true
      - QUARKUS_SCHEDULER_START_MODE=normal

      # --- Logging ---
      - QUARKUS_LOG_LEVEL=${LOG_LEVEL}
      - QUARKUS_LOG_CATEGORY__DE_BIMALO_HOMEAUTO_CONTROL__LEVEL=DEBUG
      - "QUARKUS_LOG_CONSOLE_FORMAT=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{2.}] (%t) %s%e%n"
    volumes:
      - /srv/water-heating-control/data:/deployments/data
    expose:
      - "8080"
    networks:
      - proxy
 
networks:
  proxy:
    name: proxy
    external: true
```

The `${VARIABLE}` values (`RELEASE_TAG`, ...) on the right-hand side
are your own OMV environment/stack variables; only the left-hand side (the
actual container environment variable name) has to
match a real `application.properties` key for Quarkus to pick it up. Any line
you turn into a `${VARIABLE}` reference yourself follows the same rule; a value
starting with `*` (like the cron expressions above) must stay quoted either way.

## Notes and caveats

- **`PUID`/`PGID` are not honored.** This image runs as a fixed UID `185` and
  does not include an entrypoint that remaps ownership based on `PUID`/`PGID`
  (unlike linuxserver.io-style images). If you mount a host directory (e.g. for
  `/deployments/data`), make sure it is writable by UID `185` on the host, for
  example: `chown -R 185:185 /srv/water-heating-control/data`. Setting `PUID`/
  `PGID` as environment variables is harmless but has no effect.
- **`QUARKUS_PROFILE=dev` is unusual for a deployed container.** The `dev`
  profile is intended for local development (`./gradlew quarkusDev`) and is not
  needed to run the packaged image; omit it (or set `QUARKUS_PROFILE=prod`,
  the default) unless you have a specific reason to use it.
- **ELWA2 control timeout:** set the control timeout on the ELWA2 device itself
  to 70 seconds, so it keeps accepting commands from this application between
  control cycles (see [README.md](README.md#elwa2-configuration)).
- **Set `TZ` to your local timezone.** The seasonal schedules
  (`heatingctl.winter-cron`, etc.) and the automatic season detection both use
  the JVM's default timezone. Without `TZ` set, the container defaults to UTC,
  which can shift the configured operating hours and season boundaries away
  from local time.
- **Network reachability:** the container needs TCP access to the Modbus TCP
  port (`502` by default) of every configured device (ELWA2, E3/DC, and
  optionally Vitodens and go-eCharger). Make sure routing/firewalling between
  the Docker network and your home network/VLAN allows this.
- Verify the deployment via the health endpoints once the container is up:
  `GET http://<host>:8080/q/health/ready` should report `UP` once the
  required devices (ELWA2, E3/DC) are reachable.

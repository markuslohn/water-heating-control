# Water Heating Control

A Quarkus-based application for intelligent control of the ELWA2 heating rod using solar surplus power, with improved integration for Viessmann gas heating systems.

## Overview

This application enables smart control of the my-PV ELWA2 heating rod by utilizing solar surplus power from an E3/DC battery storage system. The key features include:

- **Solar Surplus Optimization**: Automatically adjusts heating power based on available solar surplus
- **Manual Water Heating Mode**: Rule-based manual mode that heats hot water from PV surplus and, if needed, limited battery power - without ever drawing from the grid - falling back to gas heating when no electrical power is available
- **Gas Heating Integration**: Improved coordination with Viessmann gas heating systems
- **Precise Temperature Management**: Heats water up to the target temperature configured on the ELWA2 device

### Why This Application?

The ELWA2's built-in boost function always charges at maximum power, which is not ideal for:
- Optimizing self-consumption of solar energy
- Preventing unnecessary grid consumption
- Fine-tuned control based on actual available surplus power

This application provides **minute-by-minute monitoring and adjustment** to ensure optimal use of solar energy while preventing overloading or unnecessary power consumption.

## Communication

The application communicates via **Modbus TCP** with:
- **ELWA2 Heating Rod** - for power control and temperature monitoring
- **E3/DC Battery Storage** - for reading solar production, consumption, and available surplus

### ELWA2 Configuration

**Important**: Set the control timeout on your ELWA2 device to **70 seconds**. This ensures the device waits for commands from this application before reverting to default behavior.

## Web Interface

The application includes a modern, responsive web interface for monitoring and manual control.

**Access the web interface at:** `http://localhost:8080`

### Features:
- Real-time battery status (SOC, production, consumption, grid power)
- Current heating rod status (power, temperatures)
- Manual water heating mode with start/stop button and current heating source display
- Battery priority override (disable until midnight)
- Auto-refresh every 10 seconds

## Configuration

All configuration settings are defined in `src/main/resources/application.properties`.

### Modbus Connection Settings

```properties
# Battery Storage (E3/DC) Modbus TCP Configuration
battery.modbus.host=192.168.1.48
battery.modbus.port=502

# Heating Rod (ELWA2) Modbus TCP Configuration
heatingrod.modbus.host=192.168.1.2
heatingrod.modbus.port=502

# Heating System (Viessmann) Modbus TCP Configuration
gasheating.modbus.host=192.168.1.3
gasheating.modbus.port=502

# Interval at which the external Modbus request is refreshed while gas heating
# is active (the Vitodens falls back to internal control if not refreshed).
# Supports duration expressions: "1m", "30s", "2m30s"
gasheating.keep-alive-interval=20s

# Wallbox (go-eCharger) Modbus TCP Configuration
wallbox.modbus.host=192.168.1.4
wallbox.modbus.port=502
```

### Heating Control Settings

```properties
# Enable/disable automatic heating control
heatingctl.enabled=true

# Minimum surplus power (in watts) required to start heating
# Prevents heating with very small surplus amounts
heatingctl.min-surplus-power=100

# Maximum heating power (in watts)
# Limits heating power even if more surplus is available
heatingctl.max-heating-power=2900

# Battery Priority Configuration
# Master switch to enable/disable battery priority feature completely
heatingctl.battery-priority-enabled=true

# If battery SOC is below this threshold (in %), reserve power for battery charging
heatingctl.battery-priority-threshold=60

# Power (in watts) reserved for battery charging when SOC is below threshold
# This ensures the battery gets charged before using power for heating
heatingctl.battery-reserved-power=1000

# Temperature Hysteresis Configuration
# Temperature difference (in °C) for restart after target is reached
# Prevents frequent on/off cycling when temperature fluctuates near target
# Example: With target 68°C and hysteresis 10°C, heating restarts at 58°C
heatingctl.temperature-hysteresis=10.0

# Percentage to reduce available solar power (safety margin)
# Provides a buffer to avoid grid feed-in fluctuations
# Example: 5 means reduce available power by 5% (2000W → 1900W)
heatingctl.solar-power-reduction-percent=5

# Power Increase Smoothing Configuration
# Time window over which upward heating power adjustments are averaged, to
# smooth out short-lived surplus spikes (e.g. passing clouds). Downward
# adjustments are not affected and take effect immediately on every cycle.
# Supports duration expressions: "150s", "2m30s"
heatingctl.power-increase-smoothing-window=150s

# Minimum power difference (in watts) between the currently applied heating
# power and the newly determined power required to trigger an adjustment.
# Prevents chattering adjustments for minor surplus fluctuations.
heatingctl.min-power-change-threshold=100
```

**Battery Priority Override:**
- Use the web interface to temporarily disable battery priority until midnight
- REST API: `POST /api/battery/priority?disabled=true` (disable) or `?disabled=false` (enable)
- Override automatically resets at midnight
- Useful for days when you know the battery won't reach full charge

### Manual Water Heating Mode

An explicitly started/stopped mode that heats hot water from PV surplus and, if needed, a
strictly limited amount of battery power - without ever drawing from the grid - falling back
to gas heating when no electrical power is available. It replaces the previous free-form
manual power control and suspends the automatic PV-surplus control (above) while active.

```properties
# Heating rod temperature (in °C) below which battery-assisted heating may start
manualwaterheating.heating-rod-low-temperature-threshold=42.0

# Battery SOC (in %) above which battery-assisted heating may start
manualwaterheating.battery-soc-start-threshold=65

# Battery SOC (in %) at or below which battery-assisted heating stops
manualwaterheating.battery-soc-stop-threshold=50

# Maximum SOC drop (in percentage points) from the SOC at session start before
# battery-assisted heating stops, regardless of the absolute stop threshold above
# (e.g. starts at 85%, limit 10 -> stops at 75%, even if still above the absolute floor)
manualwaterheating.max-battery-soc-drop-percent=10

# Maximum power (in watts) the battery may contribute to heating
manualwaterheating.max-battery-heating-power=850

# Maximum total discharge power (in watts) the battery can deliver
manualwaterheating.battery-max-discharge-power=1500

# Gas heating hot water temperature (in °C) below which gas heating is used as a fallback
manualwaterheating.gas-heating-low-temperature-threshold=35.0

# Offset (in °C) below the gas heating target temperature at which gas heating is switched
# off (some hot water is still produced for a while after shutoff due to thermal inertia)
manualwaterheating.gas-heating-shutoff-temperature-offset=5.0
```

**How It Works:**
1. **PV surplus first**: If PV surplus is available, it is used fully for heating (source: `PV`) - not limited to the 850W battery cap.
2. **Battery assist**: If PV surplus alone isn't enough, and the heating rod temperature drops below 42°C while battery SOC is above 65%, additional battery power is used (source: `BATTERY`), capped at 850W and at whatever headroom remains within the battery's 1500W total discharge capacity (accounting for power already used by the house). Once triggered, battery assist keeps running until the rod's target temperature is reached, SOC drops to the absolute 50% floor, or SOC has dropped by 10 percentage points since the session started (whichever comes first) - even if the temperature briefly rises back above 42°C.
3. **Grid protection**: If neither PV surplus nor battery headroom is available, no electrical heating happens at all - the heating rod is never powered from the grid.
4. **Gas fallback**: If no electrical power is available and the gas heating hot water temperature drops below 35°C, gas heating is activated (source: `GAS`) until it reaches its target temperature minus the configured shutoff offset (default 5°C).
5. **Automatic shutoff**: As soon as one of the defined targets is reached - the heating rod's target temperature, or (while gas heating was actively running) the gas heating target - manual water heating mode switches itself off entirely, resuming automatic PV-surplus control.

**Usage:**
- Start/stop via the web interface button, or REST API: `POST /api/manual-water-heating/start` / `POST /api/manual-water-heating/stop`
- Current status: `GET /api/manual-water-heating/status` (active state and current heating source)
- The heating source is only shown on the dashboard while manual water heating mode is active
- The mode stops itself automatically once a target is reached (see point 5 above) - it does not need to be stopped manually every time

### Seasonal Operating Hours

The application automatically adjusts heating control schedules based on the season and daylight hours. This ensures optimal solar surplus utilization and prevents unnecessary operation during low-production periods.

```properties
# Winter Schedule (November - February): 09:00 - 16:00
heatingctl.winter-enabled=true
heatingctl.winter-cron=*/40 * 9-15 * 11,12,1,2 ?

# Spring Schedule (March - April): 08:00 - 18:00
heatingctl.spring-enabled=true
heatingctl.spring-cron=*/40 * 8-17 * 3,4 ?

# Autumn Schedule (September - October): 08:00 - 18:00
heatingctl.autumn-enabled=true
heatingctl.autumn-cron=*/40 * 8-17 * 9,10 ?

# Summer Schedule (May - August): 07:00 - 20:00
heatingctl.summer-enabled=true
heatingctl.summer-cron=*/40 * 7-19 * 5-8 ?
```

**How It Works:**
- Each season has its own scheduler with dedicated operating hours
- Scheduler runs **only** during configured months and hours
- Default: Every 40 seconds during operating hours
- Outside operating hours: **No scheduler activity** (saves resources)

**Seasonal Defaults:**
| Season | Months | Operating Hours | Interval |
|--------|--------|----------------|----------|
| Winter | Nov-Feb | 09:00-16:00 | 40 sec |
| Spring | Mar-Apr | 08:00-18:00 | 40 sec |
| Autumn | Sep-Oct | 08:00-18:00 | 40 sec |
| Summer | May-Aug | 07:00-20:00 | 40 sec |

**Customization Examples:**

*Extend winter hours to 17:00:*
```properties
heatingctl.winter-cron=*/40 * 9-16 * 11,12,1,2 ?
```

*Change summer interval to 30 seconds:*
```properties
heatingctl.summer-cron=*/30 * 7-19 * 5-8 ?
```

*Disable spring schedule entirely:*
```properties
heatingctl.spring-enabled=false
```

*Run only at full minutes (instead of every 40 sec):*
```properties
heatingctl.winter-cron=0 * 9-15 * 11,12,1,2 ?
```

**Benefits:**
- ✅ Optimized for seasonal solar production patterns
- ✅ Reduced system load outside peak solar hours
- ✅ No unnecessary Modbus communication at night/early morning
- ✅ Fully configurable via properties file
- ✅ Individual seasons can be enabled/disabled

### Quarkus Scheduler Settings

```properties
# Enable Quarkus scheduler
quarkus.scheduler.enabled=true

# Scheduler start mode (normal, forced, halted)
quarkus.scheduler.start-mode=normal
```

## Building and Running

### Prerequisites

- Java 21 or later
- Gradle 9.1+ (included via Gradle Wrapper)

### Build Commands

**Clean build:**
```bash
./gradlew clean build
```

**Build without tests:**
```bash
./gradlew clean build -x test
```

**Run tests only:**
```bash
./gradlew test
```

### Running the Application

**Development mode (with live reload):**
```bash
./gradlew quarkusDev
```

**Production mode:**
```bash
./gradlew quarkusRun
```

**Build and run as standalone JAR:**
```bash
./gradlew clean build
java -jar build/quarkus-app/quarkus-run.jar
```

**Build and push to GitHub repository:**
```bash
./gradlew build \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry=ghcr.io \
  -Dquarkus.container-image.group=markuslohn \
  -Dquarkus.container-image.name=hot-water-control \
  -Dquarkus.container-image.tag=1.0.0 \
  -Dquarkus.container-image.username=markuslohn \
  -Dquarkus.container-image.password=XXXX
```

### Development Tools

**Quarkus Dev UI:**
Access at `http://localhost:8080/q/dev-ui` (only available in dev mode)

**Health Checks:**
Access at `http://localhost:8080/q/health`

**API Endpoints:**
- Battery Status: `GET http://localhost:8080/api/battery/status`
- Battery Priority Status: `GET http://localhost:8080/api/battery/priority`
- Battery Priority Override: `POST http://localhost:8080/api/battery/priority?disabled=<true/false>`
- Heating Status: `GET http://localhost:8080/api/heatingrod/status`
- Manual Water Heating Status: `GET http://localhost:8080/api/manual-water-heating/status`
- Manual Water Heating Start/Stop: `POST http://localhost:8080/api/manual-water-heating/start` / `POST http://localhost:8080/api/manual-water-heating/stop`

## Architecture

### Key Components

- **HeatingControlService**: Seasonal schedulers that check and adjust heating based on time of year
- **HeatingRodService**: Interface to the ELWA2 heating rod (Modbus communication)
- **BatteryStorageService**: Interface to the E3/DC battery storage (Modbus communication)
- **REST Resources**: Web API for monitoring and manual control
- **Web UI**: Responsive single-page application for user interface
- **Seasonal Predicates**: Control which seasons are active (WinterDisabledPredicate, etc.)

### Control Logic

1. **Seasonal Scheduling** (resource optimization):
   - Four independent schedulers (Winter, Spring, Autumn, Summer)
   - Each runs **only** during configured months and hours:
     - Winter (Nov-Feb): Default 09:00-16:00
     - Spring (Mar-Apr): Default 08:00-18:00
     - Autumn (Sep-Oct): Default 08:00-18:00
     - Summer (May-Aug): Default 07:00-20:00
   - Outside operating hours: **No activity** (saves CPU, network, power)
   - Fully configurable via Cron expressions
   - Individual seasons can be enabled/disabled

2. **On each schedule trigger**, the application checks:
   - Current battery state and available solar surplus
   - Current and target temperatures of the heating rod
   - Whether heating should be active

3. **Battery Priority** (optimization feature):
   - If battery priority is enabled (config + not overridden):
     - If battery SOC < configured threshold (default: 60%)
       - Reserves power for battery charging (default: 1000W)
       - Only surplus above reserved power is available for heating
     - If battery SOC ≥ threshold
       - All surplus power is available for heating
   - Runtime override available via web UI or REST API
     - Temporarily disables battery priority until midnight
     - Auto-resets at 00:00

4. **Temperature Hysteresis** (prevents on/off cycling):
   - When target temperature is reached (e.g., 68°C):
     - Heating stops and enters "cooling mode"
     - Heating remains off even if surplus is available
   - Heating only restarts when temperature drops below (target - hysteresis):
     - Example: With 10°C hysteresis, restarts at 58°C
     - Prevents frequent on/off cycling near target temperature
   - Configurable hysteresis value (default: 10°C)

5. **Surplus Power Calculation**:
   - When battery priority is **ENABLED**:
     - Heating uses: Production - Consumption - Battery Charging
     - If battery charging: Reserved power is subtracted
   - When battery priority is **DISABLED**:
     - Heating uses: Production - Consumption
     - Battery charging power is added back (heating has priority)

6. **Decision making**:
   - If in cooling mode and temperature > restart threshold → keep heating off
   - If surplus power < minimum threshold → stop heating
   - If surplus power available → adjust heating power to match surplus (up to maximum)

7. **Power adjustment** (smoothed):
   - Automatically accounts for current heating power in surplus calculation
   - Respects configured maximum power limit
   - **Downward adjustments** (surplus decreasing) are applied immediately on every 40-second cycle
   - **Upward adjustments** (surplus increasing) are smoothed: the average surplus over a configurable time window (default: 150 seconds) is used instead of the instantaneous reading, avoiding overreaction to short-lived spikes (e.g. passing clouds)
   - An adjustment is only sent if it differs from the currently applied power by at least a configurable threshold (default: 100W), suppressing minor fluctuations
   - Prioritizes battery charging when SOC is low
   - Sends commands via Modbus to ELWA2

## Java 25 Compatibility

This project includes special configuration for Java 24+ compatibility:

- JVM options in `gradle.properties` for Gradle daemon
- JVM options in `build.gradle` for application runtime
- Resolves module access restrictions in newer Java versions

## Technology Stack

- **Quarkus 3.28.4** - Supersonic Subatomic Java Framework
- **Java 21** - Language level
- **Modbus TCP 2.1.3** - Industrial protocol communication
- **Lombok** - Boilerplate reduction
- **Jackson** - JSON serialization
- **Hibernate Validator** - Validation framework
- **RESTEasy Reactive** - Reactive REST endpoints

## License

This project is licensed under the terms specified in the project license file.

## Contributing

Contributions are welcome! Please ensure all tests pass before submitting pull requests.

---

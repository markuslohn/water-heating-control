# AI Agent Instructions for water-heating-control

This document provides essential context for AI coding agents working in this codebase.

## Project Overview

Ziel
Ich möchte eine Applikation entwickeln mit der ich die Warmwasseraufbereitung in Verbindung mit meiner Heizung, Heizstab und Solaranlage mit Batteriespeicher effizient steuern kann. Ziel ist es möglichst wenig Gas zu verbrauchen und damit Geld einzusparen und natürlich auch die Umwelt zu entlasten. 

Ich bin ein sehr erfahrener Enterprise Architekt und Software Engineer und habe langjährige Erfahrung in der Entwicklung von Software. Ich möchte meine Ideen und Anforderungen aufnehmen und dann die Software weitestgehend automatisch erzeugen und testen lassen.

- Als Heizung kommt Viesmann Vitodens 300-W zum Einsatz. Die Heizung ist mit einem Kommunikationsmodul verbunden
- Als Heistab kommt der AC ELWA 2 zum Einsatz
- Als Batteriespeicher kommt von E3/DC zum Einsatz

This is a Java-based water heating control system built with:
- Quarkus framework for REST APIs and health checks
- Modbus TCP client for industrial control communication
- Java 21 as the target platform
- Gradle for build automation
- Google Java Format for code style

## Key Architecture Points

### Stack Components
- REST API endpoints (see `GreetingResource.java`)
- Health monitoring (see `MyLivenessCheck.java`)
- Modbus TCP communication layer for industrial control
- Docker containers for various deployment scenarios (see `src/main/docker/`)

### Development Workflow

#### Building and Testing
```bash
# Development build
./gradlew build

# Run tests
./gradlew test

# Run the application in dev mode
./gradlew quarkusDev
```

#### Code Style
- The project uses Google Java Format (AOSP style)
- Auto-formatting is enforced via Spotless Gradle plugin
- To format code:
```bash
./gradlew spotlessApply
```

### Docker Support
Multiple Dockerfile variants are available in `src/main/docker/`:
- `Dockerfile.jvm` - Standard JVM mode
- `Dockerfile.native` - Native compilation
- `Dockerfile.native-micro` - Minimal native image
- `Dockerfile.legacy-jar` - Legacy JAR deployment

## Project Conventions

### Package Structure
- Main code: `de.bimalo.homeauto`
- Tests: Parallel structure under `src/test`
- Native tests: Separate directory under `src/native-test`
- Structure based on Boundary Control Entity Architecture

### Testing Patterns
- Unit tests end with `Test.java`
- Integration tests end with `IT.java`
- Tests use JUnit 5 and REST-assured for API testing

## Integration Points
- External REST APIs exposed via Quarkus REST
- Modbus TCP communication for industrial control systems
- Health checks for monitoring and orchestration

## Development Tips
1. Always run Spotless formatting before committing:
   ```bash
   ./gradlew spotlessApply
   ```
2. Use the Quarkus dev mode for rapid development:
   ```bash
   ./gradlew quarkusDev
   ```
3. Docker builds are available for various deployment scenarios
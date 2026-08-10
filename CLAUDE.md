# **CLAUDE.md**

## **Project Overview**

This project manages domestic hot water generation based primarily on the photovoltaic (PV) power surplus.

The primary objective is to maximize the use of locally generated PV surplus before using other energy sources.

The following devices are currently involved:

- Battery storage system
- Electric heating element
- Gas heating system

Physical devices are primarily accessed using Modbus TCP.

Do not introduce assumptions about control priorities, thresholds, polling intervals, or device behavior unless they are explicitly defined in the existing code or requirements.

------

## **Language**

- Use English for all source code.
- Use English names for classes, methods, variables, packages, tests, and technical identifiers.
- Use meaningful domain-specific names.
- Avoid generic names such as `process`, `handle`, `doWork`, or `calculate` when a more precise domain-specific name is available.

------

## **Technology Stack**

- Java 21
- Quarkus

Do not introduce additional frameworks or libraries unless they provide a clear benefit and are required by the task.

------

## **Coding Guidelines**

Follow the Google Java Style Guide:

https://google.github.io/styleguide/javaguide.html

Additionally:

- Prefer simple and explicit implementations.
- Prefer immutable objects where appropriate.
- Use constructor injection.
- Avoid static mutable state.
- Keep methods focused on one responsibility.
- Prefer composition over inheritance.
- Avoid premature abstractions.
- Do not introduce configurability that is not required.

------

## **Architecture**

The project follows the Entity-Control-Boundary (ECB) architecture.

### **Entity**

Entities represent the domain model.

Responsibilities include:

- Domain objects
- Value objects
- Domain state
- Domain-specific concepts

Rules:

- Entities must not depend on Quarkus or other frameworks.
- Entities must not contain infrastructure concerns.
- Entities must not access devices or Modbus directly.
- Prefer explicit domain types over primitive values.

### **Control**

Controls contain application and control logic.

Responsibilities include:

- PV surplus calculation
- Heating decisions
- Coordination between devices
- Control strategies
- Application workflows

Rules:

- Controls must not access Modbus or hardware directly.
- Device interaction must happen through Boundaries.
- Keep control logic testable independently of physical devices.

### **Boundary**

Boundaries provide access to external systems and physical devices.

Responsibilities include:

- Native device communication
- Modbus TCP access
- Reading and writing Modbus registers
- Mapping protocol-specific data to domain objects

Rules:

- Boundaries must not contain business or control logic.
- Protocol-specific details must remain inside the Boundary layer whenever possible.

------

## **Domain Types and Units**

Avoid primitive numeric values without explicit domain meaning.

Do not use raw `double` values for quantities such as:

- Power
- Energy
- Temperature
- Voltage
- Current

The project already contains domain-specific classes for quantities such as temperature and power.

Reuse existing domain types where they adequately model the concept.

If existing types are insufficient or poorly designed, they may be replaced or improved as part of an explicitly justified change.

Always preserve the physical unit and semantic meaning of a value in the type system whenever practical.

Examples of preferred concepts include:

- `Power`
- `Energy`
- `Temperature`

Avoid APIs such as:

```java
void setPower(double value)
```

Prefer APIs that express domain meaning:

```java
void setPower(Power power)
```

------

## **Time Handling**

Use the `java.time` API exclusively for dates and times.

Rules:

- Represent absolute timestamps using `Instant`.
- Use `ZonedDateTime` when timezone-aware calendar representation is required.
- Use `LocalDateTime`, `LocalDate`, or `LocalTime` only when timezone-independent local calendar concepts are explicitly part of the domain.
- Do not represent timestamps as primitive numbers or strings.
- Convert timestamps to a local timezone only at system boundaries or where local time is explicitly relevant.

Unless explicitly required otherwise, use UTC as the internal representation for absolute timestamps.

------

## **Energy Control Strategy**

PV surplus has priority.

The system should preferably use available photovoltaic surplus for domestic hot water generation before relying on other energy sources.

Do not infer additional priorities or control strategies.

In particular, do not make assumptions about:

- Battery charging priorities
- Minimum battery state of charge
- Gas heating priorities
- Heating thresholds
- Switching hysteresis
- Minimum operating times
- Device-specific priorities

These rules will be defined incrementally as the project evolves.

------

## **Modbus Communication**

Device communication is primarily performed using Modbus TCP.

Keep Modbus-specific implementation details inside Boundary components.

Do not expose raw Modbus registers, addresses, or protocol-specific representations to Controls or Entities unless explicitly justified.

### **Polling**

Do not assume a fixed polling strategy or polling interval.

Polling behavior will be defined during implementation based on the requirements of individual devices and use cases.

------

## **Failure Handling**

External devices must be treated as unreliable dependencies.

Failures may include:

- Modbus timeouts
- Connection failures
- Invalid device responses
- Unavailable devices
- Invalid register values
- Unexpected device states

Use appropriate resilience mechanisms where required.

Circuit breakers may be used to prevent repeated access to unavailable or failing devices.

Do not silently ignore communication failures.

Failure behavior must be explicit and testable.

Prefer safe and predictable behavior when external device state is unknown.

------

## **Logging**

Logging must make important system behavior understandable without producing unnecessary noise.

### **ERROR**

Use `ERROR` for:

- Exceptions that prevent an operation from completing
- Unexpected failures
- Situations requiring operator attention

Include sufficient context to identify the affected device, operation, or subsystem.

### **INFO**

Use `INFO` for important lifecycle and control events that help understand and trace system behavior.

Examples include:

- Important control decisions
- Changes in operating state
- Activation or deactivation of heating components
- Relevant device state transitions
- Application startup and shutdown events

Do not log every polling operation or every unchanged measurement at `INFO`.

Use lower logging levels for detailed diagnostic information where appropriate.

------

## **Concurrency and Execution Model**

Do not assume whether a component should use blocking or reactive programming.

The execution model will be decided when the concrete requirements are known.

Do not introduce reactive programming only because Quarkus supports it.

Prefer the simplest execution model that satisfies the requirements.

------

## **Testing Strategy**

Testing should focus strongly on failure behavior and control decisions.

Unit tests are preferred for domain and control logic.

### **Device Communication**

Modbus communication must be mocked or replaced by test doubles in unit tests.

Unit tests must not require physical Modbus devices.

Boundary implementations should be designed so that Controls can be tested independently from Modbus infrastructure.

### **Failure Scenarios**

Prioritize tests for scenarios such as:

- Device unavailable
- Modbus timeout
- Communication failure
- Invalid device values
- Unexpected device states
- Partial system failure
- Invalid configuration

Tests should verify that the system behaves predictably and safely when dependencies fail.

------

## **Implementation Principles**

Before implementing a change:

- Understand the affected domain behavior.
- State assumptions explicitly when requirements are ambiguous.
- Do not invent unspecified control rules.
- Prefer the simplest implementation satisfying the requirement.
- Modify only code relevant to the requested change.
- Do not refactor unrelated code.
- Preserve the existing architecture unless a change is explicitly required.
- Ensure every changed line can be traced back to the requested task.

When several valid implementations exist, prefer the one with:

1. The clearest domain model.
2. The lowest unnecessary complexity.
3. The strongest testability.
4. The least coupling to infrastructure.


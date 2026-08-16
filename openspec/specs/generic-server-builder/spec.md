## Purpose

Provide a protocol-agnostic server builder API for private or unknown protocols so users can build TCP and UDP servers through transport, pipeline, and dispatch concepts without assembling low-level customizer chains directly.

## Requirements

### Requirement: Protocol-agnostic TCP server construction

The system SHALL provide a protocol-agnostic TCP server builder entry that allows users to configure server identity, bind address, channel pipeline setup, dispatcher setup, and low-level server customization.

The TCP builder SHALL build an `XtreamServer` compatible server instance without requiring users to invoke the existing low-level TCP server builder directly.

#### Scenario: Build TCP server with bind, pipeline, and dispatch
- **WHEN** a user configures a TCP server name, bind address, pipeline customizer, and dispatcher customizer through the generic TCP builder
- **THEN** the builder returns a TCP server instance that applies those customizations when started

#### Scenario: Add low-level TCP customization
- **WHEN** a user adds a low-level TCP server customization through the generic TCP builder
- **THEN** the customization is applied along with the generic bind, pipeline, and dispatch configuration

### Requirement: Protocol-agnostic UDP server construction

The system SHALL provide a protocol-agnostic UDP server builder entry that allows users to configure server identity, bind address, channel pipeline setup, dispatcher setup, and low-level server customization.

The UDP builder SHALL build an `XtreamServer` compatible server instance without requiring users to invoke the existing low-level UDP server builder directly.

#### Scenario: Build UDP server with bind, pipeline, and dispatch
- **WHEN** a user configures a UDP server name, bind address, pipeline customizer, and dispatcher customizer through the generic UDP builder
- **THEN** the builder returns a UDP server instance that applies those customizations when started

#### Scenario: Add low-level UDP customization
- **WHEN** a user adds a low-level UDP server customization through the generic UDP builder
- **THEN** the customization is applied along with the generic bind, pipeline, and dispatch configuration

### Requirement: Dispatcher setup shortcut

The generic server builders SHALL provide a dispatcher setup API that allows users to configure handler mappings, handler adapters, result handlers, filters, exception handlers, session manager, and builtin handler support from one nested builder.

`enableBuiltinHandlers(EntityCodec)` SHALL configure both builtin handler adapters and builtin result handlers for the provided entity codec.

#### Scenario: Configure builtin dispatch support
- **WHEN** a user calls `enableBuiltinHandlers(EntityCodec)` in the dispatcher builder
- **THEN** the server dispatch layer uses builtin handler adapters and builtin result handlers for that codec

#### Scenario: Configure custom dispatch components
- **WHEN** a user configures handler mappings, filters, exception handlers, or session manager through the dispatcher setup API
- **THEN** those components are used by the server dispatch layer

#### Scenario: Configure dispatch at most once
- **WHEN** a user calls `dispatch(...)` a second time on the same TCP or UDP server builder
- **THEN** the builder SHALL reject the call with an `IllegalStateException`
- **THEN** the builder SHALL retain the dispatcher configured by the first call

### Requirement: Existing low-level builder compatibility

The system SHALL retain the existing low-level TCP and UDP server builder APIs for advanced customization and compatibility.

The generic server builders SHALL be additive and MUST NOT require existing code that uses the low-level builder APIs to change.

#### Scenario: Existing low-level builder usage remains valid
- **WHEN** existing user code constructs a TCP or UDP server with the low-level server builder API
- **THEN** that code continues to compile and behave as before

### Requirement: Core API remains protocol-neutral

The generic server builder API SHALL remain independent of concrete extension protocols such as JT/T 808 and JT/T 1078.

Protocol-specific builder APIs SHALL NOT be exposed from the core generic server entry point.

Protocol-specific server builders SHALL be exposed from their owning extension modules when provided.

#### Scenario: Core entry points do not expose extension protocol builders
- **WHEN** a user inspects the generic server builder entry points in the core server module
- **THEN** the entry points expose only protocol-agnostic TCP and UDP construction capabilities

#### Scenario: Protocol builders live in extension modules
- **WHEN** a user needs a JT/T 808 or JT/T 1078 protocol-specific server builder
- **THEN** the builder is available from the corresponding JT extension module rather than from the core generic server entry point

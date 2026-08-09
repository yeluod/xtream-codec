## Purpose

Provide JT/T 808 protocol-specific server builder APIs so users and auto-configuration can construct instruction and attachment servers through JT/T 808 concepts instead of low-level transport customizer chains.

## Requirements

### Requirement: JT/T 808 instruction server construction

The JT/T 808 module SHALL provide protocol-specific builder entries for instruction TCP and UDP servers.

The instruction server builders SHALL apply JT/T 808 instruction-server defaults, including bind address, protocol handler adapter, session management, request lifecycle handling, packet framing or datagram splitting, loop resources, and user-provided transport customizers.

#### Scenario: Build JT/T 808 instruction TCP server
- **WHEN** a user or auto-configuration constructs a JT/T 808 instruction TCP server through the protocol-specific builder
- **THEN** the resulting server applies the instruction TCP handler, delimiter-based instruction framing, idle-session handling, configured loop resources, and user transport customizers

#### Scenario: Build JT/T 808 instruction UDP server
- **WHEN** a user or auto-configuration constructs a JT/T 808 instruction UDP server through the protocol-specific builder
- **THEN** the resulting server applies the instruction UDP handler, datagram splitting support, configured loop resources, and user transport customizers

### Requirement: JT/T 808 attachment server construction

The JT/T 808 module SHALL provide protocol-specific builder entries for attachment TCP and UDP servers.

The attachment server builders SHALL apply JT/T 808 attachment-server defaults, including bind address, attachment protocol handler adapter, attachment packet handling, loop resources, and user-provided transport customizers.

#### Scenario: Build JT/T 808 attachment TCP server
- **WHEN** a user or auto-configuration constructs a JT/T 808 attachment TCP server through the protocol-specific builder
- **THEN** the resulting server applies the attachment TCP handler, attachment packet handling defaults, configured loop resources, and user transport customizers

#### Scenario: Build JT/T 808 attachment UDP server
- **WHEN** a user or auto-configuration constructs a JT/T 808 attachment UDP server through the protocol-specific builder
- **THEN** the resulting server applies the attachment UDP handler, attachment packet handling defaults, configured loop resources, and user transport customizers

### Requirement: JT/T 808 builder compatibility

The JT/T 808 protocol builders SHALL be additive and MUST NOT remove or break existing low-level server builder usage, handler adapter builders, bean names, conditional auto-configuration behavior, or user customizer extension points.

#### Scenario: Existing JT/T 808 auto-configuration remains behavior-compatible
- **WHEN** existing applications start with the same JT/T 808 server properties and beans
- **THEN** the created servers keep the same enabled protocols, bind settings, bean names, protocol handlers, pipeline behavior, loop resources, and customizer behavior as before

#### Scenario: Existing low-level JT/T 808 customization remains available
- **WHEN** existing code directly uses low-level JT/T 808 handler adapter builders or generic server builders
- **THEN** that code continues to compile and can still be used for advanced customization

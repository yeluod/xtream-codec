## Purpose

Provide JT/T 1078 protocol-specific server builder APIs so users and auto-configuration can construct audio/video TCP and UDP servers through JT/T 1078 concepts instead of low-level transport customizer chains.

## Requirements

### Requirement: JT/T 1078 TCP server construction

The JT/T 1078 module SHALL provide a protocol-specific builder entry for TCP audio/video servers.

The TCP builder SHALL apply JT/T 1078 TCP defaults, including bind address, protocol handler adapter, frame decoding, request decoding, SIM conversion, session management, heartbeat or idle-session handling, scheduler usage, loop resources, and user-provided transport customizers.

#### Scenario: Build JT/T 1078 TCP server
- **WHEN** a user or auto-configuration constructs a JT/T 1078 TCP server through the protocol-specific builder
- **THEN** the resulting server applies the TCP audio/video handler, TCP frame decoder, request decoder, idle-session handling, configured loop resources, and user transport customizers

#### Scenario: Preserve JT/T 1078 TCP media handling behavior
- **WHEN** TCP audio/video packets arrive at a server created by the protocol-specific builder
- **THEN** the packets are decoded and dispatched through the same JT/T 1078 session, scheduler, and request publishing behavior as the existing auto-configured TCP server

### Requirement: JT/T 1078 UDP server construction

The JT/T 1078 module SHALL provide a protocol-specific builder entry for UDP audio/video servers.

The UDP builder SHALL apply JT/T 1078 UDP defaults, including bind address, protocol handler adapter, SIM conversion, session management, scheduler usage, loop resources, and user-provided transport customizers.

#### Scenario: Build JT/T 1078 UDP server
- **WHEN** a user or auto-configuration constructs a JT/T 1078 UDP server through the protocol-specific builder
- **THEN** the resulting server applies the UDP audio/video handler, configured loop resources, and user transport customizers

#### Scenario: Preserve JT/T 1078 UDP media handling behavior
- **WHEN** UDP audio/video datagrams arrive at a server created by the protocol-specific builder
- **THEN** the datagrams are decoded and dispatched through the same JT/T 1078 session, scheduler, SIM conversion, and request publishing behavior as the existing auto-configured UDP server

### Requirement: JT/T 1078 builder compatibility

The JT/T 1078 protocol builders SHALL be additive and MUST NOT remove or break existing low-level server builder usage, protocol handler adapters, bean names, conditional auto-configuration behavior, or user customizer extension points.

#### Scenario: Existing JT/T 1078 auto-configuration remains behavior-compatible
- **WHEN** existing applications start with the same JT/T 1078 server properties and beans
- **THEN** the created servers keep the same enabled protocols, bind settings, bean names, protocol handlers, pipeline behavior, loop resources, and customizer behavior as before

#### Scenario: Existing low-level JT/T 1078 customization remains available
- **WHEN** existing code directly uses low-level protocol handlers or generic server builders
- **THEN** that code continues to compile and can still be used for advanced customization

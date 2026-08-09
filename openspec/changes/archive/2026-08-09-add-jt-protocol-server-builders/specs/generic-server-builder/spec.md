## MODIFIED Requirements

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

## Why

Unknown/private protocol users currently have to assemble servers through low-level Reactor Netty customizers, `XtreamServerBuilder`, and `XtreamNettyHandlerAdapter` builders. This makes the common "start a TCP/UDP server, configure framing, and dispatch requests" path harder than it needs to be.

## What Changes

- Add a protocol-agnostic Generic Server Builder API in `xtream-codec-server-reactive`.
- Provide concise TCP and UDP entry points for private protocols, scoped to server name, bind address, pipeline setup, dispatcher setup, and low-level customizers.
- Keep `XtreamServerBuilder` as the low-level advanced customization API rather than removing it.
- Keep JT/T 808 and JT/T 1078 protocol-specific APIs out of the core generic entry point; those may be introduced later in their own extension modules.
- Update custom/private protocol samples to demonstrate the generic API once implemented.

## Capabilities

### New Capabilities
- `generic-server-builder`: Protocol-agnostic TCP/UDP server builder API for private or unknown protocols.

### Modified Capabilities
- `server-quickstart-module`: The custom protocol quick-start shall demonstrate the new generic builder API instead of the low-level server builder entry.

## Impact

- Affects public API in `xtream-codec-server-reactive`.
- Affects custom/private protocol quick-start and related documentation examples.
- Does not introduce new runtime dependencies.
- Does not remove or break existing `XtreamServerBuilder` usage.

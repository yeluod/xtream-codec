## 1. Core Generic API

- [x] 1.1 Add protocol-neutral `XtreamServers` entry point in `xtream-codec-server-reactive`
- [x] 1.2 Add generic TCP server builder with `name`, `bind`, `pipeline`, `dispatch`, `customize`, and `build` operations
- [x] 1.3 Add generic UDP server builder with matching public vocabulary and UDP-specific implementation details
- [x] 1.4 Implement dispatcher builder that wraps existing TCP/UDP handler adapter builders
- [x] 1.5 Ensure `enableBuiltinHandlers(EntityCodec)` enables builtin handler adapters and builtin result handlers together
- [x] 1.6 Keep existing `XtreamServerBuilder`, `TcpXtreamServerBuilder`, and `UdpXtreamServerBuilder` behavior unchanged

## 2. Tests

- [x] 2.1 Add unit tests for TCP generic builder delegation and customization ordering
- [x] 2.2 Add unit tests for UDP generic builder delegation and customization ordering
- [x] 2.3 Add tests for dispatcher builder builtin handlers, custom handler mappings, filters, exception handlers, and session manager configuration
- [x] 2.4 Add compatibility checks showing existing low-level builder usage still compiles and behaves as before

## 3. Samples and Documentation

- [x] 3.1 Update custom annotation server quick-start to use the Generic TCP Server Builder as the primary entry
- [x] 3.2 Update docs or source snippets that describe the custom protocol server entry point
- [x] 3.3 Keep JT/T 808 and JT/T 1078 extension auto-configuration on existing builders unless a local simplification is clearly needed
- [x] 3.4 Add documentation note that `XtreamServerBuilder` remains the low-level advanced customization API

## 4. Verification

- [x] 4.1 Run `./gradlew :xtream-codec-server-reactive:test`
- [x] 4.2 Run `./gradlew :quick-start:custom-annotation-server:build`
- [x] 4.3 Run docs build if referenced source snippets changed
- [x] 4.4 Run `openspec validate add-generic-server-builder-api --strict`
- [x] 4.5 Run `git diff --check`

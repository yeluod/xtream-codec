## 1. Trace Model

- [x] 1.1 Define the core trace model for operation direction, root metadata, trace nodes, byte ranges, status, attributes, and diagnostics.
- [x] 1.2 Define a visitor abstraction or traversal method that visits trace nodes in deterministic depth-first order.
- [x] 1.3 Define a JSON-friendly trace view model in core for payload hex, tree nodes, byte-range indexes, and diagnostics.
- [x] 1.4 Add value rendering utilities for byte arrays, binary wrapper values, and large values used by trace output.

## 2. CodecTracker Migration

- [x] 2.1 Refactor `CodecTracker` to delegate trace collection to the new recorder while preserving existing public construction and trace-node visit usage.
- [x] 2.2 Preserve the existing tracker-enabled public API surface of `FieldCodec`, `EntityCodec`, `EntityEncoder`, and `EntityDecoder`.
- [x] 2.3 Remove legacy `RootSpan`/Span classes and migrate backend consumers to `CodecTrace`/`CodecTraceView`.
- [x] 2.4 Ensure nullable tracker convenience overloads still route null tracker calls to the production encode/decode path.

## 3. Core Encode/Decode Instrumentation

- [x] 3.1 Update default `FieldCodec#serializeWithTracker` and `FieldCodec#deserializeWithTracker` behavior to record value, codec type, field path, hex, and byte range.
- [x] 3.2 Update entity encode/decode tracker entry points to initialize root trace metadata, root byte range, and operation direction.
- [x] 3.3 Migrate nested entity tracking to explicit enter/exit node events.
- [x] 3.4 Migrate collection tracking to explicit field, item index, and byte-range nodes.
- [x] 3.5 Migrate map tracking to explicit field, entry, key/value, and byte-range nodes.
- [x] 3.6 Migrate generated or backfilled length field tracking to identify final length field bytes.
- [x] 3.7 Add failure recording so partial trace data and diagnostics remain available when tracked encode/decode throws.

## 4. Web Debug Integration Surface

- [x] 4.1 Add conversion from core trace to the Web trace view DTO.
- [x] 4.2 Add byte offset to owning-node lookup data for frontend byte selection.
- [x] 4.3 Add selected-node byte range data for frontend field selection.
- [x] 4.4 Migrate JT808 dashboard/debug DTOs to `CodecTraceView`.
- [x] 4.5 Implement the React + HeroUI trace debug page in the dashboard UI and stop using the legacy Vue debug UI.
- [x] 4.6 Ensure the new frontend builds and consumes the `CodecTraceView` data shape.

## 5. Tests and Validation

- [x] 5.1 Add API compatibility tests or compile coverage for existing tracker-enabled public calls.
- [x] 5.2 Add trace structure tests for simple encode and decode.
- [x] 5.3 Add trace hierarchy tests for nested entity, collection, and map fields.
- [x] 5.4 Add byte range tests that assert field ranges map to exact payload slices.
- [x] 5.5 Add generated length field tracking tests.
- [x] 5.6 Add visitor order tests for `CodecTraceNode`.
- [x] 5.7 Add JSON serialization tests for the Web trace view.
- [x] 5.8 Add partial failure diagnostic tests.
- [x] 5.9 Run `./gradlew :xtream-codec-core:test`.
- [x] 5.10 Run affected dashboard/debug module tests if tracker output consumed by those modules changes.
- [x] 5.11 Run new frontend build.
- [x] 5.12 Run `git diff --check`.

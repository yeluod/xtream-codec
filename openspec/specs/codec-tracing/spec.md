## Purpose

提供结构化的编解码调试埋点能力，使实体编解码过程可以被直接访问、序列化展示，并支持网页版报文调试页面进行字段树与报文字节范围的双向定位。

## Requirements

### Requirement: Public tracking API compatibility

The system SHALL preserve the existing public tracking entry points used by applications to run debug encode and decode operations.

- Existing public encode/decode APIs that accept a tracker SHALL remain source compatible.
- Existing public encoder/decoder methods with tracker variants SHALL remain source compatible.
- Existing field codec tracker variants SHALL remain source compatible.
- A null tracker passed through nullable public convenience APIs SHALL continue to use the production encode/decode path.

#### Scenario: Decode with existing tracker argument

- **WHEN** an application calls an existing entity decode overload with a tracker argument
- **THEN** the call SHALL compile and decode the entity through the debug path
- **THEN** the tracker SHALL contain trace data for the completed decode operation

#### Scenario: Encode with null tracker convenience overload

- **WHEN** an application calls an existing entity encode overload whose tracker argument is null
- **THEN** the system SHALL encode through the production path
- **THEN** the system SHALL NOT require trace objects to be created

### Requirement: Structured codec trace

The system SHALL produce a structured trace for debug encode and decode operations.

- The trace SHALL identify the operation direction as encode or decode.
- The trace SHALL expose the root entity type when known.
- The trace SHALL contain ordered trace nodes representing the encoded or decoded structure.
- Each trace node SHALL expose a stable node id, parent relation, node kind, display name, field path when available, Java type when available, codec type when available, value summary when available, byte range, and local hex representation.
- Byte ranges SHALL use offsets relative to the traced input or output buffer segment.

#### Scenario: Decode simple entity

- **WHEN** a simple entity is decoded with tracking enabled
- **THEN** the resulting trace SHALL contain one root node and ordered child nodes for decoded fields
- **THEN** each decoded field node SHALL include the bytes consumed by that field

#### Scenario: Encode simple entity

- **WHEN** a simple entity is encoded with tracking enabled
- **THEN** the resulting trace SHALL contain one root node and ordered child nodes for encoded fields
- **THEN** each encoded field node SHALL include the bytes written for that field

### Requirement: Nested and collection trace hierarchy

The system SHALL represent nested entities, collections, maps, and generated length fields as hierarchical trace nodes.

- Nested entity fields SHALL contain child nodes for their nested fields.
- Collection fields SHALL contain child nodes for encoded or decoded items.
- Map fields SHALL contain child nodes for encoded or decoded entries.
- Generated or backfilled length fields SHALL be identifiable as length-related trace nodes.
- Item index information SHALL be represented separately from byte offsets.

#### Scenario: Decode nested collection

- **WHEN** an entity containing a nested collection field is decoded with tracking enabled
- **THEN** the trace SHALL contain a collection node under the owning field path
- **THEN** each item node SHALL expose its item index and consumed byte range

#### Scenario: Encode generated length field

- **WHEN** an entity containing an automatically backfilled length field is encoded with tracking enabled
- **THEN** the trace SHALL identify the length field node
- **THEN** the trace SHALL expose the final bytes written for the length field

### Requirement: Visit-based direct access

The system SHALL continue to support direct tree traversal of tracked codec data.

- Applications SHALL be able to visit trace nodes in deterministic depth-first order.
- Visitor callbacks SHALL receive the node depth and node data.
- Visiting a trace SHALL NOT require JSON serialization or Web UI components.

#### Scenario: Visit tracked decode

- **WHEN** an application decodes an entity with tracking enabled and calls the visitor API
- **THEN** the visitor SHALL be invoked for the root node and each descendant node
- **THEN** callback order SHALL match the trace tree order

### Requirement: Web debug trace view

The system SHALL expose trace data in a form suitable for a Web-based packet encode/decode debugging page.

- The view SHALL include the complete traced hex payload.
- The view SHALL include trace tree nodes with byte ranges.
- The view SHALL allow a frontend to highlight bytes for a selected node.
- The view SHALL allow a frontend to identify owning nodes for a selected byte offset.
- The view SHALL provide JSON-friendly value representations for byte arrays and binary wrapper values.

#### Scenario: Highlight selected field bytes

- **WHEN** a frontend receives a trace view and a user selects a field node
- **THEN** the frontend SHALL be able to determine the exact byte range to highlight without searching for the node's hex text

#### Scenario: Locate node from selected byte

- **WHEN** a frontend receives a trace view and a user selects a byte offset
- **THEN** the frontend SHALL be able to determine the trace nodes whose byte ranges contain that offset

### Requirement: Error diagnostics

The system SHALL preserve useful trace data when a debug encode or decode operation fails after partially processing a buffer.

- The trace SHALL record diagnostic entries for failures detected during tracked operations.
- A diagnostic SHALL include message information and the best available node or byte location.
- Successfully recorded nodes before the failure SHALL remain visitable and serializable.

#### Scenario: Decode fails after partial field processing

- **WHEN** a tracked decode operation fails after one or more fields have been decoded
- **THEN** the tracker SHALL retain nodes recorded before the failure
- **THEN** the trace SHALL contain a diagnostic describing the failure location when available

### Requirement: Production path isolation

The system SHALL keep production encode/decode operations independent from debug trace collection.

- Production encode/decode calls without a tracker SHALL NOT allocate trace nodes.
- Production encode/decode calls without a tracker SHALL NOT depend on Web debug DTO types.
- Debug trace collection SHALL remain opt-in through existing tracker-enabled entry points or explicitly introduced trace APIs.

#### Scenario: Production decode without tracker

- **WHEN** an application decodes an entity through a public API without a tracker
- **THEN** the entity SHALL decode without creating trace node data
- **THEN** the decode result SHALL remain identical to the equivalent tracked decode result, excluding trace side effects

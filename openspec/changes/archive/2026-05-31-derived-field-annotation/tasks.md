## 1. @DerivedField Core SPI

- [x] 1.1 Create `FieldTransformer<S, T>` interface with `read()` and default `write()` methods
- [x] 1.2 Create `@DerivedField` annotation with `source()` (String), `using()` (Class), `reverseSource()` (boolean, default false), and `desc()` attributes

## 2. BeanMetadata: DerivedBeanPropertyMetadata + Scanner

- [x] 2.1 Add `isDerived()` (default `false`), `reverseSource()` (default `false`), `derivedSource()`, and `derivedTransformer()` methods to `BeanPropertyMetadata` interface
- [x] 2.2 Create `DerivedBeanPropertyMetadata` implementing `BeanPropertyMetadata` with `isDerived() = true`, no-op `decodePropertyValue()`/`encodePropertyValue()`, and cached `FieldTransformer` instance
- [x] 2.3 In bean metadata scanning logic: detect fields annotated only with `@DerivedField` (no `@XtreamField`) and create `DerivedBeanPropertyMetadata`
- [x] 2.4 Handle `@XtreamField` + `@DerivedField` coexisting on same field: log warning, treat as non-derived
- [x] 2.5 Validate `reverseSource` constraint: if multiple fields derive from same source, only one may have `reverseSource=true`; throw at startup if violated

## 3. EntityDecoder: Derived Pass After ByteBuf Loop

- [x] 3.1 In `EntityDecoder.decode()` (both with and without tracker): skip `isDerived()` properties during main ByteBuf iteration
- [x] 3.2 After main loop, add Pass 2: iterate derived properties, source value from `evaluationContext`, call `read()`, set via `property.setProperty()`
- [x] 3.3 Handle `null` source: if source field has null value (e.g. condition failed), skip the derived field
- [x] 3.4 Cyclic dependency check: if source property is itself derived, log warning and skip

## 4. EntityEncoder: Reverse Pass Before ByteBuf Loop

- [x] 4.1 In `EntityEncoder.encode()` (both with and without tracker): before main loop, add Pass 0: iterate only `isDerived() && reverseSource()` properties
- [x] 4.2 For each, get derived value, call `write()`, find source `BeanPropertyMetadata` by name, set the reversed value on source field
- [x] 4.3 Catch `UnsupportedOperationException` from unimplemented `write()` and skip gracefully
- [x] 4.4 In main encode loop: skip `isDerived()` properties (they've already been reverse-written to source)

## 5. Optional Utilities: BitFlag + EnumSetBitTransformer

- [x] 5.1 Create `BitFlag` marker interface in `common.utils` package with `bitOffset()` and default `bitLength()` methods
- [x] 5.2 Create `EnumSetBitTransformer<E extends Enum<E> & BitFlag>` as abstract class in `common.utils`, implementing `FieldTransformer<Long, Set<E>>` with `read()` and `write()` logic

## 6. Usage Example in ext/jt/ (StatusBit + LoadStatus)

- [x] 6.1 Create `StatusBit` enum implementing `BitFlag` with all status bits defined per JT/T 808 表24
- [x] 6.2 Create `StatusBitTransformer` extending `EnumSetBitTransformer<StatusBit>` (3-line subclass bridge)
- [x] 6.3 Create `LoadStatus` enum for bits 8-9 (EMPTY/HALF/RESERVED/FULL) with `from()` factory
- [x] 6.4 Create `LoadStatusTransformer` implementing `FieldTransformer<Long, LoadStatus>` with custom bit-range logic
- [x] 6.5 Add `@DerivedField(source = "status", using = StatusBitTransformer.class, reverseSource = true)` field + getter to `BuiltinMessage0200`
- [x] 6.6 Add `@DerivedField(source = "status", using = LoadStatusTransformer.class, reverseSource = false)` field + getter to `BuiltinMessage0200`

## 7. Tests

- [x] 7.1 Unit test for `EnumSetBitTransformer`: verify `read()` maps single-bit and multi-bit flags correctly, verify `write()` reconstructs the long
- [x] 7.2 Unit test for `StatusBitTransformer` + `LoadStatusTransformer`: decode a `BuiltinMessage0200` hex and verify both derived fields match expected values
- [x] 7.3 Unit test for `@DerivedField` encode: set `statusFlags`, encode, and verify `status` Dword in wire format has correct bits
- [x] 7.4 Unit test for `reverseSource=false`: encode with only read-only derived fields set, verify source field unchanged and wire format correct
- [x] 7.5 Unit test for multiple derived fields from same source: verify decode fills all, encode only reverses the `reverseSource=true` one
- [x] 7.6 Unit test for `reverseSource` conflict detection: verify startup fails when two fields declare `reverseSource=true` for same source

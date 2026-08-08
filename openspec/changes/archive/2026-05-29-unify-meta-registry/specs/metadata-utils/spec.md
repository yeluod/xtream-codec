## ADDED Requirements

### Requirement: MetadataUtils 提供通用 KeyMeta 创建逻辑
`MetadataUtils.createKeyMeta(int targetVersion, Key[] keys)` SHALL 封装从 `Key[]` 注解创建 `KeyMeta` 记录的全部逻辑，包括版本匹配、大小计算、key 编解码器创建、字符集解析。

#### Scenario: 版本匹配成功时返回对应 KeyMeta
- **WHEN** `keys` 数组包含 `version` 与 `targetVersion` 匹配的 `Key` 注解
- **THEN** 返回包含匹配版本的 `KeyMeta`，`sizeInBytes` 从 `key.type().dataType().sizeInBytes()` 取，若 ≤ 0 则回退到 `key.sizeInBytes()`

#### Scenario: 无匹配版本时抛出异常
- **WHEN** `keys` 数组中没有任何 `Key` 的 `version` 与 `targetVersion` 匹配且无 `ALL_VERSION` 兜底
- **THEN** 抛出 `IllegalArgumentException`

### Requirement: MetadataUtils 提供通用 ValueLengthMeta 创建逻辑
`MetadataUtils.createValueLengthMeta(int targetVersion, ValueLength[] valueLengths)` SHALL 封装从 `ValueLength[]` 注解创建 `ValueLengthMeta` 的逻辑。

#### Scenario: 版本匹配成功
- **WHEN** `valueLengths` 包含匹配版本
- **THEN** 返回包含 `version`、`type`(LengthFieldType) 和 `codec`(由 type 派生) 的 `ValueLengthMeta`

#### Scenario: 无匹配版本时抛出异常
- **WHEN** 没有匹配且没有 `ALL_VERSION` 兜底
- **THEN** 抛出 `IllegalArgumentException`

### Requirement: MetadataUtils 提供通用字符集检测
`MetadataUtils.detectCharset(...)` 的多个重载方法 SHALL 封装所有字符集检测逻辑，包括 `ValueMatcher`/`FallbackValueMatcher` 的版本、Encoder/Decoder common param 的 fallback 字符集。

#### Scenario: 基于 XtreamDataType 的 CodecCharset 推断
- **WHEN** `valueType.isPlaceholder()` 为 false 且 `codecCharset` 为 `UTF_8`/`GBK`/`GB_2312`/`BCD_8421`/`HEX`
- **THEN** 返回对应 charsetName
- **WHEN** `codecCharset` 为 `DYNAMIC`
- **THEN** 返回 `firstOr(charset, commonCharset)` 的结果

#### Scenario: 基于 codecClass 推断
- **WHEN** `codecClass` 是 `StringFieldCodecGbk`/`StringFieldCodecGb2312`/`StringFieldCodecUtf8`/`StringFieldCodecBcd8421`/`StringFieldCodecHex`
- **THEN** 返回对应的 charsetName

### Requirement: MetadataUtils 提供通用 Key 值读取
`MetadataUtils.readKey(KeyType keyType, ValueMatcher valueMatcher)` SHALL 根据 `KeyType` 调用对应的 `readI8Key`/`readU8Key`/`readStringKey` 等方法，并从 `ValueMatcher` 中读取匹配值列表。

#### Scenario: 读取 i8 key 值
- **WHEN** `keyType` 为 `i8` 且 `valueMatcher.matchI8()` 非空
- **THEN** 返回去重后的 `List<Byte>`

#### Scenario: 没有匹配值时返回 null
- **WHEN** 对应 `keyType` 的匹配数组为空
- **THEN** 返回 `null`

### Requirement: MetadataUtils 提供通用方法
以下纯工具方法 SHALL 由 `MetadataUtils` 提供：
- `findDuplicateVersions(int[] version)` - 检测版本号数组中的重复项
- `isVersionMatched(int targetVersion, int versionCandidate)` - 判断版本是否匹配
- `firstOr(@Nullable String charset, @Nullable String fallbackCharset)` - 字符串空值回退

#### Scenario: findDuplicateVersions 检测重复
- **WHEN** `version` 数组包含重复数字 `{1, 2, 1}`
- **THEN** 返回 `{1: 2}`
- **WHEN** 无重复
- **THEN** 返回空 Map

### Requirement: SimpleMapMetadataRegistry 使用实例缓存
`SimpleMapMetadataRegistry` SHALL 从全部静态方法 + 静态 `ConcurrentHashMap` 改为实例方法 + 实例 `ConcurrentHashMap` 缓存。

#### Scenario: 构造时注入 BeanMetadataRegistry
- **WHEN** 创建 `new SimpleMapMetadataRegistry(beanMetadataRegistry)`
- **THEN** `getOrCreateMapMetadata()` 方法使用实例缓存而非静态缓存

#### Scenario: 不同实例使用不同缓存
- **WHEN** 两个不同的 `SimpleMapMetadataRegistry` 实例调用 `getOrCreateMapMetadata()`
- **THEN** 各自使用独立的 `ConcurrentHashMap` 缓存

### Requirement: DefaultExtendMetaRegistry 删除重复方法
`DefaultExtendMetaRegistry` SHALL 删除与 `MetadataUtils` 重复的所有方法，改为委托 `MetadataUtils` 完成。

#### Scenario: 删除方法后行为不变
- **WHEN** 调用 `getXtreamTlvFieldSequenceMeta()` 或 `getXtreamPairFieldSequenceMeta()`
- **THEN** 内部通过 `MetadataUtils.createKeyMeta()` / `MetadataUtils.createValueLengthMeta()` 等完成元数据创建
- **AND** 结果与重构前一致

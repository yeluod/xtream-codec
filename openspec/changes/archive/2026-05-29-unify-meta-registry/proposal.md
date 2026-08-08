## Why

`DefaultExtendMetaRegistry` 和 `SimpleMapMetadataRegistry` 在 KeyMeta/ValueLengthMeta 的创建、字符集检测、编解码器创建、Key 值读取等核心逻辑上存在大量重复代码，约 300+ 行完全相同的逻辑分别维护在两个类中。同时 `SimpleMapMetadataRegistry` 使用静态缓存而 `DefaultExtendMetaRegistry` 使用实例缓存，两者策略不一致且未经验证。

## What Changes

- **提取共享工具逻辑**：将 `createKeyMeta()`、`createKeyCodec()`、`parseKeyCharset()`、`createValueLengthMeta()`、`createValueCodec()`、`detectCharset()`、`firstOr()`、`findDuplicateVersions()`、`isVersionMatched()`、`readKey()` 及其所有 `readXxxKey()` 方法和 `checkExclusive()` 抽取到共享工具类中，消除重复
- **统一 `matchVersion()` 实现**：`SimpleMapMetadataRegistry` 私有的 `matchVersion()` 与 `HasVersions.matchVersion()` 逻辑几乎相同，两者合并到 `HasVersions` 工具类
- **统一缓存策略为实例缓存**：`SimpleMapMetadataRegistry` 从全部静态方法 + 静态缓存改为实例方法 + 实例缓存，与 `DefaultExtendMetaRegistry` 保持一致
- **统一模型记录**：`SimpleMapMetadataRegistry` 内部声明的 `KeyMeta`、`ValueLengthMeta`、`ValueMatcherMeta`、`FallbackValueMatcherMeta` 与 `impl/domain/` 包中的记录统一（扩充 domain 包记录以覆盖内联记录的特殊字段）
- **消除 `DefaultExtendMetaRegistry.readKey()` 跨类静态引用**：`SimpleMapMetadataRegistry` 现在直接通过共享工具类调用，不依赖 `DefaultExtendMetaRegistry`

## Capabilities

### New Capabilities
- `metadata-utils`: 共享元数据工具类，封装 Key/ValueLength/ValueMatcher/字符集检测等通用逻辑

### Modified Capabilities
<!-- No existing spec to modify - pure implementation refactoring -->

## Impact

- `DefaultExtendMetaRegistry.java`：删除重复方法，改为委托共享工具类
- `SimpleMapMetadataRegistry.java`：去除静态缓存，改为实例缓存；去除重复方法；改用 domain 包记录；实例化方（`MapFieldCodec`）需注入注册到 `BeanMetadataRegistry`
- `impl/domain/KeyMeta.java`：增加 `resolvedCharset` 字段
- `impl/domain/ValueLengthMeta.java`：增加 `codec` 字段
- `impl/domain/ValueMatcherMeta.java`：保留现有字段（含 `length`/`valueEntity`/`description`）
- `impl/codec/utils/HasVersions.java`：合并 SimpleMap 的 `matchVersion` 逻辑差异
- `MapFieldCodec.java`：从静态 import 改为持有 `SimpleMapMetadataRegistry` 实例
- 无 API 破坏性变更

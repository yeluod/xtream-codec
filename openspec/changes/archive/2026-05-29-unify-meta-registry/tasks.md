## 1. 创建 MetadataUtils 共享工具类

- [ ] 1.1 新建 `MetadataUtils.java`，从 `DefaultExtendMetaRegistry` 抽取 `createKeyCodec()`、`parseKeyCharset()`、`isVersionMatched()`、`findDuplicateVersions()`、`firstOr()`、`formateKey()` 为静态方法
- [ ] 1.2 从 `DefaultExtendMetaRegistry` 抽取 `detectCharset()` 全部重载到 `MetadataUtils`
- [ ] 1.3 从 `DefaultExtendMetaRegistry` 抽取 `readKey()` + `checkExclusive()` + 全部 `readXxxKey()` 方法到 `MetadataUtils`
- [ ] 1.4 在 `MetadataUtils` 中实现 `createKeyMeta(int targetVersion, Key[] keys)` — 提取自 `DefaultExtendMetaRegistry.doCreateKeyMeta()`
- [ ] 1.5 在 `MetadataUtils` 中实现 `createValueCodec()` — 提取自 `DefaultExtendMetaRegistry`（需传递 `BeanMetadataRegistry`）
- [ ] 1.6 在 `MetadataUtils` 中实现 `createValueLengthMeta(int targetVersion, ValueLength[] valueLengths)` — 提取自 `DefaultExtendMetaRegistry.doCreateValueLengthMeta()`

## 2. 统一 matchVersion() 实现

- [ ] 2.1 合并 `SimpleMapMetadataRegistry.matchVersion()` 中独有的 `ALL_VERSION` 处理逻辑到 `HasVersions.matchVersion()`
- [ ] 2.2 删除 `SimpleMapMetadataRegistry` 私有的 `HasVersions`/`HasVersion`/`VersionMatchResult` record 定义，改用 `impl/codec/utils/` 中公共版本

## 3. 统一 Domain 模型记录

- [ ] 3.1 `KeyMeta`（domain 包）增加 `resolvedCharset: Charset` 字段，从 `charset: String` 构造时解析
- [ ] 3.2 `ValueLengthMeta`（domain 包）增加 `codec: FieldCodec<Object>` 字段，从 `LengthFieldType` 派生
- [ ] 3.3 `ValueMatcherMeta`（domain 包）保持现有字段（已涵盖 SimpleMap 所需字段）
- [ ] 3.4 `FallbackValueMatcherMeta`（domain 包）保持现有字段（已涵盖 SimpleMap 所需字段）

## 4. 重构 DefaultExtendMetaRegistry

- [ ] 4.1 `DefaultExtendMetaRegistry` 删除所有已抽取到 `MetadataUtils` 的重复方法
- [ ] 4.2 将 `createKeyMeta()`、`createValueLengthMeta()`、`createValueMatcherMetas()`、`createFallbackValueMatcherMeta()` 等方法委托到 `MetadataUtils`
- [ ] 4.3 验证 `getXtreamTlvFieldSequenceMeta()` 和 `getXtreamPairFieldSequenceMeta()` 行为不变

## 5. 重构 SimpleMapMetadataRegistry 为实例化

- [ ] 5.1 `SimpleMapMetadataRegistry` 添加构造器 `SimpleMapMetadataRegistry(BeanMetadataRegistry beanMetadataRegistry)`
- [ ] 5.2 缓存从 `private static final` 改为实例字段
- [ ] 5.3 删除内部重复的 `KeyMeta`/`ValueLengthMeta`/`ValueMatcherMeta`/`FallbackValueMatcherMeta`/`HasVersions`/`HasVersion`/`VersionMatchResult` record 定义
- [ ] 5.4 导入并使用 domain 包的 record 和 `MetadataUtils`
- [ ] 5.5 删除私有的 `matchVersion()` 方法，改为使用 `HasVersions.matchVersion()`
- [ ] 5.6 调整 `SimpleMapMetadataRegistry` 中的其他重复方法以委托 `MetadataUtils`

## 6. 适配 MapFieldCodec

- [ ] 6.1 将 `MapFieldCodec` 中 `SimpleMapMetadataRegistry` 的静态 import 改为实例字段引用
- [ ] 6.2 `MapFieldCodec` 构造时直接 `new SimpleMapMetadataRegistry(beanMetadataRegistry)` — 不搞单例，`MapFieldCodec` 本身已是单例

## 7. 清理已验证

- [ ] 7.1 运行 `DefaultExtendMetaRegistryTest` 验证行为不变
- [ ] 7.2 运行 `./gradlew :xtream-codec-core:test` 验证全部测试通过
- [ ] 7.3 检查 LSP diagnostics 无新增错误

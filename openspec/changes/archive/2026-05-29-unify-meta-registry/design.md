## Context

当前代码库中存在两个元数据注册表：

1. **`DefaultExtendMetaRegistry`**（实现 `ExtendMetaRegistry` 接口）
   - 负责 `@XtreamTLVFieldSequence` 和 `@XtreamPairFieldSequence` 注解的元数据解析
   - 实例级 `ConcurrentHashMap` 缓存
   - `BeanMetadataRegistry` 通过构造器注入

2. **`SimpleMapMetadataRegistry`**
   - 负责 `@XtreamMapField` 注解的元数据解析
   - 全部静态方法 + 静态 `ConcurrentHashMap` 缓存
   - `BeanMetadataRegistry` 通过方法参数传入
   - 含 `// todo 移动到 BeanMetadataRegistry ?` 待办

两者在 Key、ValueLength、ValueMatcher 等基础注解解析逻辑上有 80%+ 的重叠。
`SimpleMapMetadataRegistry` 甚至已通过 `DefaultExtendMetaRegistry.readKey()` 跨类调用。

## Goals / Non-Goals

**Goals:**
- 消除 `DefaultExtendMetaRegistry` 和 `SimpleMapMetadataRegistry` 之间的全部重复方法（约 15+ 个方法，300+ 行）
- 统一缓存策略：全部使用实例级缓存
- 统一 `matchVersion()` 实现到 `HasVersions` 工具类
- 统一模型记录：消除 `SimpleMapMetadataRegistry` 内部的重复 record 定义
- `MapFieldCodec` 从静态 import 改为持有实例引用

**Non-Goals:**
- 不改动 `ExtendMetaRegistry` 接口定义
- 不改动 `MapFieldCodec` 的编解码行为
- 不改动 TLV/Pair 的语义和流程
- 不涉及现有测试逻辑修改

## Decisions

### D1: 抽取共享工具类 `MetadataUtils`

**方案**: 新建 `io.github.hylexus.xtream.codec.core.impl.codec.utils.MetadataUtils` 纯静态工具类，包含所有通用逻辑。

**包含方法**:
- `createKeyMeta(int targetVersion, Key[] keys)` — 解析 Key 注解
- `createKeyCodec(Key key)` — 创建 key 编解码器
- `parseKeyCharset(Key key)` — 解析 key 字符集
- `createValueLengthMeta(int targetVersion, ValueLength[] valueLengths)` — 解析 ValueLength 注解
- `detectCharset(...)`（多个重载）— 字符集检测
- `firstOr(String charset, String fallbackCharset)` — 字符串回退
- `findDuplicateVersions(int[] version)` — 版本号重复检测
- `isVersionMatched(int targetVersion, int versionCandidate)` — 版本匹配
- `readKey(KeyType keyType, ValueMatcher valueMatcher)` + `checkExclusive()` + 7 个 `readXxxKey()` — Key 读取

**理由**: 这些方法都是纯函数式的确定逻辑，不依赖实例状态，适合静态工具类。`createValueCodec()` 依赖 `BeanMetadataRegistry`，通过参数传入即可。

### D2: 缓存策略 → 统一使用实例级 `ConcurrentHashMap`

**决定**: `SimpleMapMetadataRegistry` 改为实例级缓存，与 `DefaultExtendMetaRegistry` 保持一致。

**理由**:
1. **元数据存在 `BeanMetadataRegistry` 依赖**: 虽然目前 `BeanMetadataRegistry` 通常是单例，但理论上不同实例可能注册不同 codec。使用静态缓存意味着所有实例共享，导致测试间耦合。
2. **测试性**: 实例缓存可在测试间独立隔离，每个测试创建各自实例，无需清理全局状态。
3. **热重载兼容**: 在 Spring DevTools 等类热加载场景下，静态缓存会持有旧类加载器的引用，导致内存泄漏风险。
4. **没有性能损失**: `ConcurrentHashMap` 的实例级缓存开销可忽略；元数据计算本身就是 I/O 密集的反射操作，缓存命中减少的重复计算远大于 map 开销。
5. **一致性**: 当前 `DefaultExtendMetaRegistry` 已使用实例缓存，统一策略降低心智负担。

**静态缓存的反对理由**（被否决）:
- "元数据是确定的，同一个 field 相同版本下结果永远相同" → 理论上正确，但 `BeanMetadataRegistry` 依赖打破了纯函数性质
- "单例 + 静态缓存性能更好" → 注册表本身通常也是单例，两者无差异

### D3: SimpleMapMetadataRegistry 改为实例化

**方案**:
1. `SimpleMapMetadataRegistry` 从纯静态类改为普通类，构造器接收 `BeanMetadataRegistry` 参数
2. `getOrCreateMapMetadata()` 改为实例方法
3. 缓存从 `static final` 改为实例字段
4. `MapFieldCodec` 在构造时直接 `new SimpleMapMetadataRegistry(beanMetadataRegistry)` 创建实例

**依赖注入链**:
```
MapFieldCodec(beanMetadataRegistry, fieldCodecRegistry)
  └─ this.mapMetaRegistry = new SimpleMapMetadataRegistry(beanMetadataRegistry)
```

**为什么不搞单例？**

不需要。`MapFieldCodec` 本身就是通过 `FieldCodecRegistry` 管理的单例（由 `@FieldCodecCreator` 工厂方法创建一次）。所以 `MapFieldCodec` 一份实例 → `SimpleMapMetadataRegistry` 一份实例 → 一份实例缓存，天然一一对应。

如果将来出现多份 `MapFieldCodec` 实例需要共享缓存的需求，再引入工厂或注册表也不迟。现阶段用最直接的方式：

```java
// MapFieldCodec.java
private final SimpleMapMetadataRegistry mapMetaRegistry;

@FieldCodecCreator
public MapFieldCodec(BeanMetadataRegistry beanMetadataRegistry, FieldCodecRegistry fieldCodecRegistry) {
    this.beanMetadataRegistry = requireNonNull(beanMetadataRegistry);
    this.mapMetaRegistry = new SimpleMapMetadataRegistry(beanMetadataRegistry);
    // BeanMetadataRegistry 已确保 fieldCodecRegistry 一致
}
```

**替代方案考虑**:
- 将 `SimpleMapMetadataRegistry` 完全合并到 `DefaultExtendMetaRegistry` → **否决**。两者处理不同注解源（`@XtreamMapField` vs `@XtreamTLVFieldSequence`/`@XtreamPairFieldSequence`），产出不同类型（`MapMeta` vs `XtreamTLVFieldSequenceMeta`/`XtreamPairFieldSequenceMeta`），合并会导致职责不清晰。
- 在 `BeanMetadataRegistry` 上加 `getSimpleMapMetadataRegistry()` 方法 → 否决。为核心接口加专用方法属于接口污染。
- 使用 `getInstance()` 工厂 + `ConcurrentMap<BeanMetadataRegistry, SimpleMapMetadataRegistry>` 弱单例 → 否决。`BeanMetadataRegistry` 本身能否作为稳定 key 存疑；当前不需要多实例共享缓存。
- 在 `BeanMetadataRegistry` 上增加 Map 元数据方法 → 暂不实施。`todo` 注释提到过这个方向，但当前改动量已足够，后续可再评估。

### D4: 统一模型记录

**方案**: 扩充 `impl/domain/` 包中的 record 以覆盖 `SimpleMapMetadataRegistry` 内联 record 的额外字段。

| Domain Record | 要增加的字段 |
|---------------|-------------|
| `KeyMeta` | 增加 `resolvedCharset: Charset`（从 `charset: String` 解析） |
| `ValueLengthMeta` | 增加 `codec: FieldCodec<Object>`（通过 `LengthFieldType.type().codec()` 创建） |

然后 `SimpleMapMetadataRegistry` 删除内部重复的 record 定义，直接使用 domain 包 record。

### D5: 统一 `matchVersion()` 实现

`SimpleMapMetadataRegistry` 私有的 `matchVersion()` 与 `HasVersions.matchVersion()` 几乎相同，差异在于：
- `HasVersions.matchVersion()` 有一段被注释掉的 `targetVersion == XtreamField.ALL_VERSION` 提前返回逻辑
- `SimpleMapMetadataRegistry` 版有完整的 `ALL_VERSION` 检查

**方案**: 将完整的实现合并到 `HasVersions.matchVersion()`（取消注释并完善），`SimpleMapMetadataRegistry` 删除私有版本。

### D6: 异常信息格式保持可读性

**决定**: 重构后的异常消息格式必须与原有一致，保持多行 template 风格、字段路径提示、`===>` 标记等可读性设计。

**理由**: 现有异常处理中有精心设计的多行错误消息（如 `DefaultExtendMetaRegistry` 中的 `"""..."""` 文本块），包含字段签名、关键参数的值、建议的修正方向。这些信息对用户调试协议定义至关重要，不能因为在抽取过程中丢失。

**约束**:
- 所有 `IllegalArgumentException` 消息保留 `"""` 文本块格式
- 包含 `===> Field: {}` 或 `==> {}` 标记的保留
- `formateKey()` 中 `0x` 十六进制格式保留
- `log.error()` 中的错误上下文（field.toGenericString()）保留

## Risks / Trade-offs

- **[MapFieldCodec 构造变更]** `MapFieldCodec` 通过 `@FieldCodecCreator` 注解的工厂方法创建，需确保构造链能传入 `SimpleMapMetadataRegistry` 实例。→ 不影响，`MapFieldCodec` 已持有 `BeanMetadataRegistry`，可通过它创建或获取 `SimpleMapMetadataRegistry`。
- **[性能回归]** 从静态缓存改为实例缓存，每个 `MapFieldCodec` 实例各自持有缓存。→ 实际上 `MapFieldCodec` 通常是单例注册的（通过 `FieldCodecRegistry`），所以缓存实例数与之前相同。
- **[兼容性]** `SimpleMapMetadataRegistry` 的 `getOrCreateMapMetadata()` 是包级私有的（`static` 无 `public`），仅 `MapFieldCodec` 在同一包下调用。→ 外部无感知，无需迁移。

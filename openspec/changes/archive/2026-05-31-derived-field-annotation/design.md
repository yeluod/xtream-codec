## Context

当前 xtream-codec-core 的编解码管线只有一条路径：`ByteBuf → Java 字段`。所有用 `@Preset.JtStyle.*`（或底层 `@XtreamField`）标记的字段都从 ByteBuf 中读取/写入。对于按位定义、枚举组合等场景，解码后的原始整数值需要业务方手动转换。

已有的相关基础设施：

- `FieldCodec<T>` SPI — 可实现自定义类型的 ByteBuf 编解码
- `CodecStrategy.TRANSIENT` — 可跳过字段的编解码
- `FiledDataType` — 定义了 `basic` / `struct` / `sequence` / `map` / `dynamic` / `unknown` 六种数据类型
- 管线入口：`EntityDecoder.decode()` 单遍循环解码所有字段；`EntityEncoder.encode()` 单遍循环编码所有字段

## Goals / Non-Goals

**Goals:**

- 新增 `@DerivedField` 注解，标记在实体类的字段上，声明其值从另一个已解码字段衍生而来
- 新增 `FieldTransformer<S, T>` 接口，定义 `S → T`（解码后转换，`read()`）和 `T → S`（编码前逆向，`write()`）
- 新增编解码后处理步骤：解码完成后执行 `read()`，编码前对标记为 `reverseSource = true` 的字段执行 `write()` 回写源字段
- 支持从**同一个 source 衍生出多个不同类型的字段**（如 `Set<Enum>`、`Map<String, Boolean>`、`LoadStatus` 枚举等），解码时无冲突
- 提供可选工具：`BitFlag` 标记接口 + `EnumSetBitTransformer` 位段枚举转换器（使用子类桥接模式传递枚举类型参数）

**Non-Goals:**

- 不修改现有 `@XtreamField` / `@Preset.JtStyle.*` 的语义
- 不改变现有 `ByteBuf` 编解码管线
- 不引入强制依赖或编译期 APT
- `@DerivedField` 不耦合 bit-field 场景——它是通用的 S → T 衍生机制
- 不覆盖 multi-field aggregation 或编译期代码生成

## Decisions

### Decision 1: `@DerivedField` 作为独立注解，不 alias `@XtreamField`

**方案对比：**

| 方案 | 评价 |
|------|------|
| A: `@DerivedField` 独立注解，不 alias `@XtreamField` | ✅ 语义清晰，与 ByteBuf 管线彻底解耦 |
| B: `@DerivedField` 作为 `@XtreamField` 的一个属性 | ❌ `@XtreamField` 属性已过多，再叠加衍生语义会混淆 |

**结论**: 采用方案 A。`@DerivedField` 字段不参与 ByteBuf 编解码，仅在后处理中被填充。

### Decision 2: 在 `BeanPropertyMetadata` 中新增 `isDerived()` 标记

现有 `FiledDataType.derived` 不可行（FiledDataType 用于确定 FieldCodec，而衍生字段没有 FieldCodec），因此新增一个独立标记：

```java
// BeanPropertyMetadata 新增
default boolean isDerived() { return false; }
```

衍生字段的 `BeanPropertyMetadata` 实现覆盖此方法返回 `true`。编解码主循环通过该标记跳过衍生字段。

### Decision 3: 解码后处理 — 在 `EntityDecoder.decode()` 主循环之后追加 derived pass

```java
// 伪代码：EntityDecoder.decode()
// Pass 1: 现有 ByteBuf 解码循环（跳过 isDerived() == true 的字段）
for (property : propertyMetadataList) {
    if (property.isDerived()) continue;
    // ... 现有解码逻辑，解码后所有值通过 evaluationContext.setVariable() 暴露
}

// Pass 2: 衍生字段计算
for (property : propertyMetadataList) {
    if (!property.isDerived()) continue;
    Object sourceValue = context.evaluationContext().getVariable(property.derivedSource());
    Object derivedValue = property.derivedTransformer().read(sourceValue);
    property.setProperty(containerInstance, derivedValue);
}
```

多个衍生字段从同一个 source 取值的场景天然支持——每个独立执行 `read()`，写入不同字段，互不干扰。

### Decision 4: 编码前处理 — 在 `EntityEncoder.encode()` 主循环之前追加 reverse pass

```java
// 伪代码：EntityEncoder.encode()
// Pass 0: 衍生字段逆向回写（仅 reverseSource = true 的字段）
for (property : propertyMetadataList) {
    if (!property.isDerived()) continue;
    if (!property.reverseSource()) continue;   // 只回写主动声明 reverseSource 的字段
    Object derivedValue = property.getProperty(instance);
    if (derivedValue == null) continue;
    Object sourceValue = property.derivedTransformer().write(derivedValue);
    BeanPropertyMetadata sourceProperty = findProperty(property.derivedSource());
    sourceProperty.setProperty(instance, sourceValue);
}

// Pass 1: 现有 ByteBuf 编码循环（跳过 isDerived() == true 的字段）
for (property : propertyMetadataList) {
    if (property.isDerived()) continue;
    // ... 现有编码逻辑
}
```

通过 `reverseSource` 属性精确控制哪个衍生字段负责回写 source，避免多字段时的覆盖冲突。

### Decision 5: `@DerivedField` 注解定义

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DerivedField {
    /** 源字段的名称（与实体类中的字段名一致） */
    String source();

    /** 转换器实现类。必须有无参构造器，或框架能通过反射实例化 */
    Class<? extends FieldTransformer<?, ?>> using();

    /** 是否负责反向回写 source 字段。默认 false。同一个 source 只能有一个 reverseSource=true 的衍生字段 */
    boolean reverseSource() default false;

    /** 描述 */
    String desc() default "";
}
```

`reverseSource` 的设计意图：

| 场景 | reverseSource | 行为 |
|------|-------------|------|
| 单字段，需要编解码 | `true`（默认） | 正常 reverse → source |
| 多字段，只有一个需要回写 | 写回的那个 `true`，其他的 `false` | Pass 0 只处理 `true` 的 |
| 多字段，都只读不用编码 | 全 `false` | Pass 0 跳过 |
| 多字段，多个标记 `true` | — | 启动时抛异常（同一个 source 只能一个回写） |

### Decision 6: `FieldTransformer` 接口定义

```java
public interface FieldTransformer<S, T> {
    @Nullable T read(@Nullable S source);

    /**
     * 可选的逆向转换。编码时需要。
     * 默认抛出 UnsupportedOperationException，表示该衍生字段只读（不参与编码）。
     */
    @Nullable default S write(@Nullable T derived) {
        throw new UnsupportedOperationException(
            this.getClass().getSimpleName() + " does not support write-back"
        );
    }
}
```

`write()` 为可选设计的原因：部分衍生场景（如运行时计算的聚合值、只读视图）不需要逆向，此时只需实现 `read()` 即可。

### Decision 7: `BitFlag` 和 `EnumSetBitTransformer` 作为可选工具

`BitFlag` 和 `EnumSetBitTransformer` 是 optional utilities，**不属于 `@DerivedField` 核心设计**。位于 `common.utils` 包。

```java
// common/utils/BitFlag.java
public interface BitFlag {
    int bitOffset();
    default int bitLength() { return 1; }
}

// common/utils/EnumSetBitTransformer.java
public abstract class EnumSetBitTransformer<E extends Enum<E> & BitFlag>
        implements FieldTransformer<Long, Set<E>> {

    private final Class<E> enumType;

    protected EnumSetBitTransformer(Class<E> enumType) {
        this.enumType = enumType;
    }

    // read() / write() 实现...
}
```

**泛型类型参数问题**：`@DerivedField(using = XXX.class)` 只能传递 `Class` 字面量，无法传递泛型实参。因此 `EnumSetBitTransformer` 设计为抽象类，用户写 3 行子类桥接枚举类型：

```java
// 3 行子类，桥接泛型参数
public class StatusBitTransformer extends EnumSetBitTransformer<StatusBit> {
    public StatusBitTransformer() {
        super(StatusBit.class);
    }
}
```

如果用户不想写子类，也可以直接实现 `FieldTransformer<Long, Set<StatusBit>>` 接口，完全自定义逻辑。

### Decision 8: 自定义 Transformer 示例（多 bit range）

bit range（如 8~9 位表示载货状态）不适合用 `EnumSet` 表达——它是一个排他性枚举值，不是一组独立 flag。用户直接写自定义 Transformer：

```java
public enum LoadStatus {
    EMPTY(0), HALF(1), RESERVED(2), FULL(3);
    final int bits;
    LoadStatus(int bits) { this.bits = bits; }

    public static LoadStatus from(long status) {
        int v = (int)((status >> 8) & 0x3);
        for (LoadStatus ls : values()) if (ls.bits == v) return ls;
        throw new IllegalArgumentException("Unknown: " + v);
    }
}

public class LoadStatusTransformer implements FieldTransformer<Long, LoadStatus> {
    @Override
    public LoadStatus read(Long source) {
        return source == null ? null : LoadStatus.from(source);
    }
    @Override
    public Long write(LoadStatus derived) {
        return derived == null ? 0L : ((long) derived.bits) << 8;
    }
}
```

`@DerivedField` 对此无任何特殊处理——它就是 `FieldTransformer` 的一个普通实现。

### Decision 9: 衍生字段对 Record 类型的支持

衍生字段不能是 Record 的 canonical constructor 参数（因为它们的值不来自 ByteBuf，无法在构造时提供）。

方案：Record 类中使用 `@DerivedField` 的字段必须是**非 record component 的实例字段**（即声明在 Record 体中的字段，不在构造器参数列表中）。例如：

```java
public record BuiltinMessage0200(
    @JtStyle.Dword long status
    // ... 其他 record components
) {
    @DerivedField(source = "status", using = StatusBitTransformer.class)
    private transient Set<StatusBit> statusFlags;

    public Set<StatusBit> getStatusFlags() { return statusFlags; }
}
```

对于非 Record 类，无此限制。

### Decision 10: BeanMetadata 扫描中对 `@DerivedField` 的处理

现有的 `BeanMetadataRegistry` 扫描逻辑（`SimpleBeanMetadataRegistry` / `BeanUtils`）需要新增识别 `@DerivedField` 的逻辑：

1. 扫描所有字段，同时检查 `@XtreamField`（及其别名）和 `@DerivedField`
2. 只有 `@DerivedField` 没有 `@XtreamField` → 创建 `DerivedBeanPropertyMetadata`（`isDerived()=true`）
3. 既有 `@XtreamField` 又有 `@DerivedField` → 作为普通字段处理（`@DerivedField` 被忽略或抛警告）
4. 只有 `@XtreamField` → 现有逻辑不变

`DerivedBeanPropertyMetadata` 需实现：
- `isDerived()` → `true`
- `reverseSource()` → 从注解中提取
- `derivedSource()` → 从注解中提取 `source` 属性值
- `derivedTransformer()` → 从注解中解析 `using` Class 并缓存实例
- `decodePropertyValue()` / `encodePropertyValue()` → 空操作（不参与 ByteBuf）

### 完整使用示例

```java
public class BuiltinMessage0200 {
    @JtStyle.Dword(desc = "状态")
    private long status;

    // 1. 单 bit flags → EnumSet（通过子类桥接泛型）
    @DerivedField(source = "status", using = StatusBitTransformer.class, reverseSource = true)
    private transient Set<StatusBit> statusFlags;

    // 2. 多 bit range → 自定义枚举（纯自定义 Transformer）
    @DerivedField(source = "status", using = LoadStatusTransformer.class, reverseSource = false)
    private transient LoadStatus loadStatus;

    // 3. 自定义视图 → Map（纯自定义 Transformer，只读；value 为 bit range 的值 0/1/2/3）
    @DerivedField(source = "status", using = StatusMapTransformer.class, reverseSource = false)
    private transient Map<String, Integer> statusMap;
}
```

解码时三个字段都从 `status` 衍生，互不冲突。编码时只有 `statusFlags`（`reverseSource = true`）负责回写 `status`。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| **同一个 source 有多个 reverseSource=true 的衍生字段** | 初始化时检测，抛异常 |
| **衍生字段与 condition 表达式的交互**：如果 source 字段因 condition 为 false 而未解码，衍生字段无法计算 | 文档明确：derived 的 source 字段的 condition 必须为 true 或无条件 |
| **Record 类型中对非 canonical 字段的反射性能** | Record 中非 component 字段通过常规反射访问，性能略低但可接受；后续优化可用 `VarHandle` |
| **循环依赖**：衍生字段的 source 本身也是衍生字段 | 运行时检测（在 Pass 2/Pass 0 中检测未解决的 source 并抛警告） |
| **序列化顺序**：@DerivedField 不参与 order 排序 | 不排序是合理的——derived pass 总是最后/最先执行 |

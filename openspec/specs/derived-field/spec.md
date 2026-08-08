## Purpose

允许从已解码的协议字段声明式地衍生出业务视图字段（如 `EnumSet`、枚举、`Map` 等），避免业务层重复位运算或类型转换。

## Requirements

### Requirement: @DerivedField 注解定义

`@DerivedField` 是字段/Record 组件级注解，SHALL 标记该字段的值不是直接从 ByteBuf 中解码，而是从一个已有解码字段衍生而来。它是纯通用的 S → T 衍生机制，与 bit-field 场景解耦。

支持 `@Repeatable`：可在同一字段上为不同版本声明多个 `@DerivedField`，通过 `version()` 属性区分。

```java
@Repeatable(DerivedFieldContainer.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DerivedField {

    /** 匹配任意版本的常量（与 XtreamField.ALL_VERSION 值相同） */
    int ALL_VERSION = Integer.MIN_VALUE;

    /** 适用版本号，默认匹配所有版本。支持多版本：int[] */
    int[] version() default {ALL_VERSION};

    /** 源字段的名称（与实体类中的字段名一致） */
    String source();
    /** 转换器实现类。必须有无参构造器。 */
    Class<? extends FieldTransformer<?, ?>> using();
    /**
     * 是否负责反向回写 source 字段。
     * 默认 false。同一个 source 只能有一个 reverseSource=true 的衍生字段。
     */
    boolean reverseSource() default false;
    /** 描述 */
    String desc() default "";
}
```

版本匹配规则（与 `@XtreamField` 一致）：
- 精确匹配目标版本 → 使用该注解
- 无精确匹配，有 `ALL_VERSION` → 使用默认版本兜底
- 无精确匹配也无默认版本 → 忽略该字段

#### Scenario: 注解标记在实体类字段上
- **WHEN** 一个实体类的字段使用 `@DerivedField(source = "status", using = StatusBitTransformer.class)` 注解
- **THEN** 该字段在解码时不会从 ByteBuf 读取数据，而是在其数据源字段 `status` 解码完成后，立即通过 `StatusBitTransformer.read()` 内联计算得到**

#### Scenario: 同一个 source 衍生出多个不同类型的字段
- **WHEN** 实体类中定义了以下字段：
  ```java
  @DerivedField(source = "status", using = StatusBitTransformer.class, reverseSource = true)
  private transient Set<StatusBit> statusFlags;

  @DerivedField(source = "status", using = StatusMapTransformer.class, reverseSource = false)
  private transient Map<String, Integer> statusMap;

  @DerivedField(source = "status", using = LoadStatusTransformer.class, reverseSource = false)
  private transient LoadStatus loadStatus;
  ```
- **THEN** 解码时三个字段都从 `status` 的值独立内联衍生，互不冲突
- **THEN** 编码时只有 `reverseSource=true` 的 `statusFlags` 参与编码值替换：编码循环遍历到源字段 `status` 时，读取 `statusFlags` 的 Getter 值并调用 `write()`，用结果替代原始 `status` 值写入 ByteBuf；`statusMap` 和 `loadStatus` 被跳过

#### Scenario: 同一个 source 多个 reverseSource=true 检测
- **WHEN** 实体类中同一个 source 有两个或以上 `reverseSource=true` 的 `@DerivedField`
- **THEN** 框架在启动时检测到此冲突并抛出异常，要求用户明确指定唯一回写字段

### Requirement: FieldTransformer 接口

`FieldTransformer<S, T>` SHALL 定义从源类型到衍生类型的转换契约。用户自定义完整的转换逻辑。

```java
public interface FieldTransformer<S, T> {
    @Nullable T read(@Nullable S source);
    @Nullable default S write(@Nullable T derived) {
        throw new UnsupportedOperationException("write not supported");
    }
}
```

#### Scenario: 解码时执行 read
- **WHEN** 一个实体被解码
- **THEN** 在解码循环中，每个普通字段解码后，框架立即查询该字段的 `derivedBySource` 索引表，若存在关联的 `@DerivedField`，则调用 `FieldTransformer.read(sourceValue)` 并将结果填入实例/Record 构造器数组
- **THEN** 整个解码仅需单遍遍历，无需额外的后处理 pass

#### Scenario: 编码时执行 write
- **WHEN** 一个实体被编码，且其字段在 `reverseDerivedBySource` 索引表中存在对应的 reverseSource 派生字段，且 transformer 实现了 `write()` 方法
- **THEN** 在编码循环中，遍历到该普通字段时，不是直接读取其 Getter 值，而是读取派生字段的 Getter 值，调用 `FieldTransformer.write(derivedValue)`，将结果作为编码值写入 ByteBuf
- **THEN** 不修改实体实例中的源字段值（无副作用）

#### Scenario: reverseSource=false 的字段不影响编码
- **WHEN** 一个 `@DerivedField` 的 `reverseSource=false`
- **THEN** 编码时该衍生字段被完全跳过，不影响编码结果

#### Scenario: write() 未实现的字段不影响编码
- **WHEN** 一个 `@DerivedField` 的 `reverseSource=true` 但 transformer 未实现 `write()`（默认抛出 `UnsupportedOperationException`）
- **THEN** 编码时该衍生字段被跳过，不影响编码结果

#### Scenario: 自定义 Transformer 示例
- **WHEN** 用户定义一个 Transformer：
  ```java
  public class LoadStatusTransformer implements FieldTransformer<Long, LoadStatus> {
      @Override public LoadStatus read(Long source) {
          if (source == null) return null;
          int v = (int)((source >> 8) & 0x3);
          return LoadStatus.from(v);
      }
      @Override public Long write(LoadStatus derived) {
          return derived == null ? 0L : ((long) derived.bits) << 8;
      }
  }
  ```
- **THEN** `@DerivedField(source = "status", using = LoadStatusTransformer.class, reverseSource = false)` 可以在解码时将 status 的 8~9 bit 转换为 `LoadStatus` 枚举

### Requirement: 实体解码内联求值管线

在 `EntityDecoder.decode()` 的主循环中，每个源字段解码后 SHALL 立即计算依赖它的派生字段值。

#### Scenario: 解码过程中衍生字段被内联填充
- **WHEN** `EntityDecoder.decode()` 对包含 `@DerivedField` 字段的实体执行解码
- **THEN** 在解码循环中，每个普通字段解码完成后，立即查询 `BeanMetadata.getDerivedBySource()` 索引表，若存在关联的 `@DerivedField`，则调用 `FieldTransformer.read(sourceValue)`：
  - **POJO**：结果通过 `derived.setProperty(instance, value)` 注入
  - **Record**：结果直接填入 `filedValues[derivedIndex]`，使 Record 构造器接收完整的参数数组（含派生值）
- **THEN** 整个解码只需单遍遍历 ByteBuf，无后续 pass

#### Scenario: 衍生字段不影响非衍生字段的解码
- **WHEN** 包含 `@DerivedField` 字段的实体被解码
- **THEN** 非衍生字段的 ByteBuf 读取顺序、长度、条件和现有行为完全一致，内联求值仅发生在每个源字段解码完成后，不改变 ByteBuf 读取流程

### Requirement: 实体编码内联值替换管线

在 `EntityEncoder.encode()` 的主循环中，每个普通字段编码前 SHALL 检查是否有对应的 reverseSource 派生字段，若有则从派生字段取值 + 逆变换。

#### Scenario: 编码时派生值替换源字段值
- **WHEN** `EntityEncoder.encode()` 对包含 `@DerivedField(reverseSource=true)` 的实体执行编码
- **THEN** 编码循环中遍历到普通字段时，查询 `BeanMetadata.getReverseDerivedBySource()` 索引表：
  - 若存在对应的派生字段，读取其 Getter 值，调用 `derivedTransformer.write(derivedValue)` 作为编码值
  - 若无派生字段，则直接读取源字段的 Getter 值
- **THEN** 不修改实体实例的任何字段值（无副作用）

#### Scenario: 回写不影响非衍生字段的编码
- **WHEN** 编码循环执行
- **THEN** 非衍生字段的 ByteBuf 写入顺序、长度和现有行为完全一致；reverseSource 逻辑仅改变从哪个 Getter 取值，不改变编码时序

### Requirement: BeanMetadata 创建索引表识别 @DerivedField

`BeanMetadata` 构建时 SHALL 识别 `@DerivedField` 并创建对应的 `DerivedBeanPropertyMetadata`。同时构建 `derivedBySource` 和 `reverseDerivedBySource` 索引表用于编解码时的 O(1) 内联查找。

#### Scenario: 构建 derivedBySource 和 reverseDerivedBySource 索引表
- **WHEN** `SimpleBeanMetadataRegistry.doGetMetadata()` 完成 `pdList` 构建和排序
- **THEN** 遍历 `pdList`，对每个 `DerivedBeanPropertyMetadata`：
  - 以 `source` 字段名为 key 加入 `derivedBySource` 多值索引表（解码时用）
  - 若 `reverseSource=true`，以 `source` 字段名为 key 加入 `reverseDerivedBySource` 单值索引表（编码时用）
- **THEN** 两个索引表传入 `BeanMetadata` 构造器，可通过 `hasDerivedFields()` 快速判断是否含派生字段

#### Scenario: 只有 @DerivedField 没有 @XtreamField 的字段被识别为衍生字段
- **WHEN** 实体扫描过程中发现一个字段仅有 `@DerivedField` 注解（无 `@XtreamField` 或其别名 `@JtStyle.*`/`@RustStyle.*`）
- **THEN** 该字段被标记为 `isDerived() = true`，不参与 ByteBuf 编解码循环

#### Scenario: 同时有 @DerivedField 和 @XtreamField 的字段按普通字段处理
- **WHEN** 一个字段同时具有 `@DerivedField` 和 `@XtreamField`（或其别名）
- **THEN** 该字段被当作普通字段处理，`@DerivedField` 被忽略（或打印一条 warning 日志）

### Requirement: 衍生字段在 Record 类型中的支持

衍生字段 SHALL 可以出现在 Record 类中，支持两种使用方式：
1. Record 组件（构造器参数）上直接标注 `@DerivedField`（需 `@Target` 包含 `RECORD_COMPONENT`）
2. Record body 中声明的 `transient` 字段标注 `@DerivedField`（非 canonical 构造器参数）

当 Record 组件上只有 `@DerivedField`、没有显式 `@XtreamField` 时，`XtreamFieldUtils.getOrDefault()` 不会生成默认的 `@XtreamField` 代理实例，确保 `@DerivedField` 被正确识别为衍生字段。

#### Scenario: Record 组件上的衍生字段
- **WHEN** 一个 Record 组件的定义如下：
  ```java
  public record NestedRecordEntity(
      @Preset.RustStyle.u8 long level,
      @DerivedField(source = "level", using = StatusDisplayTransformer.class) String levelDisplay
  ) {}
  ```
- **THEN** `levelDisplay` 被识别为 `isDerived() = true`，不参与 ByteBuf 读取
- **THEN** 解码时 `level` 解码完成后立即通过内联求值计算 `levelDisplay`
- **THEN** Record 构造器接收完整的参数（包括派生值）

#### Scenario: Record body 中的衍生字段
- **WHEN** 一个 Record 类的 body 中使用 `@DerivedField` 声明一个 `transient` 字段
- **THEN** 解码时该字段的值通过内联求值填入 `filedValues[derivedIndex]`，与普通字段一同传入 Record 的 canonical 构造器
- **THEN** 编码时该字段的 Getter 值通过 reverseSource 索引读取并逆变换，替代源字段的编码值

### Requirement: 循环依赖检测

框架 SHALL 对衍生字段的 source 依赖进行基础检测，防止循环衍生。

#### Scenario: source 字段本身是衍生字段时告警
- **WHEN** 字段 A 标记 `@DerivedField(source = "B")`，字段 B 也标记了 `@DerivedField`
- **THEN** 在解码内联求值阶段，`applyDerivedFieldsInline()` 检测到 A 的 sourceValue 为 null（因为 B 是派生字段，其源索引位无值），跳过 A 的衍生计算并打印一条 warning 日志

### Requirement: 内嵌 Bean 中的派生字段支持

嵌入到外层 Bean 中的嵌套类或 Record 如果包含 `@DerivedField`，SHALL 在 `NestedBeanPropertyMetadata` 的 decode 循环中执行内联求值，与顶层 `EntityDecoder` 的行为一致。

#### Scenario: 内嵌普通类中的派生字段被正确计算
- **WHEN** 一个外层实体的字段是嵌套 POJO 类型：
  ```java
  @Preset.RustStyle.struct
  private InnerClassEntity inner;
  ```
  其中 `InnerClassEntity` 包含 `@DerivedField(source = "level", using = StatusDisplayTransformer.class)`
- **THEN** 外层实体解码时，`NestedBeanPropertyMetadata.decodePropertyValue()` 遍历 `InnerClassEntity` 的属性：
  - 跳过 `isDerived()` 字段（不尝试从 ByteBuf 读取）
  - 解码 `level` 后，调用 `applyNestedDerivedFieldsInline(value, "level", nestedBeanMetadata, ...)` 计算 `levelDisplay`
- **THEN** 解码后的 `inner.levelDisplay` 值为 `"unknown:2"`（假设原始 `level` 值为 2）

#### Scenario: 内嵌 Record 中的派生字段被正确计算
- **WHEN** 一个外层实体的字段是嵌套 Record 类型：
  ```java
  @Preset.RustStyle.struct
  NestedRecordEntity inner;
  ```
  其中 `NestedRecordEntity` 包含 `@DerivedField(source = "level", using = StatusDisplayTransformer.class)`
- **THEN** 解码路径同内嵌普通类，`applyNestedDerivedFieldsInline` 在 Record 组件中同样生效
- **THEN** 解码后的 `inner.levelDisplay()` 值为正确的转换结果
- **NOTE** `@DerivedField` 在 Record 组件上使用时，`@Target` 必须包含 `RECORD_COMPONENT`，且 `XtreamFieldUtils.getOrDefault()` 检测到 `@DerivedField` 存在时不会生成默认的 `@XtreamField` 代理实例

#### Scenario: 内嵌 Bean 中的派生字段不影响外层字段解码
- **WHEN** 外层实体和内嵌 Bean 都包含 `@DerivedField`
- **THEN** 两个层级的派生字段各自独立计算，互不干扰
- **THEN** 外层 ByteBuf 读取顺序不受内层派生字段影响

<!-- 以下 requirement 定义了 `@DerivedField` 文档的行为规范。 -->

### Requirement: 文档页存在并可访问

文档系统 SHALL 在 `guide/core/annotation-driven/derived-field.md` 路径提供 `@DerivedField` 的使用指南，且该页 SHALL 在侧边栏 `注解驱动开发` 分组下可导航。

#### Scenario: 访问文档页

- **WHEN** 用户导航到 `guide/core/annotation-driven/derived-field.md`
- **THEN** 页面正常渲染，且侧边栏中该条目出现在 `内置注解` 之后、`自定义注解` 之前

---

### Requirement: 注解定义说明

文档 SHALL 说明 `@DerivedField` 的以下属性：

- `source` — 源字段名称，指向同一实体中已解码的普通字段
- `using` — `FieldTransformer` 实现类，必须有无参构造器
- `reverseSource` — 编码时是否逆向回写源字段（默认 false，每 source 最多一个 true）
- `version` — 多版本支持，与 `@XtreamField.version()` 匹配逻辑一致
- `desc` — 描述信息

#### Scenario: 注解属性覆盖

- **WHEN** 用户阅读 `@DerivedField` 文档
- **THEN** 文档列出 `source`、`using`、`reverseSource`、`version`、`desc` 五个属性并提供说明

---

### Requirement: FieldTransformer SPI 说明

文档 SHALL 说明 `FieldTransformer<S, T>` 接口的两个方法：

- `read(S source)` — 解码后从源字段值衍生目标值，在解码阶段调用
- `write(T derived) / write(T derived, S oldSource)` — 编码前将衍生值逆向回写为源字段值，可选实现

#### Scenario: SPI 方法说明

- **WHEN** 用户阅读 `FieldTransformer` 部分
- **THEN** 文档说明 `read()` 和 `write()` 的调用时机和默认行为

---

### Requirement: 编解码流水线说明

文档 SHALL 说明 `@DerivedField` 在编解码全过程中的行为：

- **解码**：单遍内联 — 源字段解码后立即通过 `derivedBySource` 索引找到依赖它的派生字段并计算值
- **编码**：遍历源字段时，若存在 `reverseSource=true` 的派生字段，调用 `write()` 回写后作为编码值写入 ByteBuf

#### Scenario: 解码流程

- **WHEN** 用户阅读编解码流水线说明
- **THEN** 文档说明解码时 `@DerivedField` 不读取 ByteBuf，而是由 `FieldTransformer.read()` 从源字段衍生

#### Scenario: 编码流程

- **WHEN** 用户阅读编码流水线说明
- **THEN** 文档说明编码时 `reverseSource=true` 的字段会通过 `FieldTransformer.write()` 回写源字段值

---

### Requirement: @Repeatable 多版本支持

文档 SHALL 说明 `@DerivedField` 自 0.6.0 起支持 `@Repeatable`，可在同一字段上为不同版本声明不同的派生规则，匹配逻辑与 `@XtreamField` 一致。

#### Scenario: 多版本示例

- **WHEN** 用户阅读多版本支持部分
- **THEN** 文档提供 `@Repeatable` 使用示例，展示不同版本不同派生规则的场景

---

### Requirement: BitFlag / EnumSetBitTransformer 工具说明

文档 SHALL 说明 `BitFlag` 标记接口和 `EnumSetBitTransformer` 抽象类的用途：

- `BitFlag` — 位段枚举的标记接口，定义 `offset()` 和 `length()`（单 bit 为 1，多 bit range >1）
- `EnumSetBitTransformer<E extends Enum<E> & BitFlag>` — 抽象基类，提供 `EnumSet<E>` 与 `long` 之间的双向转换，含旧位覆盖保护

#### Scenario: 位段工具说明

- **WHEN** 用户阅读工具类部分
- **THEN** 文档说明 `BitFlag` 和 `EnumSetBitTransformer` 的用法和适用场景

---

### Requirement: 完整实战示例

文档 SHALL 提供至少一个实战示例，展示从定义位段枚举 → 实现 Transformer → 实体中使用 `@DerivedField` 的完整流程。

#### Scenario: 示例可运行

- **WHEN** 开发者按照文档中的示例编写代码
- **THEN** 示例涉及的实体能够正确解码源字段并衍生出业务视图，编码时能逆向回写

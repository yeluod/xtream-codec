## ADDED Requirements

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

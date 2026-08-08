## Context

现有 `prependLengthFieldType` 机制只支持在单个字段前自动写入其长度。对于「从字段 A 到字段 B 的序列化字节数」这个广泛存在于二进制协议中的模式，用户只能手动计算并设置 Java 字段值，无法利用框架自动完成。

## Goals / Non-Goals

**Goals:**
- 提供声明式注解 `@EncodedLength`，标注在无符号整数长度字段上，编码时自动计算指定字段范围的编码后字节数并写入
- 支持平面单类、继承结构（父类定义 bodyLength、子类定义 body 字段）
- 支持 `from`/`until` 双参数，灵活排除不纳入计数的字段
- 编码时使用占位-回填策略，不改变现有编码流程的总体结构
- 把区间解析、字段顺序校验、长度字段格式校验前移到元数据阶段，避免在编码热路径中维护散落状态
- 解码时不特殊处理（长度字段按普通字段读取）

**Non-Goals:**
- v1 不支持同一实体声明多个 `@EncodedLength`；后续如有真实场景，再扩展为多个非重叠 `EncodedLengthPlan`
- 不支持解码时用 range 长度对 ByteBuf 切片（forward-compat 留待后续）
- 不修改 `@DerivedField` 机制

## Decisions

### 1. from/until（包含开始，排除结束）而非 from/to（双包含）

`from` 表示被计入范围的第一个字段，`until` 表示第一个不计入范围的字段；语义与常见的 `substring(from, until)` 一致。继承场景下父类可以写 `until = "checkSum"`，无需知道子类字段名。

当 `from` 为空时，范围从 `@EncodedLength` 字段之后的第一个实际编码字段开始；当 `until` 为空时，范围延伸到实体最后一个实际编码字段。

### 2. 编码回填策略：元数据计划 + 运行时辅助对象

不在 `encodePropertyValue` 方法内处理，也不把多个临时状态变量直接放在 `EntityEncoder` 主循环中。range 跨越多个字段，但这些字段边界可以在元数据阶段预先解析为一个计划：

```
EncodedLengthPlan(
  lengthFieldIndex,
  fromFieldIndex,
  untilFieldIndex,
  writer
)
```

编码时 `EntityEncoder` 只负责按字段顺序发出事件，由 `EncodedLengthRuntime` 记录占位符位置、范围起点和范围终点：

```
for each field:
  encodedLengthRuntime.beforeField(i, target)

  if current field is @EncodedLength:
    encodedLengthRuntime.writePlaceholder(...)
    continue

  encodeField(...)

encodedLengthRuntime.finish(target)
```

这种拆分保留单次编码和 ByteBuf 原地回填，避免额外缓冲区；普通实体仍走无 range 的编码路径。

### 3. 元数据层：BeanPropertyMetadata 加默认方法 + 内部计划

`BeanPropertyMetadata` 只新增 `isEncodedLength()`，用于保留字段标识。`from`/`until` 的解析结果、长度字段回填方式等作为 `EncodedLengthPlan` 存储在 `BeanMetadata` 或专用内部对象中，不扩大公共接口表面积。

### 4. 元数据注册：SimpleBeanMetadataRegistry 检测 @EncodedLength 并构建计划

与 `@DerivedField` 类似，在字段元数据构建时检测 `@EncodedLength`，校验 `from`/`until` 字段名在实体字段列表中存在，且 `until` 字段的编码顺序大于 `from` 字段。v1 检测到同一实体多个 `@EncodedLength` 时直接拒绝。

长度字段格式在元数据阶段解析，只接受 u8/u16/u32 这三种可明确原地回填的格式；其他格式提前报错。

## Risks / Trade-offs

- [风险] 字段名（`from`/`until`）是字符串引用，重构不友好 → 接受，这是字段级别注解的固有缺陷，与 `@DerivedField` 同类
- [风险] 继承场景下子类字段名可能拼写错误 → 元数据注册时验证 `from`/`until` 字段名存在性，编译期不可达，运行时尽早报错
- [风险] 多个 `@EncodedLength` 字段在同一实体中相互干扰 → v1 明确不支持多个，检测到多个时抛出 `IllegalArgumentException`
- [取舍] 使用 `EncodedLengthPlan` 和 `EncodedLengthRuntime` 会多出少量内部类型 → 接受，用于换取 `EntityEncoder` 主循环可读性和后续扩展空间

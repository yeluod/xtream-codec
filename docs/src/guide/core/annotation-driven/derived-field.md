---
date: 2026-06-02
tag:
  - 派生字段
  - 注解
---

# @DerivedField

## 介绍 <Badge text="0.6.0" type="tip" vertical="top"/> <Badge text="Experimental" type="danger" vertical="top" />

`@DerivedField` 用于从已解码的原始协议字段中**衍生**出业务视图，而无需改动原有的编解码字段。

典型的应用场景：

- 将 `long` 类型的位段（如状态字）自动解析为 `Set<Enum>`，而非手动编写位运算
- 将原始数值映射为可读的业务描述（如 `0 → "offline"`，`1 → "online"`）
- 编码时将业务视图逆向回写为原始协议的字段值

## 设计思想

```
ByteBuf ──→ long status        ← @Dword (byte-level codec)
              │
              └──→ Set<StatusBit>  ← @DerivedField (field-level transformation)
```

原始协议字段的编解码方式不变，`@DerivedField` 作为一个**可选的后处理步骤**，在解码完成后自动执行转换。这使得：

- 原始字段保持**向后兼容**，`getStatus()` 依然返回 `long`
- 衍生字段是**按需追加**的，不影响既有的协议解析逻辑
- 编码时可通过 `reverseSource=true` 将衍生值**逆向回写**到原始字段

## 注解属性

| 属性              | 类型                                  | 默认值             | 说明                                                                  |
|-----------------|-------------------------------------|-----------------|---------------------------------------------------------------------|
| `source`        | `String`                            | —               | 源字段名称，指向同一实体中已解码的普通字段（**必填**）                                       |
| `using`         | `Class<? extends FieldTransformer>` | —               | 转换器实现类，必须有无参构造器（**必填**）                                             |
| `reverseSource` | `boolean`                           | `false`         | 编码时是否将衍生值通过 `write()` 回写到源字段。每个 source 最多只能有一个 `reverseSource=true` |
| `version`       | `int[]`                             | `{ALL_VERSION}` | 适用的版本号，匹配逻辑与 `@XtreamField` 一致                                      |
| `desc`          | `String`                            | `""`            | 描述信息                                                                |

### source

指定当前衍生字段的数据来源，必须是同一实体中已通过 `@XtreamField`（或其别名注解）标记的字段。

```java
// status 是原始协议字段，通过 @Dword 从 ByteBuf 读取
@Preset.JtStyle.Dword(desc = "状态")
private long status;

// statusDisplay 不从 ByteBuf 读取，而是从 status 衍生
@DerivedField(source = "status", using = StatusDisplayTransformer.class)
private String statusDisplay;
```

### using

指定 `FieldTransformer` 的实现类，框架会在启动时通过无参构造器反射实例化。

### reverseSource

默认 `false`，表示该衍生字段是**只读**的——它仅用于解码后的展示，不参与编码。

设为 `true` 时，编码前会先调用 `FieldTransformer.write()` 将衍生值回写到源字段，再编码源字段：

```java

@DerivedField(source = "raw", using = UpperCaseTransformer.class, reverseSource = true)
private String upper;
```

如果同一个 source 有多个 `reverseSource=true` 的衍生字段，启动时会抛出异常。

### version

支持多版本声明，与 `@XtreamField` 的版本匹配逻辑一致。详见[多版本支持](#repeatable-多版本支持)。

## FieldTransformer SPI

`FieldTransformer<S, T>` 是框架提供的 SPI 接口，定义了两个方法：

| 方法                   | 方向   | 说明                                                        |
|----------------------|------|-----------------------------------------------------------|
| `T read(S source)`   | 解码方向 | 从已解码的源字段值计算出衍生字段值。source 为 `null` 时不会调用                   |
| `S write(T derived)` | 编码方向 | 将衍生字段值逆向转换为源字段值。默认抛出 `UnsupportedOperationException`，表示只读 |

实现类必须有一个无参构造器，框架通过反射实例化。

### 基础示例

@[code{32-}](@core-test/io/github/hylexus/xtream/codec/core/docs/BasicDerivedFieldTest.java)

## 编解码流水线

### 解码流程

解码时，`@DerivedField` 字段不参与 ByteBuf 的读取。源字段解码完成后，框架立即通过 `derivedBySource` 索引找到依赖它的衍生字段，并调用 `read()` 计算值。整个过程是**单遍内联**的，无需第二轮遍历。

### 编码流程

编码时分两步：

1. **逆向回写**（仅 `reverseSource=true`）：遍历所有 `reverseSource=true` 的衍生字段，调用 `write()` 将衍生值回写到对应的源字段
2. **正常编码**：按普通字段的编码逻辑将源字段写入 ByteBuf

### 源字段为 null 时的行为

如果源字段因条件不满足（如 `condition = "false"`）而未解码，其值为 `null`，此时衍生字段也会被跳过（保持 `null`）。`read(null)` 不会被调用。

## @Repeatable 多版本支持

自 0.6.0 起，`@DerivedField` 支持 `@Repeatable`，可在同一字段上为不同版本声明不同的派生规则。

版本匹配逻辑与 `@XtreamField` 一致：

1. 精确匹配目标版本 → 使用该注解
2. 无精确匹配，有 `ALL_VERSION` → 使用默认版本兜底
3. 无精确匹配也无默认版本 → 忽略该字段

@[code{31-}](@core-test/io/github/hylexus/xtream/codec/core/docs/MultiVersionDerivedFieldTest.java)

## BitFlag / EnumSetBitTransformer 工具

对于位段枚举场景（如 JT/T 808 状态位解析），框架提供了两个辅助接口/类。

### BitFlag 标记接口

`BitFlag` 定义位段枚举的基本约定：

| 方法            | 返回    | 说明                                                 |
|---------------|-------|----------------------------------------------------|
| `bitOffset()` | `int` | 在当前 raw 值中的起始 bit 位置（从 0 计数）                       |
| `bitLength()` | `int` | 占用的 bit 位数。`1` 为单 bit，`>1` 为 range。默认 `1`          |
| `bitValue()`  | `int` | `bitLength=1` 时固定返回 `1`（置位即匹配）；`>1` 时为排他性取值。默认 `1` |

### EnumSetBitTransformer

`EnumSetBitTransformer<E extends Enum<E> & BitFlag>` 是 `FieldTransformer<Number, Set<E>>` 的抽象基类，提供了 `EnumSet<E>` 与 `long` 之间的双向转换：

- **`read()`**: 遍历枚举常量，单 bit 检查置位，多 bit range 提取后精确匹配 `bitValue()`
- **`write()`**: 遍历 `EnumSet`，单 bit 用 `|=`，多 bit range 先清除目标位再写入（旧位覆盖保护）

@[code{36-}](@core-test/io/github/hylexus/xtream/codec/core/docs/BitFlagDerivedFieldTest.java)

## reverseSource 编码回写

当需要在编码时将衍生字段的值还原到协议字段时，设置 `reverseSource = true`。

@[code{33-}](@core-test/io/github/hylexus/xtream/codec/core/docs/ReverseSourceDerivedFieldTest.java)

## 注意事项

1. **仅派生字段（无 `@XtreamField`）**：如果字段上只有 `@DerivedField` 而没有 `@XtreamField`（或其别名），该字段不参与 ByteBuf 编解码
2. **与 `@XtreamField` 共存**：如果字段上同时有 `@DerivedField` 和 `@XtreamField`，`@XtreamField` 优先——该字段按普通字段编解码，`@DerivedField` 被忽略
3. **`reverseSource` 冲突**：同一 source 不能有多个 `reverseSource=true` 的衍生字段，否则启动时抛出 `IllegalArgumentException`
4. **循环依赖**：衍生字段的 `source` 必须指向已解码的普通字段，不能指向另一个衍生字段，否则启动时抛出 `IllegalStateException`

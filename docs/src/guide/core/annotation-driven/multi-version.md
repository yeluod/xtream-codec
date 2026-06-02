---
date: 2026-01-17
icon: fa-solid fa-tags
tag:
  - 注解
  - 多版本
---

# 多版本支持 <Badge text="0.1.0" type="tip" vertical="top"/>

## 介绍

协议是会进化的。版本升级时，往往只是部分字段发生变化：

- 某字段在旧版本中是 `u8`，新版本变成了 `u16`
- 某字段在旧版本中以 GBK 编码，新版本改用 UTF-8
- 某字段干脆就是新版本才有的
- ……

若为每个版本维护一套独立的 Entity 类，代码将迅速膨胀，版本之间的差异淹没在重复的 boilerplate 中。

`@XtreamField` 的 `version` 属性便是为此而生——在 **同一 Entity 类** 中，通过 **同一字段** 上的多个注解声明，精确控制每个版本下该字段的编解码行为。

## 基本用法

`@XtreamField` 及其所有 Preset 别名注解（如 `@Preset.RustStyle.u8`、`@Preset.JtStyle.Dword` 等），都支持 `version` 属性：

```java
// 完整类定义参见下方“完整示例”
class VersionedEntity {
    @Preset.RustStyle.u32(desc = "用户ID")
    private Long id;

    // 默认版本（匹配所有版本）：UTF-8 编码
    @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8,
            desc = "用户名(UTF-8)")
    // 仅 version=1,2：GBK 编码
    @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8,
            desc = "用户名(GBK)",
            version = {1, 2},
            charset = XtreamConstants.CHARSET_NAME_GBK)
    private String name;
}
```

上例中，`name` 字段声明了两个 `@Preset.RustStyle.str` 注解：

- 第一个没有指定 `version`，默认值 `{ALL_VERSION}`，作为兜底
- 第二个指定了 `version = {1, 2}`，表示版本 1 和 2 使用 GBK 编码

在测试中通过 `doCodecTest(version, ...)` 的版本参数控制使用哪个注解：

```java
class DocTest {
    void test() {
        // version=1 匹配 version={1,2} → GBK 编码
        doCodecTest(1, entity, (source, hex, decoded) -> {
        });

        // version=ALL_VERSION 匹配默认注解 → UTF-8 编码
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
        });
    }
}
```

### ALL_VERSION 常量

`ALL_VERSION` 是 `@XtreamField` 中定义的常量，值为 `Integer.MIN_VALUE`：

```java
@interface XtreamField {
    int ALL_VERSION = Integer.MIN_VALUE;
}
```

未指定 `version` 的注解默认值为 `{ALL_VERSION}`，表示匹配**所有版本**。当没有精确匹配时，框架会使用 `ALL_VERSION` 的注解作为兜底。

## 版本匹配规则

框架在编解码时，按照以下规则为每个字段选择生效的注解：

1. **精确匹配**：`@XtreamField` 或其别名注解的 `version[]` 数组中包含目标版本 → 使用该注解
2. **默认兜底**：无精确匹配时，如果存在 `version = {ALL_VERSION}` 的注解 → 使用该注解
3. **忽略字段**：既无精确匹配也无默认注解 → 该字段在当前版本下被跳过（不编码、不解码）

```
目标版本=2
字段上的注解：
  @Preset.RustStyle.str(version = {1})       → 不匹配
  @Preset.RustStyle.str(version = {ALL_VERSION}) → 匹配（兜底）
  → 最终使用兜底注解
```

```
目标版本=3
字段上的注解：
  @Preset.RustStyle.str(version = {1, 2})    → 不匹配
  @Preset.RustStyle.str(version = {ALL_VERSION}) → 匹配（兜底）
  → 最终使用兜底注解
```

```
目标版本=2
字段上的注解：
  @Preset.RustStyle.str(version = {2})        → 精确匹配
  → 最终使用 version={2} 的注解
```

## @Repeatable 在同一字段上声明多个版本

`@XtreamField` 以及所有 Preset 别名注解（如 `@Preset.RustStyle.str`）都标注了 `@Repeatable`，允许在同一个字段上重复使用。

这是实现多版本的核心机制——每个注解声明一种版本的编解码配置，框架根据运行时版本自动选择。

### 不同编码格式

```java
class MultiVersionEntity {
    // V1: GBK 编码
    @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8,
            charset = XtreamConstants.CHARSET_NAME_GBK,
            version = {1})
    // V2: UTF-8 编码
    @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8,
            charset = XtreamConstants.CHARSET_NAME_UTF8,
            version = {2})
    private String username;
}
```

### 不同类型

```java
class MultiVersionEntity {
    // V1: u8
    @Preset.RustStyle.u8(version = {1})
    // V2: u16
    @Preset.RustStyle.u16(version = {2})
    private int status;
}
```

### 新版本新增字段

新版本的字段在旧版本中不存在，只需不为旧版本声明注解即可：

```java
class MultiVersionEntity {
    // 仅在 version≥2 时编解码
    @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8,
            version = {2})
    private String newField;
}
```

当以 `version=1` 编解码时，`newField` 会被忽略（规则 3）。

## Preset 别名注解中的 version

所有 Preset 别名注解都通过 Spring 的 `@AliasFor` 将 `version` 属性委托给 `@XtreamField#version`：

```java
@interface SomeAlias {
    @AliasFor(annotation = XtreamField.class, attribute = "version")
    int[] version() default {XtreamField.ALL_VERSION};
}
```

这意味着以下写法完全等价：

```java
class VersionedEntity {
    // 直接使用 @XtreamField
    @XtreamField(version = {1} /*, ... 其他属性 ... */)
    private int status;

    // 使用 Preset 别名
    @Preset.RustStyle.u8(version = {1} /*, ... 其他属性 ... */)
    private int status;
}
```

可用的 Preset 注解族见 [@Preset 注解族](./builtin-annotations.md)。

## @DerivedField 中的 version

`@DerivedField` 同样支持 `version` 属性，可在不同版本下使用不同的派生逻辑。详见 [@DerivedField 多版本支持](./derived-field.md#repeatable-多版本支持)。

## 完整示例

以下测试类综合演示了上述所有场景——不同编码格式、不同类型、新版本新增字段，以及 `ALL_VERSION` 兜底行为：

@[code{33-}](@core-test/io/github/hylexus/xtream/codec/core/docs/MultiVersionCodecTest.java)

## 注意事项

1. **`version` 是 `int[]`**：每个注解可声明匹配多个版本，如 `version = {1, 2, 3}`
2. **`ALL_VERSION` 兜底**：建议始终保留一个 `version = {ALL_VERSION}`（即不指定 version）的注解，作为未匹配版本的默认行为
3. **`@Repeatable` 依赖**：同一字段上的多个版本声明依赖注解的 `@Repeatable` 元注解，确保使用正确
4. **版本号由调用方传入**：`EntityCodec.encode(version, ...)` / `EntityCodec.decode(version, ...)` 的第一个参数即是版本号，框架据此匹配

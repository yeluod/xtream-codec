---
date: 2025-03-01
icon: arrows-spin
---

# 实体编解码器

## 介绍

::: tip

这里介绍的是 `io.github.hylexus.xtream.codec.core.EntityCodec`

:::

`EntityCodec` 用来支持基于注解的编解码。这里的注解指的是前面提到的：

- `@XtreamField()`
- `@Preset.RustStyle.xxx()`
- `@Preset.JtStyle.xxx()`
- 自定义注解 ...

本质上 `EntityCodec` 是 `EntityEncoder` 和 `EntityDecoder` 的组合。

## 示例

下面以 `@Preset.RustStyle.xxx()` 风格的注解为例，介绍如何使用 `EntityCodec`:

@[code{36-}](@core-test/io/github/hylexus/xtream/codec/core/EntityCodecTest.java)


---
date: 2025-03-02
icon: arrows-spin
---

# 实体编解码器调试

## 介绍

只需要给 `EntityCodec` 的 `encode` 或 `decode` 方法传入一个 `CodecTracker` 实例即可追踪每个字段的编解码详情。

## 注意

::: danger

`CodecTracker` 的设计目的仅仅是用来调试。 会对编解码性能产生 **严重** 影响。

:::

## 示例

@[code{36-}](@core-test/io/github/hylexus/xtream/codec/core/EntityCodecWithTrackerTest.java)

## 输出效果

```text
[==> 0] CodecTraceNode[kind=ROOT, name='root', path='', javaType='io.github.hylexus.xtream.codec.core.EntityCodecWithTrackerTest$UserEntity', codecType='null', value=null, byteRange=0-23, hex='0000040009e697a0e5908de6b08f080006e4bf9de5af86', status=SUCCESS]
	[==> 1] CodecTraceNode[kind=FIELD, name='id', path='id', javaType='null', codecType='U32FieldCodec', value=1024, byteRange=0-4, hex='00000400', status=SUCCESS]
	[==> 1] CodecTraceNode[kind=LENGTH_FIELD, name='prependLengthField', path='prependLengthField', javaType='null', codecType='u8', value=9, byteRange=4-5, hex='09', status=SUCCESS]
	[==> 1] CodecTraceNode[kind=FIELD, name='name', path='name', javaType='null', codecType='StringFieldCodec', value=无名氏, byteRange=5-14, hex='e697a0e5908de6b08f', status=SUCCESS]
	[==> 1] CodecTraceNode[kind=FIELD, name='age', path='age', javaType='null', codecType='U16FieldCodec', value=2048, byteRange=14-16, hex='0800', status=SUCCESS]
	[==> 1] CodecTraceNode[kind=LENGTH_FIELD, name='prependLengthField', path='prependLengthField', javaType='null', codecType='u8', value=6, byteRange=16-17, hex='06', status=SUCCESS]
	[==> 1] CodecTraceNode[kind=FIELD, name='address', path='address', javaType='null', codecType='StringFieldCodec', value=保密, byteRange=17-23, hex='e4bf9de5af86', status=SUCCESS]
```

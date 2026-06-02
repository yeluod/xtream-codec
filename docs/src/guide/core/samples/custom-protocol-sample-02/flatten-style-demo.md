---
date: 2024-03-24
icon: at
author: hylexus
category:
  - 示例
tag:
  - 编码
  - 解码
---

# 扁平化写法示例

::: tip

- 在阅读本文之前，建议先阅读 [协议格式说明](protocol.md)
- 所谓的 _扁平化写法_ 就是不用单独封装 `Header` 和 `Body`，而是直接将 `Header` 和 `Body` 的数据写在同一个实体类中

:::

## 实体类定义

::: tabs#demo1

@tab Rust 命名风格#rust-style

@[code{30-}](@core-debug/io/github/hylexus/xtream/debug/codec/core/demo02/RustStyleDebugEntity02Flatten.java)

@tab 原始命名风格#raw-style

@[code{32-}](@core-debug/io/github/hylexus/xtream/debug/codec/core/demo02/RawStyleDebugEntity02Flatten.java)

@tab:active JT/T 808 命名风格#jt-style

@[code{30-}](@core-debug/io/github/hylexus/xtream/debug/codec/core/demo02/JtStyleDebugEntity02Flatten.java)

:::

## 反序列化

```java {12,15}
public class EntityDecodeTest {

    @Test
    void testDecode() {
        final EntityCodec entityCodec = EntityCodec.DEFAULT;

        // buffer 中存储的是要反序列化的数据(这里写死用来演示)
        final String hexString = "0200402a01000000000139111122220063000000580000006f01dc9a0707456246231d029a005a240322222633010400001a0a02020058030200595b";
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer().writeBytes(XtreamBytes.decodeHex(hexString));

        try {
            final JtStyleDebugEntity02Flatten entity = entityCodec.decode(JtStyleDebugEntity02Flatten.class, buffer);
            System.out.println(entity);
        } finally {
            buffer.release();
        }
    }
}
```

## 序列化

```java {16,20}
public class EntityEncodeTest {

    @Test
    void testEncode() {
        final EntityCodec entityCodec = EntityCodec.DEFAULT;

        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final JtStyleDebugEntity02Flatten instance = new JtStyleDebugEntity02Flatten();
            // TODO 省略属性赋值
            // instance.setXxx(someValue);
            // instance.setMsgId(someValue);
            // ...

            // 将 instance 的数据序列化到 buffer 中
            entityCodec.encode(instance, buffer);
            // 使用 buffer
            System.out.println(ByteBufUtil.hexDump(buffer));
        } finally {
            buffer.release();
        }
    }
}
```

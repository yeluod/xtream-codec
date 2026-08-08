---
date: 2026-08-08
tag:
  - 编码长度
  - 注解
---

# @EncodedLength

## 介绍 <Badge text="0.7.0" type="tip" vertical="top"/>

`@EncodedLength` 用于标记实体中的独立长度字段。编码时，框架先写入占位值，再根据指定字段范围的 **实际编码字节数** 回填长度。

它适合下面这类协议结构：

```text
消息头 | 消息体长度 | 消息体 | 校验码
```

消息体可能包含多个字段、条件字段、嵌套对象或继承自子类。手动维护长度容易遗漏，
`@EncodedLength` 可以直接按照最终写入 `ByteBuf` 的字节数计算。

::: tip 与前置长度字段的区别

- [`prependLengthFieldType`](./annex/prepend-length-field.md) 描述的是某个字符串、数组、内嵌对象 或 列表自身携带的前置长度；
- `@EncodedLength` 描述的是实体中的一个独立字段，可以统计多个字段组成的编码范围。

:::

## 范围语义

`@EncodedLength` 使用 `[from, until)` 左闭右开区间：

- `from` 指向第一个被统计的字段，包含该字段
- `until` 指向统计结束后的第一个字段，不包含该字段
- 字段位置按照继承、`order` 排序后的最终编码顺序判断，而不是只看源码声明顺序（实际上 `Java` 从没说过会保证字段声明顺序）

| 写法                                                   | 统计范围                                         |
|--------------------------------------------------------|--------------------------------------------------|
| `@EncodedLength`                                       | 从(当前)长度字段之后开始，一直到实体编码结束     |
| `@EncodedLength(until = "checksum")`                   | 从(当前)长度字段之后开始，到 `checksum` 之前结束 |
| `@EncodedLength(from = "payload")`                     | 从 `payload` 开始，一直到实体编码结束            |
| `@EncodedLength(from = "payload", until = "checksum")` | 从 `payload` 开始，到 `checksum` 之前结束        |

## 继承场景示例

下面的公共父类定义消息头、消息体长度和校验字段。`dataLength` 位于消息体之前，`checksum` 位于最终编码顺序的末尾：

@[code{25-59}](@core-debug/io/github/hylexus/xtream/debug/codec/core/demo005/BaseMessage.java)

- `from` 保持默认空字符串，因此范围从 `dataLength` 后面立即开始；
- `until = "checksum"` 表示 `checksum` 本身不计入长度。

具体消息只需要继承父类并声明自己的消息体字段：

@[code{28-64}](@core-debug/io/github/hylexus/xtream/debug/codec/core/demo005/DemoMessage005.java)

父类不需要知道子类有哪些字段。

元数据注册时，框架会合并继承字段并按照 `order` 排序， 因此 `time`、`serialNumber`、`iccid`、`bmsBatteryCount` 和 `bmsBatteries`
都会自动纳入 `dataLength` 的统计范围。

该示例的消息体长度为：

| 字段              | 编码长度                    |
|-------------------|-----------------------------|
| `time`            | 6 字节 BCD 时间             |
| `serialNumber`    | 2 字节 u16                  |
| `iccid`           | 20 字节定长字符串           |
| `bmsBatteryCount` | 1 字节前置长度 + 1 字节内容 |
| `bmsBatteries`    | 1 个 24 字节记录            |
| **合计**          | **54 字节**                 |

对应测试会同时检查编码缓冲区中的长度字段和解码后的 `dataLength`：

@[code{34-82}](@core-debug-test/io/github/hylexus/xtream/debug/codec/core/demo005/DemoMessage005Test.java)

## 编解码行为

### 编码

编码长度字段时，框架会：

1. 在长度字段位置写入 `0` 作为占位值
2. 记录范围开始时的 `writerIndex`
3. 正常编码范围内的字段
4. 在 `until` 字段之前或实体编码结束时，通过 `writerIndex` 差值计算长度
5. 使用 `ByteBuf#setByte`、`setShort` 或 `setInt` 原地回填

- 计算过程不会复制消息体，也不会进行第二次编码。
- 条件表达式不成立或值为 `null` 的字段没有写入字节， 因此也不会被计入长度。

长度字段在源对象中的原始值 **不会被用于编码** ，框架也不会修改源对象；重新解码后，长度字段会得到实际编码值。

### 解码

解码时，`@EncodedLength` 字段按照普通无符号整数字段读取。

`@EncodedLength` **不会** 自动限制后续字段的解码范围。后续字符串、数组、列表、内嵌对象 或 动态字段 仍需通过 自身的固定长度、长度表达式或其他字段编解码配置确定读取方式。

## 字段要求

当前版本有以下限制：

1. 长度字段必须同时使用 `@XtreamField` 或其(内置/自定义)别名注解
2. 长度字段仅支持大端无符号 `u8`、`u16`、`u32`
3. 一个实体的最终字段列表中只能存在一个 `@EncodedLength`
4. `from`、`until` 指向的字段必须存在，并且位于长度字段之后
5. 同时声明 `from` 和 `until` 时，`from` 必须位于 `until` 之前
6. `@EncodedLength` 不能标记在仅作为 `@DerivedField` 的字段上

不满足这些条件时，框架会在构建实体元数据时抛出 `IllegalArgumentException`，而不是等到编码中途失败。

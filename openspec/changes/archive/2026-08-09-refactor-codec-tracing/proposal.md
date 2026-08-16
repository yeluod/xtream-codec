## Why

现有 `CodecTracker` 同时承担埋点收集、树结构模型、JSON 输出和文本访问职责，导致调试能力难以扩展，尤其缺少稳定的字节范围、字段路径和错误诊断信息，无法很好支撑网页版报文编解码调试页面。

这次变更希望在保持现有面向用户 API 兼容的前提下，重构调试埋点系统，让生产编解码路径继续保持轻量，调试路径产出可被程序消费的结构化 trace。

## What Changes

- 保持 `FieldCodec`、`EntityCodec`、`EntityEncoder`、`EntityDecoder` 现有公开方法签名和调用方式兼容。
- 引入新的结构化编解码 trace 模型，记录编解码方向、字段路径、节点类型、Java 类型、Codec 类型、值摘要、字节范围、十六进制片段和诊断信息。
- 将 `CodecTracker` 调整为新埋点系统的兼容门面，继续支持现有 `visit` 直接访问能力。
- 调试路径继续通过 `serializeWithTracker`、`deserializeWithTracker`、`encodeWithTracker`、`decodeWithTracker` 触发埋点；生产路径不依赖 trace 模型。
- 为 Web 调试功能提供稳定 DTO/视图模型，支持字段树和报文字节范围互相定位。
- 对旧 Span 模型提供兼容迁移策略，避免现有 dashboard、测试或用户代码在一次变更中被强制迁移。
- 不引入破坏性 API 变更。

## Capabilities

### New Capabilities

- `codec-tracing`: 定义结构化编解码埋点、兼容访问和 Web 调试数据模型能力。

### Modified Capabilities

- 无。

## Impact

- 影响 `xtream-codec-core` 中的 `CodecTracker`、`FieldCodec` 调试默认方法、实体编码器/解码器的 tracker 分支，以及集合、Map、嵌套实体、长度字段等 FieldCodec 的调试路径。
- 现有公开 API 必须源码兼容；如新增公开 API，需要按当前版本规则添加 `@since 0.9.0`。
- JT808 dashboard 已迁移为消费 `CodecTraceView`，不再保留旧 `RootSpan` 输出模型。
- 需要补充覆盖字节范围、字段路径、异常诊断、`visit` 兼容、JSON 输出稳定性的测试。

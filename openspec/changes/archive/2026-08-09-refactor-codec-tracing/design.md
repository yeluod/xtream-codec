## Context

当前 `CodecTracker` 相关实现把“埋点收集器”“Span 树模型”“JSON 输出模型”“控制台 visit 展示”混在一起。现有数据主要依赖局部 `hexString`，缺少稳定的 byte range、字段路径、方向、诊断和 Web UI 需要的双向定位索引。

同时，`FieldCodec`、`EntityCodec`、`EntityEncoder`、`EntityDecoder` 已经暴露了带 tracker 的公开 API，用户代码可能直接依赖这些方法和 `CodecTracker#visit`。本设计约束是：公开入口保持兼容，重构发生在入口背后。

## Goals / Non-Goals

**Goals:**

- 保持现有面向用户的 tracker 编解码 API 源码兼容。
- 将 `CodecTracker` 重构为新的结构化 trace 系统的兼容门面。
- 让调试路径产出稳定的结构化数据：树、路径、byte range、hex、值摘要、诊断信息。
- 支持直接 `visit` 访问，也支持 Web 调试页面消费 JSON-friendly trace view。
- 保持生产路径不依赖 trace 模型，不为未启用 tracker 的编解码分配 trace 节点。
- 删除旧 `RootSpan`/Span 调试模型，现有调试输出统一迁移到 `CodecTrace`/`CodecTraceView`。
- 为新的 React + HeroUI 调试前端提供后端数据模型，不再兼容旧 Vue 调试前端。

**Non-Goals:**

- 不在本变更里重写 JT808/JT1078 的完整 Web 调试页面；本变更只建立新前端项目和 trace 消费边界。
- 不改变实体注解编解码的语义。
- 不保留旧 `RootSpan`/Span 类和旧前端数据结构。

## Decisions

### Decision 1: 保留公开入口，替换内部模型

现有公开入口继续存在：

- `FieldCodec#serializeWithTracker(...)`
- `FieldCodec#deserializeWithTracker(...)`
- `EntityCodec` 带 `CodecTracker` 的重载
- `EntityEncoder#encodeWithTracker(...)`
- `EntityDecoder#decodeWithTracker(...)`
- `CodecTracker#visit(...)`

内部引入新的 trace 模型。`CodecTracker` 不再维护旧 Span 树，而是新的 trace recorder 门面：

```text
CodecTracker
    ├── CodecTraceRecorder  // 编解码过程中记录事件
    ├── CodecTrace          // 一次编解码完成后的结构化结果
    ├── CodecTraceView      // 面向 Web 调试页面的 JSON-friendly 视图
    └── visit(...)          // 基于 CodecTraceNode 的树遍历
```

这样可以在不破坏用户调用方式的情况下，重建后端埋点能力。

**Alternative considered:** 继续扩展旧 `BaseSpan` 子类。放弃原因是旧模型把节点类型绑定到 Java 继承层级，字段 index 和 byte offset 命名容易混淆，也缺少 Web 调试需要的统一数据合同。

### Decision 2: 使用统一节点模型表达字段树

新的核心节点使用统一模型表示：

```text
CodecTraceNode
    id
    parentId
    kind
    name
    path
    javaType
    codecType
    value
    byteStart
    byteEnd
    hex
    status
    attributes
    diagnostics
```

`kind` 表达 root、普通字段、嵌套实体、集合、集合项、Map、Map entry、长度字段、虚拟节点等语义。集合项和 Map entry 的序号放在 `attributes` 或明确的 `itemIndex` 字段中，不再复用 `offset` 表示序号。

**Alternative considered:** 为每种节点继续建一个公开类。放弃原因是 Web DTO 和 visitor 都更需要稳定的字段集合，而不是复杂继承树。

### Decision 3: byte range 是一等数据

调试路径在进入和退出节点时记录当前 `readerIndex` 或 `writerIndex`，并把每个节点的 byte range 转换成相对于本次 trace root 的偏移。

```text
rootStart = buffer readerIndex/writerIndex before tracked operation
node.byteStart = nodeAbsoluteStart - rootStart
node.byteEnd = nodeAbsoluteEnd - rootStart
```

Web UI 使用 byte range 做高亮，不再根据局部 hex 文本反查原始报文。

**Alternative considered:** 继续只保存 `hexString`。放弃原因是重复字节片段无法唯一定位，且无法支持“点字节反查字段”。

### Decision 4: recorder 使用显式栈，不使用临时全局状态

新的 recorder 维护节点栈：

```text
enterNode(...)
recordValue(...)
recordDiagnostic(...)
exitNode(...)
```

集合、Map、嵌套实体通过 enter/exit 表达层级，不再依赖 `current` 指针加临时字段名、临时 map item 类型这类隐式状态。

**Alternative considered:** 保留 `current` 指针并补更多临时字段。放弃原因是调用顺序约束隐蔽，复杂 FieldCodec 更容易写错。

### Decision 5: Web trace view 与核心 trace 分离

核心 `CodecTrace` 面向 Java 访问和测试，Web DTO 面向 JSON 输出：

```text
CodecTrace
    └── CodecTraceView.from(trace)
            ├── payloadHex
            ├── nodes
            ├── byteSegments / indexes
            └── diagnostics
```

这样核心模型不被前端展示细节污染，同时 Web 页面能直接得到双向定位所需的数据。

**Alternative considered:** 让前端直接消费核心 trace。放弃原因是核心模型可以服务 Java API，而 Web 需要更扁平、更稳定的 JSON 结构和索引。

### Decision 6: 核心 trace view 放在 core，协议页面后续接入

`CodecTrace`、visitor 和基础 `CodecTraceView` 放在 `xtream-codec-core`，因为它们描述的是实体编解码调试结果，不依赖具体协议。协议模块可以在后续基于基础 view 增加帧头、校验、转义、分包等协议层信息。

**Alternative considered:** 把 Web DTO 放在 dashboard 模块。放弃原因是这样会让通用私有协议调试也依赖 JT808 dashboard 的展示模型，不利于协议无关复用。

### Decision 7: 删除旧 Span 模型

旧 `RootSpan`、`BaseSpan`、`BasicFieldSpan`、`NestedFieldSpan`、`CollectionFieldSpan`、`MapFieldSpan` 等类在本变更中删除。核心埋点逻辑、测试、JT808 dashboard DTO 和响应编码调试信息统一迁移到 `CodecTrace`/`CodecTraceNode`/`CodecTraceView`。

**Alternative considered:** 保留旧 Span 并提供 adapter。放弃原因是旧前端不再演进，继续保留 adapter 会让新 trace 设计长期背负旧模型。

### Decision 8: 新建 React + HeroUI 调试前端，旧 Vue 调试前端停止演进

旧 `debug/jt/jt-808-server-spring-boot-starter-debug-ui` 不再作为 trace UI 的兼容目标。新的 trace 调试页面放入现有 `ext/jt/jt-808-server-dashboard-ui` React、Vite、TypeScript、Tailwind CSS 和 HeroUI 工程，直接消费 `CodecTraceView`。

**Alternative considered:** 在旧 Vue 项目里继续改造。放弃原因是旧页面深度依赖 `spanType` 和 `hexString`，会把新 trace view 的数据合同拖回旧结构。

## Risks / Trade-offs

- **二进制兼容风险** → 保持 `FieldCodec`、`EntityCodec`、`EntityEncoder`、`EntityDecoder` 的 tracker 入口不变；旧 Span 类作为调试实现细节删除。
- **trace 数据过大** → 默认只在 tracker 路径收集；value 使用摘要或 JSON-friendly 表示，避免无界展开大对象。
- **异常路径难以完整退出节点栈** → recorder 提供 failure/close 语义，在异常时标记当前节点并保留已记录数据。
- **复杂 FieldCodec 埋点遗漏** → 先覆盖基础字段、嵌套实体、集合、Map、TLV、长度字段，再迁移协议 dashboard。
- **旧 dashboard DTO 迁移成本** → DTO 直接改为 `CodecTraceView`，同时用编译和现有 JT808 测试确认后端引用完成迁移。

## Migration Plan

1. 建立新的 trace 数据模型、recorder、visitor 和 Web view DTO。
2. 改造 `CodecTracker`，让它委托 recorder，并保持基于 `CodecTraceNode` 的 visit 调用方式。
3. 改造基础 FieldCodec tracker 默认方法，记录 byte range、value、codec 类型和字段路径。
4. 逐步改造嵌套实体、集合、Map、TLV、长度字段等复杂 codec 的 tracker 分支。
5. 删除旧 Span 类，迁移 core 测试和 JT808 dashboard/response encoder 到 `CodecTraceView`。
6. 新建 React + HeroUI 调试前端项目，建立直接消费 `CodecTraceView` 的页面骨架。
7. 增加兼容、trace 结构、byte range、错误诊断、JSON 输出和前端构建测试。

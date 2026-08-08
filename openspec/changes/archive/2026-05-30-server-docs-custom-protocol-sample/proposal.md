## Why

目前文档中 `xtream-codec-server-reactive` 模块缺少一个完整的从零到一的服务端 quick-start 示例。已有文档（`custom-protocol-sample-01/02`）仅覆盖了**核心编解码层**的实体注解使用，而**服务端**的自定义注解 + HandlerMapping 机制只有概念说明，缺少可运行、可追踪的完整示例。

用户想基于该框架实现自己的私有协议时，只能参考 JT/T 808 扩展（太重），没有轻量级的入门指引。这个 gap 导致上手成本高。

## What Changes

- 在 `docs/src/guide/server/samples/` 下新增一个 server quick-start 示例，包含一份虚构的极简私有协议（X-IoT Demo Protocol）
- 配套一个新建的 Gradle module `quick-start/custom-annotation-server/`，包含可运行的入口（TCP 服务端、自定义注解、HandlerMapping、Handler 方法）
- 撰写协议说明文档（`protocol.md`）
- 撰写代码示例文档（参考现有示例结构）
- 不修改任何已有代码或文档

## Capabilities

### New Capabilities

- `x-iot-demo-protocol`: 一个虚构的私有 IoT 协议定义，类似于 JT/T 808 但极简化——仅含 msgType 单维分发，无版本、无分包、无加密、无终端地址
- `custom-annotation-server`: 在 server-reactive 模块中自定义注解 `@DemoMessageHandler` + `@DemoMessageMapping`，并基于 `AbstractSimpleXtreamRequestMappingHandlerMapping` 实现按 msgType 分发的 HandlerMapping
- `server-quickstart-module`: 新增一个独立 Gradle module `quick-start/custom-annotation-server/`，包含可运行的 TCP 服务端入口，演示完整的服务端启动、Handler 方法编写、多种参数注入

### Modified Capabilities

- 无

## Impact

- 仅新增文件和目录，不影响已有代码
- 新增 `quick-start/custom-annotation-server/` module，需要在根 `settings.gradle.kts` 中注册
- 新增 `docs/src/guide/server/samples/custom-demo-protocol/` 文档目录

# server-quickstart-module Specification

## Purpose
TBD - created by archiving change server-docs-custom-protocol-sample. Update Purpose after archive.
## Requirements
### Requirement: 新增 quick-start Gradle module

SHALL 在 `quick-start/custom-annotation-server/` 下新增 Gradle module，并注册到项目根 `settings.gradle.kts`。

Module 的 `build.gradle.kts` 依赖：
- `xtream-codec-server-reactive`（核心服务端库）
- `logback-classic`（日志实现）

不使用 Spring Boot，保持最简依赖。

#### Scenario: Module 构建成功
- **WHEN** 执行 `./gradlew :quick-start:custom-annotation-server:build`
- **THEN** 构建成功，生成可执行 jar

### Requirement: 可运行的 TCP 服务端入口

`XtreamDemoServerApp.java` SHALL 包含 `main` 方法，启动 TCP 服务器：

1. 使用 `XtreamServerBuilder.newTcpServerBuilder()` 构建服务端
2. 注册 `DemoMessageHandlerMapping` 为 HandlerMapping
3. 注册内置的 HandlerAdapter（`enableBuiltinHandlerAdapters(EntityCodec.DEFAULT)`）
4. 注册内置的 HandlerResultHandler
5. 添加 `LengthFieldBasedFrameDecoder` 到 pipeline
6. 添加 `LoggingXtreamFilter` 用于调试日志
7. 监听端口 8888

#### Scenario: 服务端启动成功
- **WHEN** 运行 `main` 方法
- **THEN** 服务端在 8888 端口启动，日志输出 "server started" 信息

### Requirement: 包含至少 3 个 Handler 方法

一个 `@DemoMessageHandler` 类中 SHALL 至少包含 3 个 Handler 方法：

| msgType | 方法名 | 参数 | 说明 |
|---------|--------|------|------|
| 0x01 | handleHeartbeat | 无参 | 演示最简用法 |
| 0x11 | handleTemperature | `@DemoBody TemperatureReport report` | 演示 body 参数注入 |
| 0x81 | handleRegister | `XtreamExchange exchange, @DemoBody DeviceRegisterRequest req` | 演示 exchange 和 body 联合注入 |

#### Scenario: 心跳 Handler 可被调用
- **WHEN** 通过端口发送 heartbeat 报文（`12 34 56 78 01 00 00`）
- **THEN** handleHeartbeat 方法被执行，日志打印心跳记录

#### Scenario: 温湿度 Handler 可被调用
- **WHEN** 通过端口发送温湿度上报报文
- **THEN** handleTemperature 方法被执行，TemperatureReport 实体解析正确

### Requirement: 配套文档

文档 SHALL 位于 `docs/src/guide/server/samples/custom-demo-protocol/`，结构参考 `custom-protocol-sample-01`：

```
custom-demo-protocol/
├── README.md          # 示例索引
├── protocol.md         # 协议格式说明
└── handler-demo.md     # 自定义注解 + Handler 代码示例
```

#### Scenario: 文档包含协议结构表
- **WHEN** 阅读 `protocol.md`
- **THEN** 看到报文头字段表、消息类型列表、每种类型的消息体字段说明

#### Scenario: 文档包含完整代码示例
- **WHEN** 阅读 `handler-demo.md`
- **THEN** 看到自定义注解定义、HandlerMapping 实现、Handler 方法代码


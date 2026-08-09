## MODIFIED Requirements

### Requirement: 可运行的 TCP 服务端入口

`XtreamDemoServerApp.java` SHALL 包含 `main` 方法，启动 TCP 服务器：

1. 使用协议无关的 `XtreamServers.tcp()` 构建服务端
2. 注册 `DemoMessageHandlerMapping` 为 HandlerMapping
3. 通过 dispatcher 配置启用内置的 HandlerAdapter 和 HandlerResultHandler（`enableBuiltinHandlers(EntityCodec.DEFAULT)`）
4. 添加 `LengthFieldBasedFrameDecoder` 到 pipeline
5. 添加 `LoggingXtreamFilter` 用于调试日志
6. 监听端口 9527

#### Scenario: 服务端启动成功
- **WHEN** 运行 `main` 方法
- **THEN** 服务端在 9527 端口启动，日志输出 "server started" 信息

#### Scenario: 示例不直接使用低层 Server Builder
- **WHEN** 阅读 `XtreamDemoServerApp.java` 的服务端启动代码
- **THEN** 示例使用 Generic TCP Server Builder 作为主入口，而不是直接使用低层 TCP Server Builder

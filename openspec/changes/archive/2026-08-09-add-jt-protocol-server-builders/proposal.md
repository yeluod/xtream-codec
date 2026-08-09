## Why

`XtreamServers` 已经提供协议无关的 TCP/UDP Server Builder，但 JT/T 808 和 JT/T 1078 的服务端自动配置仍需要手动拼装低层 `XtreamServerBuilder`、handler adapter、pipeline、loop resources 和协议默认处理逻辑。

对已经确定协议的使用方来说，Server Builder 应该表达协议语义，而不是暴露通用 Netty 组装细节；同时 core 的 `XtreamServers` 必须继续保持协议无关。

## What Changes

- 在 JT/T 808 reactive starter 模块中新增协议专用 server builder 入口，用于构建指令服务器和附件服务器的 TCP/UDP `XtreamServer`。
- 在 JT/T 1078 reactive starter 模块中新增协议专用 server builder 入口，用于构建 TCP/UDP 音视频服务端。
- 协议 builder SHALL 复用 `XtreamServers` 或现有低层 builder 作为内部实现底座，但对调用方暴露协议相关命名和默认项。
- Spring Boot 自动配置 SHALL 迁移到协议 builder，保持已有 bean 名称、条件装配、默认端口、pipeline、handler adapter、loop resources 和用户 customizer 覆盖能力不变。
- `XtreamServers` 不新增 JT/T 808 或 JT/T 1078 入口，继续作为协议无关 API。
- 不移除现有低层 `XtreamServerBuilder` 和现有 handler adapter builder。

## Capabilities

### New Capabilities

- `jt-808-server-builder`: JT/T 808 模块内的协议专用 Server Builder，用于指令服务器和附件服务器的 TCP/UDP 构建。
- `jt-1078-server-builder`: JT/T 1078 模块内的协议专用 Server Builder，用于音视频 TCP/UDP 服务端构建。

### Modified Capabilities

- `generic-server-builder`: 明确 core generic builder 不暴露具体 JT 协议入口，协议专用 builder 位于各自 ext 模块。

## Impact

- Affected modules:
  - `ext/jt/jt-808-server-spring-boot-starter-reactive`
  - `ext/jt/jt-1078-server-spring-boot-starter-reactive`
  - Potential quick-start/debug modules that show direct low-level server construction
- Public API:
  - Add JT/T 808 and JT/T 1078 protocol builder facade APIs in their protocol modules.
  - Keep `XtreamServers` protocol-neutral.
  - Keep existing low-level builders source-compatible.
- Tests:
  - Add focused builder tests for default customizer order, one-time handler binding, protocol defaults, and auto-configuration migration behavior.

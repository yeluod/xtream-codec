## Context

当前已有三层 server 构建能力：

- `XtreamServerBuilder`：低层高级定制 API，直接收集 `TcpNettyServerCustomizer` / `UdpNettyServerCustomizer`。
- `XtreamServers`：协议无关的 generic facade，表达 TCP/UDP、bind、pipeline、dispatch、customize。
- JT/T 808 和 JT/T 1078 Spring Boot 自动配置：仍直接使用 `XtreamServerBuilder` 拼装协议 handler、pipeline、loop resources 和用户 customizers。

本 change 要补的是第三层：已经确定协议时，调用方应该看到协议术语，而不是通用 Netty 组装术语。

## Goals / Non-Goals

**Goals:**

- 在 808 模块中提供 `Jt808Servers` 风格的入口。
- 在 1078 模块中提供 `Jt1078Servers` 风格的入口。
- Spring Boot 自动配置迁移到协议 builder，同时保持已有 bean 名称、条件装配和运行行为。
- 让协议 builder 内部复用现有 handler adapter、resource factory、generic builder 或低层 builder，避免重写协议处理逻辑。
- 保持 core `XtreamServers` 协议无关，不引入 808/1078 类型。

**Non-Goals:**

- 不重构 JT/T 808 或 JT/T 1078 的协议编解码逻辑。
- 不改变已有配置属性结构和默认值。
- 不移除 `XtreamServerBuilder` 或现有 handler adapter builder。
- 不把 808 和 1078 的 API 聚合到 `XtreamServers` 中。

## Decisions

### 1. 协议入口放在各自 ext 模块

建议新增入口：

```text
io.github.hylexus.xtream.codec.ext.jt808.spec.Jt808Servers
io.github.hylexus.xtream.codec.ext.jt1078.spec.Jt1078Servers
```

备选方案是放到 `xtream-codec-server-reactive` 的 `XtreamServers`，但这会破坏刚建立的协议无关边界，也会让 core 模块依赖或暴露 JT 扩展概念。

### 2. JT/T 808 builder 按 server role 拆入口

建议 API 语义：

```text
Jt808Servers.instructionTcp()
Jt808Servers.instructionUdp()
Jt808Servers.attachmentTcp()
Jt808Servers.attachmentUdp()
```

808 的差异不只是 TCP/UDP，还包括指令服务器和附件服务器两个 role。把 role 放进入口名，比通过一个通用 builder 再传 enum 更直接，也避免调用链里出现大量条件分支。

每个 builder 负责收集：

- name
- bind host/port
- protocol handler adapter
- loop resources / prefer native
- TCP pipeline 或 UDP datagram 相关默认项
- idle-session 相关默认项
- user transport customizers

Spring Boot 自动配置可以把现有 bean 依赖注入后交给 builder，不再手写低层 customizer 链。

### 3. JT/T 1078 builder 按 transport 拆入口

建议 API 语义：

```text
Jt1078Servers.tcp()
Jt1078Servers.udp()
```

1078 当前没有 808 那样的 instruction/attachment role，主要差异来自 TCP/UDP：

- TCP 需要帧分隔、请求解码、心跳/空闲处理。
- UDP 主要依赖 datagram handler、SIM 转换、session、scheduler 和 request publisher。

因此按 transport 拆入口足够清晰。

### 4. 内部优先复用 `XtreamServers`，必要时保留低层 builder

协议 builder 的公开 API 不应暴露低层 customizer 链作为主要路径，但内部可以复用：

- `XtreamServers.tcp()` / `XtreamServers.udp()` 处理 name、bind、pipeline、customize、build。
- 现有 `XtreamServerBuilder` 作为兼容或难以表达的 fallback。
- 现有协议 handler adapter builder 继续负责协议 dispatch 细节。

如果某个协议场景需要直接 `server.handle(protocolHandlerAdapter)`，协议 builder 可以在内部调用 generic builder 的 `customize(...)`，不强行复用 generic `dispatch(...)`。`dispatch(...)` 更适合私有协议的 annotation-driven handler 组装；1078 这类媒体流处理并不天然匹配这个抽象。

### 5. 用户 customizers 保持最后应用

现有自动配置里用户 `TcpNettyServerCustomizer` / `UdpNettyServerCustomizer` 在默认 bind、handler、pipeline、loop resources 之后追加。协议 builder 需要保持这个顺序，让用户仍可覆盖或补充低层 server 设置。

测试应覆盖 customizer 顺序，而不只覆盖 builder 返回类型。

## Risks / Trade-offs

- API 数量增加 → 通过明确 role/transport 命名控制复杂度，避免一个万能 builder 暴露大量可空配置。
- 自动配置迁移可能改变 customizer 顺序 → 用单元测试或上下文测试锁住默认 customizer 数量和顺序。
- 808 附件服务器和 1078 媒体服务器的 pipeline 语义不同 → 不抽象成一个共享 JT base builder，先让每个协议 builder 封装自己的默认项。
- 协议 builder 如果过度暴露底层 Netty 配置，会回到抽象负担问题 → 公开 API 只保留必要高级入口，低层修改通过 `customize(...)` 留出口。

## Migration Plan

1. 新增协议 builder facade 和对应 builder 类型。
2. 给 builder 添加针对默认 customizer 顺序、handler 绑定、pipeline 默认项的单元测试。
3. 将 JT/T 808 自动配置中的 server bean 创建逻辑迁移到 `Jt808Servers`。
4. 将 JT/T 1078 自动配置中的 server bean 创建逻辑迁移到 `Jt1078Servers`。
5. 保持 bean name、条件注解、配置属性和现有 handler adapter bean 不变。
6. 运行 808/1078 starter 模块测试、相关 quick-start 构建和全量格式检查。

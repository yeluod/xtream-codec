## Context

当前核心服务端模块通过 `XtreamServerBuilder` 暴露低层 TCP/UDP Server Builder 的静态工厂。构建私有协议服务时，用户需要同时组合该 API、`XtreamNettyHandlerAdapter.newTcpBuilder()` 或 `newUdpBuilder()`，以及 Reactor Netty 的 customizer lambda。

本变更在核心服务端模块中新增一层中层 Generic API。它用于改善私有协议的默认构建路径，同时保留高级用户和扩展模块已经使用的低层 API。

## Goals / Non-Goals

**Goals:**
- 在 `xtream-codec-server-reactive` 中引入协议无关的 `XtreamServers.tcp()` 和 `XtreamServers.udp()` 风格入口。
- 让常见私有协议构建路径按 name、bind、pipeline、dispatch、customize、build 的顺序表达。
- 保持 TCP 和 UDP 显式区分，不强行抹平传输层差异。
- 内部复用已有 Server Builder 和 Handler Adapter Builder。
- 保留现有 `XtreamServerBuilder` 的行为和兼容性。

**Non-Goals:**
- 本变更不在核心模块中添加 JT/T 808 或 JT/T 1078 Builder API。
- 本变更不删除或废弃 `XtreamServerBuilder`。
- 本变更不重设计 `XtreamServer`、服务生命周期、Session 管理或 Handler 分发内部机制。
- 本变更不把所有扩展模块的服务装配都替换为协议专用 Builder。

## Decisions

### Decision: 使用 `XtreamServers` 作为 Generic 公共入口

在核心服务端模块中新增一个协议无关的 facade，提供 TCP 和 UDP 入口方法。该 facade 应位于扩展模块之外，不引用 JT/T 808、JT/T 1078 或任何其他具体协议。

Rationale:
- `XtreamServerBuilder` 当前位于 `impl` 包中，读起来更像低层实现工具。
- `XtreamServers.tcp()` 和 `XtreamServers.udp()` 更能表达用户正在构建通用服务器。

Alternative considered:
- 重命名或替换 `XtreamServerBuilder`。这会带来不必要的兼容性风险，同时仍然无法解决缺少中层 API 的问题。

### Decision: 保留 `XtreamServerBuilder` 作为低层逃生口

现有低层 Builder 继续保留，供需要直接控制 customizer 顺序和 Reactor Netty Server 构建过程的用户使用。

Rationale:
- 现有 quick-start、debug 和扩展模块自动配置已经在使用它。
- 在协议专用 API 独立演进期间，扩展模块仍可能需要低层装配能力。

Alternative considered:
- 删除 `XtreamServerBuilder`。这会在新 API 尚未证明能覆盖高级场景前，强迫所有低层用户迁移到新 API。

### Decision: Generic Builder 暴露 transport、pipeline、dispatch 和 customize 概念

Generic Builder 应暴露一组较小的方法：

```java
name(String name)
bind(String host, int port)
pipeline(...)
dispatch(...)
customize(...)
build()
```

TCP 和 UDP Builder 可以在需要时使用传输层特定的函数类型，但公共词汇应保持一致。

Rationale:
- 私有协议用户通常按地址绑定、帧/管道设置和请求分发来理解服务构建。
- `addServerCustomizer` 这个名字准确，但对常见路径来说过于偏实现细节。

Alternative considered:
- 为常见 frame decoder 暴露更多便利方法。后续可能有价值，但第一版过早添加会导致 API 猜测具体协议需求。

### Decision: Dispatcher Builder 包装已有 Handler Adapter Builder

嵌套的 dispatch API 应收集 HandlerMapping、HandlerAdapter、ResultHandler、Filter、ExceptionHandler、SessionManager，并提供 `enableBuiltinHandlers(EntityCodec)` 快捷配置。内部根据 TCP 或 UDP 创建对应的 `XtreamNettyHandlerAdapter`。

Rationale:
- 当前用户需要记住内置处理能力同时涉及 builtin HandlerAdapter 和 builtin ResultHandler。
- 嵌套的 Dispatch Builder 可以把请求分发关注点和 transport/pipeline 关注点分开。

Alternative considered:
- 要求用户传入已经构建好的 `XtreamNettyHandlerAdapter`。这可以通过低层 customization 继续支持，但无法减轻常见场景负担。

### Decision: 协议专用 API 保持在扩展模块中

未来 JT/T 808 和 JT/T 1078 Builder API 应位于各自模块中，而不是挂在 `XtreamServers` 下。

Rationale:
- 核心服务端模块应保持协议无关。
- 扩展模块拥有协议默认值，例如 instruction/attachment server、frame decoder、SessionManager 和协议专用 handler。

Alternative considered:
- 添加 `XtreamServers.jt808()` 或 `XtreamServers.jt1078()`。这会让核心模块变成协议注册中心，并反转模块所有权。

## Risks / Trade-offs

- Public API 名称发布后可能难以修改 -> 第一版保持小而稳定，避免 speculative convenience methods。
- Generic Builder 可能和低层 Builder 存在概念重复 -> 将它定位为委托式中层 facade，而不是低层 Builder 的替代品。
- TCP 和 UDP 的 pipeline customization 存在差异 -> 保持 TCP/UDP 入口分离，并在实现中使用必要的传输层特定细节。
- Dispatcher Builder 对注解驱动协议仍可能偏复杂 -> 通过 custom protocol quick-start 更新验证 API 是否自然易读。

## Migration Plan

1. 添加 Generic API，同时保持现有 Builder 不变。
2. 将 custom/private protocol quick-start 更新为使用 Generic TCP Builder。
3. 扩展模块自动配置继续使用低层 Builder，除非 Generic API 能明显简化局部装配。
4. 在文档中说明 `XtreamServerBuilder` 是低层高级 API，私有协议优先使用 `XtreamServers.tcp()/udp()`。

回滚方式直接：现有低层 Builder 不变；如有需要，可以将受影响示例恢复到之前的 Builder 用法。

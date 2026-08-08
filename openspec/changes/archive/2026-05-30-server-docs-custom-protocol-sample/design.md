## Context

`xtream-codec-server-reactive` 是一个与协议无关的异步 TCP/UDP 服务端框架，内置了 `AbstractSimpleXtreamRequestMappingHandlerMapping` 用于在非 Spring 环境下快速实现注解驱动的 Handler 分发。但现有文档仅通过 JT/T 808（太重）作为参考例子，缺少轻量级的教学级示例。

本设计围绕一个虚构的私有协议 **X-IoT Demo Protocol**，构建完整的服务端 quick-start 示例，包含自定义注解、HandlerMapping、Handler 方法、服务端启动等全链路演示。

## Goals / Non-Goals

**Goals:**
- 定义一个极简的虚构私有协议，报文头仅 7 字节，6 种消息类型覆盖多种 Handler 用法
- 设计并实现自定义注解 `@DemoMessageHandler` + `@DemoMessageMapping(msgType)`
- 基于 `AbstractSimpleXtreamRequestMappingHandlerMapping` 实现按 msgType 分发的 `DemoMessageHandlerMapping`
- 新增 `quick-start/custom-annotation-server/` Gradle module，包含可运行的 `main()` 入口
- 新增配套文档（协议说明 + 代码示例）

**Non-Goals:**
- 不修改已有模块或文档
- 不引入新的外部依赖
- 不实现客户端（使用 `nc` 或任何 TCP 调试工具即可测试）
- 不覆盖 UDP 传输，仅演示 TCP
- 不需要 Spring 依赖，使用纯 Java + 框架的核心模块

## Decisions

### 1. 虚构协议而非真实协议
- **选择**：虚构协议（X-IoT Demo Protocol）
- **理由**：真实协议（如 Modbus TCP）的 header 含有与注解分发演示无关的字段（Transaction ID、Unit ID 等），这些字段会分散读者注意力。虚构协议可以精确控制每个字段的用途，让读者专注于「自定义注解 → 按消息类型分发」这一核心概念。
- **替代方案**：Modbus TCP — 虽真实但 MBAP header 的 2/3 字段与教学目的无关

### 2. 整数缩放而非浮点数表示物理量
- **选择**：温度 int16 × 0.1°C、湿度 uint8 × 0.5%RH、气压 uint16 × 10hPa
- **理由**：真实 IoT 协议普遍使用缩放整数（JT/T 808 经纬度也是如此），规避浮点数端序和精度问题，对嵌入式终端友好。同时可展示 `@XtreamField` 中 `javaType` 映射或自定义转换器的使用
- **替代方案**：float/double — 更直观但偏离真实 IoT 实践

### 3. LengthFieldBasedFrameDecoder 处理粘包
- **选择**：基于消息头中 `bodyLength(2B)` 字段，使用 Netty 内置的 `LengthFieldBasedFrameDecoder`
- **理由**：TCP 流式传输必然产生粘包/半包问题。消息头中已有 bodyLength，用 Netty 内置解码器零成本解决，无需自定义定界符和转义规则（比 JT/T 808 的 0x7e 转义方案简单得多）
- **配置**：`maxFrameLength=1024, lengthFieldOffset=5, lengthFieldLength=2, lengthAdjustment=0, initialBytesToStrip=0`

### 4. 包扫描范围基于 main 类所在包
- **选择**：继承 `AbstractSimpleXtreamRequestMappingHandlerMapping` 后，包扫描默认使用 `XtreamUtils.detectMainClassPackageName()`（main 方法所在包）
- **理由**：与现有 `DemoTcpXtreamHandlerMapping2` 模式一致，无需显式配置包名，用法最简

### 5. 复用框架元注解而非从头定义
- **选择**：`@DemoMessageHandler` 标记 `@XtreamRequestHandler`，`@DemoMessageMapping` 标记 `@XtreamRequestHandlerMapping`
- **理由**：框架内置的扫描逻辑依赖 `@XtreamRequestHandler`（类级筛选）和 `@XtreamRequestHandlerMapping`（方法级筛选）。复用元注解可直接使用 `AbstractSimpleXtreamRequestMappingHandlerMapping` 的扫描能力，无需覆写扫描逻辑

## Data Model

### 报文头格式

| 字段 | 长度 | 说明 |
|------|------|------|
| magic | 4B | 固定 `0x12345678`，帧同步 |
| msgType | 1B | 消息类型，无符号 |
| bodyLength | 2B | 消息体字节数（大端） |

### 消息类型

| 类型值 | 名称 | 方向 | 消息体 |
|--------|------|------|--------|
| 0x01 | 心跳请求 | C→S | 无 |
| 0x02 | 时间查询 | C→S | 无，S→C 回复 `serverTime long(8B)` |
| 0x11 | 温湿度上报 | C→S | `temperature int16`(×0.1°C) + `humidity uint8`(×0.5%RH) |
| 0x12 | 多传感器数据 | C→S | `temperature int16` + `humidity uint8` + `pressure uint16`(hPa×10) + `windSpeed uint16`(×0.1m/s) + `timestamp long(8B)` |
| 0x81 | 设备注册 | C→S | `imeiLen byte` + `imei String`(ASCII) + `productKeyLen byte` + `productKey String`(ASCII) |
| 0x82 | 报警上报 | C→S | `alarmType uint16` + `descLen byte` + `desc String`(UTF-8) |

### 注解设计

```java
// 类级：标记该类为 Demo 协议处理器
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@XtreamRequestHandler
public @interface DemoMessageHandler { }

// 方法级：标记该方法可处理的消息类型
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@XtreamRequestHandlerMapping
public @interface DemoMessageMapping {
    byte[] msgType();
}
```

### HandlerMapping 设计

继承 `AbstractSimpleXtreamRequestMappingHandlerMapping`，覆写 `getHandler()` 方法：

1. 从报文 payload 中读取第 4 字节（msgType）
2. 遍历已扫描的 `handlerMethods`，返回 `msgType` 匹配的方法
3. 若无匹配，返回空 `Mono.empty()` 交给后续 HandlerMapping 或默认处理

### 包结构

```
quick-start/custom-annotation-server/
└── src/main/java/
    └── io/github/hylexus/xtream/quickstart/custom/annotation/
        ├── annotation/
        │   ├── DemoMessageHandler.java       # 类级注解
        │   └── DemoMessageMapping.java        # 方法级注解
        ├── handler/
        │   ├── MyDemoHandler.java             # @DemoMessageHandler 处理器类
        │   └── DemoMessageHandlerMapping.java # 自定义 HandlerMapping
        ├── entity/
        │   ├── TemperatureReport.java         # 温湿度上报实体
        │   ├── MultiSensorData.java           # 多传感器数据实体
        │   ├── DeviceRegisterRequest.java     # 设备注册实体
        │   └── AlarmReport.java               # 报警上报实体
        └── XtreamCustomAnnotationServerApp.java  # main 启动入口
```

## Risks / Trade-offs

- **虚构协议缺乏真实感** → 文档中明确标注"瞎编的私有协议，仅供演示"，与 `custom-protocol-sample-01` 风格一致
- **没有客户端代码** → 提供示例报文（hex 字符串），用户可用 `nc`、`echo` 配合 `xxd` 等工具直接测试
- **省略响应下发逻辑** → Handler 方法目前只演示 Request 接收，响应通过 `XtreamResponse` 写回。部分消息类型（0x02 时间查询、0x11/0x12 ack）会展示完整的请求-响应流程，其他仅展示请求处理

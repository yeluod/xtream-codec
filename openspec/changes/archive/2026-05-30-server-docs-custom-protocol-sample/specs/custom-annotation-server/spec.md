## ADDED Requirements

### Requirement: @DemoMessageHandler 注解

类级注解，标记某个类为 X-IoT Demo 协议的请求处理器。该注解须使用 `@XtreamRequestHandler` 作为元注解，以便被框架的扫描逻辑识别。

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@XtreamRequestHandler
public @interface DemoMessageHandler {
}
```

#### Scenario: 类被标记为处理器
- **WHEN** 一个类使用 `@DemoMessageHandler` 注解
- **THEN** `AbstractSimpleXtreamRequestMappingHandlerMapping` 的扫描逻辑能识别并扫描该类中的处理器方法

### Requirement: @DemoMessageMapping 注解

方法级注解，标记该方法能处理的消息类型。使用 `@XtreamRequestHandlerMapping` 作为元注解，并通过 `msgType` 属性指定能处理的消息类型值。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@XtreamRequestHandlerMapping
public @interface DemoMessageMapping {
    byte[] msgType();
}
```

#### Scenario: 方法被标记为特定消息类型的处理器
- **WHEN** 一个方法使用 `@DemoMessageMapping(msgType = 0x11)` 注解
- **THEN** 该方法被注册为 msgType=0x11 消息的处理器

#### Scenario: 注解属性为字节数组
- **WHEN** `@DemoMessageMapping` 的 `msgType` 属性为 byte[] 类型
- **THEN** 可同时指定多个消息类型，如 `msgType = {0x01, 0x02}`

### Requirement: DemoMessageHandlerMapping 分发逻辑

继承 `AbstractSimpleXtreamRequestMappingHandlerMapping`，按报文中的 msgType 分发到对应处理方法。

1. 从 `exchange.request().payload()` 中读取第 4 字节偏移处的 msgType 值
2. 遍历 `handlerMethods`，检查方法上的 `@DemoMessageMapping.msgType()` 是否包含该 msgType
3. 匹配成功返回该 HandlerMethod，否则返回 `Mono.empty()`

#### Scenario: 按 msgType 正确分发
- **WHEN** 收到 msgType=0x11 的请求
- **THEN** `getHandler()` 返回被 `@DemoMessageMapping(msgType = 0x11)` 注解标注的方法

#### Scenario: 无匹配处理器返回空
- **WHEN** 收到 msgType=0xFF 的请求，而没有任何处理器声明处理此类型
- **THEN** `getHandler()` 返回 `Mono.empty()`

### Requirement: 粘包处理

服务端使用 `LengthFieldBasedFrameDecoder` 拆包，配置为：
- `maxFrameLength` = 1024
- `lengthFieldOffset` = 5（magic 4B + msgType 1B 之后的 bodyLength 字段）
- `lengthFieldLength` = 2
- `lengthAdjustment` = 0
- `initialBytesToStrip` = 0

#### Scenario: 粘包时正确拆包
- **WHEN** 报文 A 和报文 B 在 TCP 流中一起到达（粘包）
- **THEN** `LengthFieldBasedFrameDecoder` 正确切分，依次调用 HandlerMapping 处理

### Requirement: 内置参数解析支持

Handler 方法可使用的参数类型（框架已有的内置支持）：

- `XtreamExchange exchange`
- `XtreamRequest request`
- `XtreamResponse response`
- `XtreamSession session`
- `ByteBuf payload`
- `@DemoBody` 注解标记的消息体实体（框架的 `@XtreamRequestBody` 别名或直接使用）

#### Scenario: 可注入 XtreamExchange 参数
- **WHEN** Handler 方法声明 `XtreamExchange exchange` 参数
- **THEN** 框架注入当前请求的 exchange 对象

#### Scenario: 无参方法
- **WHEN** Handler 方法不声明任何参数（如心跳处理）
- **THEN** 方法正常被调用，不报错

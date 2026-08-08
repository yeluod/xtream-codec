## 1. Module Setup

- [x] 1.1 Create `quick-start/custom-annotation-server/` directory with `build.gradle.kts` depending on `:xtream-codec-server-reactive` and logging
- [x] 1.2 Register module in `settings.gradle.kts`: `include("quick-start:custom-annotation-server")`
- [x] 1.3 Create Java package directory structure under `quick-start/custom-annotation-server/src/main/java/io/github/hylexus/xtream/quickstart/custom/annotation/`

## 2. Custom Annotations

- [x] 2.1 Create `@DemoMessageHandler` — class-level annotation, meta-annotated with `@XtreamRequestHandler`
- [x] 2.2 Create `@DemoMessageMapping` — method-level annotation, meta-annotated with `@XtreamRequestHandlerMapping`, with `int[] msgType()` attribute (changed from `byte[]` to avoid Java annotation byte literal casting)

## 3. Entity Classes

- [x] 3.1 Create `TemperatureReport` entity — fields: temperature(int16×0.1°C), humidity(uint8×0.5%RH), annotated with `@XtreamField`
- [x] 3.2 Create `MultiSensorData` entity — fields: temperature, humidity, pressure(uint16×10hPa), windSpeed(uint16×0.1m/s), timestamp(long)
- [x] 3.3 Create `DeviceRegisterRequest` entity — fields: imei(String ASCII), productKey(String ASCII)
- [x] 3.4 Create `AlarmReport` entity — fields: alarmType(uint16), desc(String UTF-8)

## 4. HandlerMapping

- [x] 4.1 Create `DemoMessageHandlerMapping` — extend `AbstractSimpleXtreamRequestMappingHandlerMapping`, override `getHandler()` to dispatch by msgType byte from payload

## 5. Handler Methods

- [x] 5.1 Create `MyDemoHandler` — class annotated `@DemoMessageHandler` with at least 3 handler methods:
  - `handleHeartbeat` for msgType=0x01 (no parameters)
  - `handleTemperature` for msgType=0x11 (with `@XtreamRequestBody TemperatureReport` parameter)
  - `handleRegister` for msgType=0x81 (with `XtreamExchange exchange` and `@XtreamRequestBody DeviceRegisterRequest` parameters)
- [x] 5.2 Reuse `@XtreamRequestBody` for body parameter injection (no separate @DemoBody needed)

## 6. Server Entry Point

- [x] 6.1 Create `XtreamCustomAnnotationServerApp.java` — `main()` method using `XtreamServerBuilder.newTcpServerBuilder()`
  - Configure LengthFieldBasedFrameDecoder(1024, 5, 2, 0, 0)
  - Register `DemoMessageHandlerMapping`
  - Enable builtin handler adapters and result handlers
  - Add LoggingXtreamFilter
  - Listen on port 8888
- [x] 6.2 Verify: compileJava passes ([build 8.1](#8-verification))

## 7. Documentation

- [x] 7.1 Create `docs/src/guide/server/samples/custom-demo-protocol/README.md` — sample index page
- [x] 7.2 Create `docs/src/guide/server/samples/custom-demo-protocol/protocol.md` — protocol format specification with header table, message type list, field details
- [x] 7.3 Create `docs/src/guide/server/samples/custom-demo-protocol/handler-demo.md` — complete code walkthrough showing annotations, HandlerMapping, and handler methods

## 8. Verification

- [x] 8.1 Run `./gradlew :quick-start:custom-annotation-server:build` and confirm success — **BUILD SUCCESSFUL**
- [ ] 8.2 Launch server and manually test with `echo -ne '...' | nc localhost 8888` for at least 2 message types (requires running the server)
- [ ] 8.3 Confirm LSP diagnostics clean on all new files (requires jdtls to be installed)

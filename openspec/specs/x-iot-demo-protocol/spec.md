# x-iot-demo-protocol Specification

## Purpose

定义 quick-start 中 X-IoT Demo 私有 TCP 协议的报文头、请求消息、响应消息和实体字段编码规则。
## Requirements
### Requirement: 报文头格式

X-IoT Demo Protocol 的报文头 SHALL 固定为 7 字节：

| 字段 | 长度 | 类型 | 说明 |
|------|------|------|------|
| magic | 4B | uint32(big-endian) | 固定值 `0x12345678`，用于帧同步 |
| msgType | 1B | uint8 | 消息类型 |
| bodyLength | 2B | uint16(big-endian) | 消息体字节数（不含报文头） |

总帧长 = 7(header) + bodyLength。

#### Scenario: 合法的报文头
- **WHEN** 服务端收到 `12 34 56 78 10 00 00`（magic=0x12345678, msgType=0x10, bodyLength=0）
- **THEN** 成功解析报文头，bodyLength=0 表示无消息体

#### Scenario: 非法 magic 应被丢弃
- **WHEN** 服务端收到 magic 不等于 `0x12345678` 的报文
- **THEN** 该报文被忽略或走异常处理路径

### Requirement: 心跳 (0x10)

心跳消息 SHALL 为 msgType=0x10，客户端→服务端，无消息体。用于保活连接。

#### Scenario: 心跳接收
- **WHEN** 服务端收到 msgType=0x10 且 bodyLength=0 的报文
- **THEN** 日志记录接收到心跳，连接保持

### Requirement: 时间查询 (0x11)

时间查询消息 SHALL 为 msgType=0x11，客户端→服务端，无消息体。服务端回复 `0x81` 类型的 BCD 时间。

#### Scenario: 时间查询与回复
- **WHEN** 服务端收到 msgType=0x11 的报文
- **THEN** 服务端回复 msgType=0x81 且消息体为 6 字节 `yyMMddHHmmss` BCD 时间的报文

### Requirement: 温湿度上报 (0x12)

温湿度上报消息 SHALL 为 msgType=0x12，消息体格式如下：

| 字段 | 长度 | 类型/缩放 | 说明 |
|------|------|-----------|------|
| temperature | 2B | int16 big-endian × 0.1°C | 如 235 → 23.5°C |
| humidity | 1B | uint8 × 0.5%RH | 如 120 → 60.0%RH |

#### Scenario: 温湿度上报处理
- **WHEN** 服务端收到 msgType=0x12，body=`00 EB 78`（temperature=235→23.5°C, humidity=120→60.0%RH）
- **THEN** 服务端成功解析为 TemperatureReport，temperature=23.5°C, humidity=60.0%RH

### Requirement: 多传感器数据上报 (0x13)

多传感器数据上报消息 SHALL 为 msgType=0x13，消息体格式如下：

| 字段 | 长度 | 类型/缩放 | 说明 |
|------|------|-----------|------|
| temperature | 2B | int16 big-endian × 0.1°C | |
| humidity | 1B | uint8 × 0.5%RH | |
| pressure | 2B | uint16 big-endian × 0.1hPa | 如 10132 → 1013.2hPa |
| windSpeed | 2B | uint16 big-endian × 0.1m/s | 如 35 → 3.5m/s |
| timestamp | 8B | long big-endian | Unix 毫秒时间戳 |

#### Scenario: 多传感器数据上报处理
- **WHEN** 服务端收到 msgType=0x13，body=`00 E1 6E 27 94 00 23 00 00 01 8B 3F 3B 5A 00`（temperature=22.5°C, humidity=55.0%RH, pressure=1013.2hPa, windSpeed=3.5m/s）
- **THEN** 服务端成功解析为 MultiSensorData 实体，各字段值正确

### Requirement: 设备注册 (0x14)

设备注册消息 SHALL 为 msgType=0x14，消息体格式如下：

| 字段 | 长度 | 说明 |
|------|------|------|
| imeiLen | 1B | 后续 imei 字符串的字节长度 |
| imei | N bytes | ASCII 字符串 |
| productKeyLen | 1B | 后续 productKey 字符串长度 |
| productKey | M bytes | ASCII 字符串 |

#### Scenario: 设备注册处理
- **WHEN** 服务端收到 msgType=0x14，body=`0F 38 36 38 31 30 35 30 34 30 38 37 36 35 34 33 02 41 42`（imeiLen=15, imei="868105040876543", productKeyLen=2, productKey="AB"）
- **THEN** 服务端成功解析注册请求，imei="868105040876543", productKey="AB"

### Requirement: 报警上报 (0x15)

报警上报消息 SHALL 为 msgType=0x15，消息体格式如下：

| 字段 | 长度 | 说明 |
|------|------|------|
| alarmType | 2B | uint16 big-endian |
| descLen | 1B | 后续 desc 字符串长度 |
| desc | N bytes | UTF-8 字符串 |

#### Scenario: 报警上报处理
- **WHEN** 服务端收到 msgType=0x15，body=`00 01 08 6F 76 65 72 68 65 61 74`（alarmType=1, descLen=8, desc="overheat" UTF-8）
- **THEN** 服务端成功解析报警请求，alarmType=1, desc="overheat"

### Requirement: 通用应答 (0x80)

通用应答消息 SHALL 为 msgType=0x80，消息体包含被应答消息类型和应答结果两个 u8 字段。

#### Scenario: 通用应答
- **WHEN** 服务端处理心跳、温湿度、多传感器或报警请求
- **THEN** 服务端可以回复 msgType=0x80，body=`ackMsgType result`

### Requirement: 时间查询响应 (0x81)

时间查询响应 SHALL 为 msgType=0x81，消息体为 6 字节 BCD8421 编码的 `yyMMddHHmmss` 时间。

#### Scenario: 返回服务器时间
- **WHEN** 服务端收到 msgType=0x11 的时间查询
- **THEN** 服务端回复 0x81 类型且 bodyLength=6 的响应

### Requirement: 注册应答 (0x82)

注册应答消息 SHALL 为 msgType=0x82，消息体包含一个 u8 注册结果和一个带 u8 前置长度的 ASCII 描述字符串。

#### Scenario: 返回注册结果
- **WHEN** 服务端处理 msgType=0x14 的设备注册请求
- **THEN** 服务端回复 msgType=0x82 的注册结果和描述

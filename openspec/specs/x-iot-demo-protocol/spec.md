# x-iot-demo-protocol Specification

## Purpose
TBD - created by archiving change server-docs-custom-protocol-sample. Update Purpose after archive.
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
- **WHEN** 服务端收到 `12 34 56 78 01 00 00`（magic=0x12345678, msgType=0x01, bodyLength=0）
- **THEN** 成功解析报文头，bodyLength=0 表示无消息体

#### Scenario: 非法 magic 应被丢弃
- **WHEN** 服务端收到 magic 不等于 `0x12345678` 的报文
- **THEN** 该报文被忽略或走异常处理路径

### Requirement: 心跳 (0x01)

心跳消息 SHALL 为 msgType=0x01，客户端→服务端，无消息体。用于保活连接。

#### Scenario: 心跳接收
- **WHEN** 服务端收到 msgType=0x01 且 bodyLength=0 的报文
- **THEN** 日志记录接收到心跳，连接保持

### Requirement: 时间查询 (0x02)

时间查询消息 SHALL 为 msgType=0x02，客户端→服务端，无消息体。服务端回复服务器当前时间戳。

#### Scenario: 时间查询与回复
- **WHEN** 服务端收到 msgType=0x02 的报文
- **THEN** 服务端回复 8 字节 long(big-endian) 格式的当前系统时间戳

### Requirement: 温湿度上报 (0x11)

温湿度上报消息 SHALL 为 msgType=0x11，消息体格式如下：

| 字段 | 长度 | 类型/缩放 | 说明 |
|------|------|-----------|------|
| temperature | 2B | int16 big-endian × 0.1°C | 如 235 → 23.5°C |
| humidity | 1B | uint8 × 0.5%RH | 如 120 → 60.0%RH |

#### Scenario: 温湿度上报处理
- **WHEN** 服务端收到 msgType=0x11，body=`EB 03 78`（temperature=0xEB03→601.9°C? wait, EB03 is -5373 signed... let me recalculate — EB 03 as signed int16 = -5373, × 0.1 = -537.3°C — unrealistic. let me use positive values in scenarios）
- **WHEN** 服务端收到 msgType=0x11，body=`00 8B 78`（temperature=0x008B=139→13.9°C, humidity=0x78=120→60.0%RH）
- **THEN** 服务端成功解析为 TemperatureReport，temperature=13.9°C, humidity=60.0%RH

### Requirement: 多传感器数据上报 (0x12)

多传感器数据上报消息 SHALL 为 msgType=0x12，消息体格式如下：

| 字段 | 长度 | 类型/缩放 | 说明 |
|------|------|-----------|------|
| temperature | 2B | int16 big-endian × 0.1°C | |
| humidity | 1B | uint8 × 0.5%RH | |
| pressure | 2B | uint16 big-endian × 10hPa | 如 10130 → 1013.0hPa |
| windSpeed | 2B | uint16 big-endian × 0.1m/s | 如 35 → 3.5m/s |
| timestamp | 8B | long big-endian | Unix 毫秒时间戳 |

#### Scenario: 多传感器数据上报处理
- **WHEN** 服务端收到 msgType=0x12，body=`00 8B 78 27 9A 00 23 00 00 01 81 99 5B 20 00`（示例值）
- **THEN** 服务端成功解析为 MultiSensorData 实体，各字段值正确

### Requirement: 设备注册 (0x81)

设备注册消息 SHALL 为 msgType=0x81，消息体格式如下：

| 字段 | 长度 | 说明 |
|------|------|------|
| imeiLen | 1B | 后续 imei 字符串的字节长度 |
| imei | N bytes | ASCII 字符串 |
| productKeyLen | 1B | 后续 productKey 字符串长度 |
| productKey | M bytes | ASCII 字符串 |

#### Scenario: 设备注册处理
- **WHEN** 服务端收到 msgType=0x81，body=`0F 38 36 38 31 30 35 30 34 30 38 37 36 35 34 33 02 41 42`（imeiLen=15, imei="868105040876543", productKeyLen=2, productKey="AB"）
- **THEN** 服务端成功解析注册请求，imei="868105040876543", productKey="AB"

### Requirement: 报警上报 (0x82)

报警上报消息 SHALL 为 msgType=0x82，消息体格式如下：

| 字段 | 长度 | 说明 |
|------|------|------|
| alarmType | 2B | uint16 big-endian |
| descLen | 1B | 后续 desc 字符串长度 |
| desc | N bytes | UTF-8 字符串 |

#### Scenario: 报警上报处理
- **WHEN** 服务端收到 msgType=0x82，body=`00 01 0C E6 B8 A9 E5 BA A6 E8 BF 87 E9 AB 98`（alarmType=1, descLen=12, desc="温度过高" UTF-8）
- **THEN** 服务端成功解析报警请求，alarmType=1, desc="温度过高"


## Why

二进制协议定义中，经常需要用一个前导长度字段表示后续多个字段的总字节数（消息体长度）。现有 `prependLengthFieldType` 只能覆盖单个字段，无法表达「从字段 A 到字段 B 的序列化字节数」。用户需要手动计算长度，繁琐且易错。

## What Changes

- 新增 `@EncodedLength` 注解，标注在无符号整数长度字段上，声明其值应自动计算为指定字段范围的编码后字节数
- 在元数据阶段解析 `@EncodedLength` 为单个 `EncodedLengthPlan`，编码时按计划计算范围字段的实际字节数并回填到长度字段
- 新增 `BeanPropertyMetadata.isEncodedLength()` 接口方法，用于保留字段标识；`from`/`until` 解析结果作为内部元数据，不作为公开接口暴露
- 修改 `SimpleBeanMetadataRegistry` 元数据解析，识别 `@EncodedLength` 并校验字段名、字段顺序、长度字段格式有效性
- 提供 Debug 模块的 Demo 示例（Demo005Message 以及继承场景）

## Capabilities

### New Capabilities
- `encoded-length`: 在平面实体或继承结构中使用单个 `@EncodedLength` 声明一个字段为后续字段范围的编码后字节数长度

### Modified Capabilities

（无）

## Impact

- `xtream-codec-core` 新增注解 `@EncodedLength`，新增编码长度计划与运行时辅助逻辑
- `xtream-codec-common` 新增 `BeanPropertyMetadata.isEncodedLength()` 接口方法
- `xtream-codec-core-debug` 新增 Demo005 示例
- 不涉及破坏性变更，新增功能与现有注解兼容

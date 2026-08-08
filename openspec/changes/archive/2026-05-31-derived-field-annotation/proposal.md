## Why

在 JT/T 808 等协议中，存在大量按位定义的字段（如位置信息汇报 0x0200 的 status 字段，32 位中每 1 位或每几位有独立语义）。当前框架仅支持将整个字段解码为原始整数类型（`long`/`int`），按位含义的提取需要业务方手动位运算或依赖 Javadoc 注释。

问题在于：
- 原始类型（`long`）暴露给业务层，缺乏类型安全
- 位定义分散在注释中，无编译期校验
- 每个业务方都要重复写 `(status >> n) & 1` 的样板代码

需要一个**可选的、声明式的**机制，让开发者能从一个已解码的原始字段自动衍生出业务视图（如 `EnumSet<T>`、`Map`、枚举、布尔等），同时保留原始字段的完整性和向后兼容性。

**注意**：此机制是通用字段衍生机制，不耦合 bit-field 场景——bit-field 只是其中一种典型用例。

## What Changes

- **新增 `@DerivedField` 注解** — 标记在实体类的字段上，声明该字段的值从另一个已解码字段衍生而来；支持从同一个 source 衍生出**多个不同类型的**目标字段
- **新增 `FieldTransformer` 接口** — 定义转换契约（`S → T` 和可选的 `T → S`），由用户自定义实现完整转换逻辑
- **新增编解码后处理管线** — 实体解码完成后，对所有 `@DerivedField` 执行 `read()`；编码前执行 `write()` 回写源字段（可配置每个衍生字段是否参与回写）
- **可选工具** — `BitFlag` 接口 + `EnumSetBitTransformer` 作为位段枚举场景的辅助工具，不属于 `@DerivedField` 核心

**非侵入原则**：
- 原始字段（如 `@JtStyle.Dword long status`）不受影响，完全向后兼容
- `@DerivedField` 是纯可选的，不引入强制依赖
- 不影响现有 codec pipeline 的性能路径

## Capabilities

### New Capabilities

- `derived-field`: 提供 `@DerivedField` 注解 + `FieldTransformer` SPI + 后处理管线，支持从任意已解码字段衍生出新的业务视图字段。`@DerivedField` 自身是纯通用的——与 bit-field 场景解耦，用户可自定义 `FieldTransformer` 将 source 转换为任意类型
- `bit-flag-enum`: 可选辅助工具，提供 `BitFlag` 标记接口 + `EnumSetBitTransformer` 抽象类，简化位段枚举场景的转换（通过子类桥接模式传递枚举类型参数）

### Modified Capabilities

<!-- No existing specs are modified -->

## Impact

### xtream-codec-core

- `EntityDecoder`: 解码完成后，对所有标记 `@DerivedField` 的字段执行 `FieldTransformer.read()`
- `EntityEncoder`: 编码前，对所有标记 `@DerivedField` 且 `reverseSource = true` 的字段执行 `FieldTransformer.write()`，将衍生值回写到源字段
- `BeanMetadata`: 扫描逻辑新增对 `@DerivedField` 的识别，将其标记为"非 ByteBuf 直接解码"字段
- 新增文件：
  - `annotation/DerivedField.java` — 注解定义
  - `FieldTransformer.java` — 转换器接口
  - `impl/bean/DerivedBeanPropertyMetadata.java` — 衍生字段的元数据实现
  - `common/utils/BitFlag.java` — 可选工具：位段标记接口
  - `common/utils/EnumSetBitTransformer.java` — 可选工具：位段枚举转换器

### ext/jt/jt-808-server-spring-boot-starter-reactive

- `BuiltinMessage0200.java` 及类似 message 类可逐渐使用 `@DerivedField` 提供类型安全的位段视图（`Set<StatusBit>`、`Map<String, Integer>`、`LoadStatus` 等）

### 无 Breaking Changes

- 原始字段的 `getStatus()` 返回类型不变
- 现有编解码行为不变

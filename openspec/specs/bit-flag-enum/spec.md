## Purpose

提供可选的位段枚举辅助工具（`BitFlag` + `EnumSetBitTransformer`），简化按位定义协议字段到类型安全 `EnumSet` 的转换。

## Requirements

### Requirement: BitFlag 标记接口（可选工具）

`BitFlag` SHALL 是位段枚举场景的标记接口，位于 `common.utils` 包。它是纯可选工具——不强制使用，不属于 `@DerivedField` 核心设计。

```java
public interface BitFlag {
    /** 该位段在原始整数值中的偏移量（0-based） */
    int bitOffset();
    /** 该位段的长度（以位为单位），默认 1 */
    default int bitLength() { return 1; }
}
```

#### Scenario: 枚举实现 BitFlag 接口
- **WHEN** 一个枚举类型 `StatusBit` 实现 `BitFlag` 接口，每个枚举常量指定 `bitOffset()`
- **THEN** 该枚举可以作为 `EnumSetBitTransformer` 的泛型参数

#### Scenario: bitOffset 从 0 开始计数
- **WHEN** 协议的位定义中 bit 0 表示 ACC 状态
- **THEN** `ACC_ON` 的 `bitOffset()` 返回 0

### Requirement: EnumSetBitTransformer 可选转换器

`EnumSetBitTransformer` SHALL 是框架提供的内置辅助转换器，将 `Long` 转换为 `EnumSet<E extends Enum<E> & BitFlag>`。因 Java 注解无法传递泛型实参，用户需写 3 行子类桥接枚举类型。

```java
// 框架提供
public abstract class EnumSetBitTransformer<E extends Enum<E> & BitFlag>
        implements FieldTransformer<Long, Set<E>> {

    protected EnumSetBitTransformer(Class<E> enumType) { ... }

    @Override
    public Set<E> read(Long source);    // 遍历枚举，按 bitOffset/bitLength 检测位值
    @Override
    public Long write(Set<E> derived);     // 遍历枚举，按 bitOffset 设位
}

// 用户写 3 行子类桥接泛型
public class StatusBitTransformer extends EnumSetBitTransformer<StatusBit> {
    public StatusBitTransformer() {
        super(StatusBit.class);
    }
}
```

#### Scenario: read 将 long 转换为 EnumSet
- **WHEN** `StatusBitTransformer().read(0b011)` 被调用，且枚举定义了 bit 0 和 bit 1
- **THEN** 返回的 `EnumSet` 包含 bit 0 和 bit 1 对应的枚举常量

#### Scenario: write 将 EnumSet 转换回 long
- **WHEN** `StatusBitTransformer().write(EnumSet.of(ACC_ON, POSITIONED))` 被调用
- **THEN** 返回的 `long` 值中 bit 0 和 bit 1 被置为 1，其余位为 0

#### Scenario: 不使用 EnumSetBitTransformer，完全自定义
- **WHEN** 用户不写子类，而是直接实现 `FieldTransformer<Long, Set<StatusBit>>`
- **THEN** 完全控制转换逻辑，按需处理 bit range、掩码等

### Requirement: 多 bit range 不适合 EnumSet

bit range（如 8~9 位表示载货状态）是**排他性枚举值**，不是一组独立 flag。用户 MUST 直接写自定义 Transformer，MUST NOT 使用 `EnumSetBitTransformer`。

#### Scenario: 多 bit range 使用自定义 Transformer
- **WHEN** 协议定义 bits 8~9: 00空车 01半载 10保留 11满载
- **THEN** 用户定义 `LoadStatus` 枚举 + `LoadStatusTransformer`（实现 `FieldTransformer<Long, LoadStatus>`）
- **THEN** `@DerivedField(source = "status", using = LoadStatusTransformer.class)` 正常使用

### Requirement: BitFlag 与 @DerivedField 配合使用（示例）

`BitFlag` / `EnumSetBitTransformer` 与 `@DerivedField` SHALL 通过 `FieldTransformer` 接口正交配合。

#### Scenario: 在实体中使用
- **WHEN** 实体类中定义如下字段：
  ```java
  @JtStyle.Dword(desc = "状态")
  private long status;

  // 单 bit flags（使用子类桥接）
  @DerivedField(source = "status", using = StatusBitTransformer.class, reverseSource = true)
  private transient Set<StatusBit> statusFlags;

  // 多 bit range（自定义 Transformer）
  @DerivedField(source = "status", using = LoadStatusTransformer.class, reverseSource = false)
  private transient LoadStatus loadStatus;
  ```
- **THEN** 解码后 `statusFlags` 和 `loadStatus` 分别从 `status` 衍生
- **THEN** 编码时仅 `statusFlags`（`reverseSource=true`）负责回写 `status`

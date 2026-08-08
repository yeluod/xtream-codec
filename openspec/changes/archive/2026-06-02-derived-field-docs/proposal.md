## Why

`@DerivedField` 注解自 0.6.0 版本起已实现，但缺少面向用户的文档说明。用户无法通过官方文档了解到：

- `@DerivedField` 的基本用法和编解码流程
- `FieldTransformer` SPI 如何自定义转换逻辑
- `@Repeatable` 多版本支持
- `BitFlag` / `EnumSetBitTransformer` 位段枚举工具
- 编码阶段的 `reverseSource` 机制

现有代码注释和 Javadoc 无法替代完整的使用指南。

## What Changes

- 在 `docs/src/guide/core/annotation-driven/` 下新增 `derived-field.md` 文档页
- 更新 `docs/src/.vuepress/sidebar/zh.ts`，在 ``builtin-annotations.md`` 之后添加该页导航
- 可选：在 `docs/src/code-snippet/core/` 下创建示例代码文件

不涉及代码（注解、SPI、编解码管线）的修改，仅文档变更。

## Capabilities

### New Capabilities

- `derived-field`: `@DerivedField` 注解的完整使用指南，包括注解属性说明、FieldTransformer SPI、编解码流水线（解码后转换 / 编码前回写）、@Repeatable 版本控制、BitFlag/EnumSetBitTransformer 工具类、实战示例（BuiltinMessage0200 状态位衍生）

### Modified Capabilities

无

## Impact

- 仅新增文档文件，无代码改动
- 侧边栏配置需添加一行导航项

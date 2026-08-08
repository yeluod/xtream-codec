## Context

`@DerivedField` 是 0.6.0 引入的核心注解，允许用户从已解码的原始字段（如 `long status`）衍生出业务视图（如 `Set<StatusBit>`），而无需改动原有的 `@Dword` 解码字段。

该功能已实现但在文档中完全缺失。现有文档目录 `guide/core/annotation-driven/` 覆盖了 `@XtreamField`、内置注解、自定义注解等，但缺少 `@DerivedField` 的独立页面。

## Goals / Non-Goals

**Goals:**

- 在 `guide/core/annotation-driven/derived-field.md` 创建完整的 `@DerivedField` 使用指南
- 覆盖：注解定义、`FieldTransformer` SPI、编解码流水线、`@Repeatable` 版本控制、`reverseSource` 编码回写、`BitFlag`/`EnumSetBitTransformer` 工具
- 包含至少一个完整的实战示例（基于 `BuiltinMessage0200.status` 的位衍生场景）
- 更新侧边栏配置

**Non-Goals:**

- 不修改任何 Java 代码（注解、SPI、编解码管线）
- 不修改现有文档页的内容
- 不在 JT/T 808 文档区重复衍生字段文档（可在复杂消息示例中交叉引用）

## Decisions

### 文档位置：`guide/core/annotation-driven/derived-field.md`

- 这是 `@DerivedField` 的自然归属——它是一个 core 层的注解，与 `@XtreamField`、内置注解同级
- 不放在 `annex/`（不是附录）
- 不放在 JT/T 808 扩展下（不是协议特定功能）

### 侧边栏排序：`builtin-annotations.md` → `derived-field.md` → `custom-annotation.md`

- `builtin-annotations.md` 介绍已有的预设注解
- `derived-field.md` 是新增的注解类型
- `custom-annotation.md` 是进阶自定义能力
- 从"基础"到"扩展"的自然过渡

### 文档内容结构

采用与现有 `xtream-field-annotation.md` 一致的写作风格：介绍 → 注解属性 → 使用方式 → 示例 → 进阶特性。

### 代码示例引用

- 核心示例代码（如 DemoEntity 和 Test）放在 `docs/src/code-snippet/core/derived-field-demo/` 下
- 文档通过 `@[code](@src/core/derived-field-demo/DerivedFieldDemoTest.java)` 引用
- 避免在 .md 文件中嵌入大段 Java 代码，保持文档可读性

### 写作规范

- 所有注释和说明用简体中文（遵循 AGENTS.md 5.3 规范）
- `@since` 标签版本统一为 `0.6.0`

## Risks / Trade-offs

- [低] 文档内容与未来可能的 `@DerivedField` 增强（如新的 SPI 方法）不同步 → 作为一个独立文档页，后续更新成本低
- [低] 代码示例可能随重构而过时 → 示例放在独立的 `code-snippet` 目录，更新时定位方便

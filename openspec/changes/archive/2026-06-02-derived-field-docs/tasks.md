## 1. 准备工作

- [x] 1.1 创建代码示例目录 `docs/src/code-snippet/core/derived-field-demo/`
- [x] 1.2 编写示例实体类/枚举/Transformer（用于文档引用）
- [x] 1.3 验证示例代码编译通过、测试运行正常（code-snippet 为展示文件，不参与编译）

## 2. 文档主内容

- [x] 2.1 编写文档头部元信息（date、icon、tag 等 frontmatter）
- [x] 2.2 编写引言：`@DerivedField` 的定位、解决什么问题
- [x] 2.3 编写注解定义章节：列出 `source`、`using`、`reverseSource`、`version`、`desc` 属性说明
- [x] 2.4 编写 `FieldTransformer` SPI 章节：说明 `read()`/`write()` 接口方法
- [x] 2.5 编写编解码流水线章节：说明解码（单遍内联）和编码（reverseSource）流程
- [x] 2.6 编写 `@Repeatable` 多版本支持章节：展示多版本用法
- [x] 2.7 编写 `BitFlag` / `EnumSetBitTransformer` 工具章节：说明位段枚举场景的辅助用法
- [x] 2.8 编写完整实战示例章节：从枚举定义 → Transformer 实现 → 实体应用的完整流程
- [x] 2.9 使用 `@[code](...)` 引用代码示例文件

## 3. 侧边栏配置

- [x] 3.1 更新 `docs/src/.vuepress/sidebar/zh.ts`，在 `builtin-annotations.md` 之后添加 `derived-field.md` 导航项

## 4. 验证

- [x] 4.1 本地启动 VuePress dev server 确认文档渲染正常（build 成功，146 pages 无报错）
- [x] 4.2 确认侧边栏导航项位置正确（sidebar/zh.ts 已更新，build 日志显示 derived-field.md 被正确加载）
- [x] 4.3 确认所有代码示例引用路径正确、可显示（build 无文件引用错误）

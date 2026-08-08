## 1. 注解定义

- [x] 1.1 创建 `@EncodedLength` 注解，含 `from()` / `until()` 属性（默认均为 `""`），`@since 0.7.0`

## 2. 元数据层

- [x] 2.1 在 `BeanPropertyMetadata` 接口中新增 `default boolean isEncodedLength()`，不公开 `from`/`until` 解析细节
- [x] 2.2 新增内部 `EncodedLengthPlan`，存储 length 字段下标、from 下标、until 下标、回填写入器
- [x] 2.3 修改 `BeanMetadata`，缓存可选的 `EncodedLengthPlan`，普通实体保持快速路径
- [x] 2.4 修改 `SimpleBeanMetadataRegistry`，检测 `@EncodedLength` 注解并构建 `EncodedLengthPlan`
- [x] 2.5 元数据注册时验证：`from`/`until` 字段名存在于实体字段列表、`from != until`、`from order < until order`、只允许单个 `@EncodedLength`
- [x] 2.6 元数据注册时验证长度字段格式仅支持 u8/u16/u32，其他格式提前失败

## 3. 编码器

- [x] 3.1 新增内部 `EncodedLengthRuntime`，封装占位索引、范围起始索引、回填动作
- [x] 3.2 修改 `EntityEncoder`，在带 `EncodedLengthPlan` 的实体中调用 runtime helper，主循环不直接维护多个 range 状态变量
- [x] 3.3 回填时根据计划中的 u8/u16/u32 写入器选择正确的 ByteBuf 写方法
- [x] 3.4 保持普通实体走无 range 的编码路径，避免给常规编码增加额外分支

## 4. 测试

- [x] 4.1 编写平面单类的 roundtrip 编码/解码测试（`@EncodedLength` + 显式 `from`/`until`）
- [x] 4.2 编写继承场景的 roundtrip 测试（父类 `@EncodedLength`、子类 body 字段）
- [x] 4.3 编写 `from` 空值/`until` 空值/两者皆空的边界测试
- [x] 4.4 编写元数据校验失败的负向测试（字段名无效、`from == until`、`from` 在 `until` 之后、多个 `@EncodedLength`、不支持的长度字段格式）
- [x] 4.5 编写条件字段或 null 字段场景测试，确认长度按实际编码字节数计算

## 5. Debug 示例

- [x] 5.1 更新 `Demo005Message` 或其关联类，演示单个 `@EncodedLength` 用法

## 6. 验证

- [x] 6.1 运行 `./gradlew :xtream-codec-core:test` 确保全部通过
- [x] 6.2 运行 `./gradlew build -P xtream.backend.build.checkstyle.enabled=false -P xtream.backend.build.errorprone.enabled=false` 确保构建通过

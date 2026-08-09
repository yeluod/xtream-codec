## 1. 协议 Builder 入口

- [x] 1.1 在 JT/T 808 模块中新增协议专用 Server Builder facade
- [x] 1.2 在 JT/T 1078 模块中新增协议专用 Server Builder facade
- [x] 1.3 保持 `XtreamServers` 仅暴露协议无关能力

## 2. JT/T 808 自动配置迁移

- [x] 2.1 将指令 TCP 自动配置迁移到协议 builder
- [x] 2.2 将指令 UDP 自动配置迁移到协议 builder
- [x] 2.3 将附件 TCP 自动配置迁移到协议 builder
- [x] 2.4 将附件 UDP 自动配置迁移到协议 builder
- [x] 2.5 保持现有 bean name、条件装配和默认配置不变

## 3. JT/T 1078 自动配置迁移

- [x] 3.1 将 TCP 自动配置迁移到协议 builder
- [x] 3.2 将 UDP 自动配置迁移到协议 builder
- [x] 3.3 保持现有 bean name、条件装配和默认配置不变

## 4. 测试与验证

- [x] 4.1 为 808 builder 添加默认 customizer 顺序与 handler 绑定测试
- [x] 4.2 为 1078 builder 添加默认 customizer 顺序与 handler 绑定测试
- [x] 4.3 为自动配置迁移添加兼容性测试
- [x] 4.4 运行相关 starter 模块测试和构建验证

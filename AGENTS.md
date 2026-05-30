# Agent 指引（my-website）

## website-services 后端 Java

修改 `services/website-services/` 下代码前，必须遵守：

1. **结构总纲（完整）**  
   [`docs/website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`](docs/website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)

2. **Cursor 硬约束（短版）**  
   [`.cursor/rules/backend-structure.mdc`](.cursor/rules/backend-structure.mdc)（编辑 `**/*.java` 时自动关联）

3. **命名速查**  
   [`docs/website-services/设计文档/基础架构/后端包结构与命名规范V1.md`](docs/website-services/设计文档/基础架构/后端包结构与命名规范V1.md)

冲突时以 **结构总纲 V1** 为准。当前 **5E 菜单未实现**；结构小收敛见总纲 §17，功能开发在收敛后再做 5E。

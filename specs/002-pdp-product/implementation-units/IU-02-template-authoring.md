# IU-02 项目模板编制基础

## 目标

完成项目模板 MySQL 持久化与模板版本编制服务，为后续跨模块原子实例化提供稳定模板来源，不提前创建项目、任务、交付件或审批数据。

## 原始任务

按顺序完成并分别提交：`T133`、`T134`、`T135`。

依赖：`T131`、`T132` 已完成。

## 独占文件

- `modules/persistence-mysql/src/main/java/com/pdp/mysql/template/`
- `modules/persistence-mysql/src/main/resources/mapper/template/`
- `tests/backend/contract/template/ProjectTemplateDatabaseContractTest.java`
- `modules/template/src/main/java/com/pdp/template/application/ProjectTemplateService.java`
- `modules/template/src/test/` 中与模板编制直接相关的测试

## 禁止修改

禁止修改公共模板 DDL、项目/计划/交付件/审批模块、OpenAPI、Controller、Vue 页面和 E2E。不得以临时表或假仓储替代 T133。

## 最小验证

- T133：编译 `public-persistence`、`persistence-mysql`。
- T134：仅运行 `ProjectTemplateDatabaseContractTest`；Docker 不可用时运行等价路径并记录一次限制。
- T135：运行 `modules/template` 定向单元测试。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。交付摘要列出 Task 与提交号，并说明模板版本状态、内容哈希和仓储乐观锁语义。

# IU-03 项目生命周期基础

## 目标

建立项目、阶段、项目成员与父子关系的数据结构、领域模型、公开端口和 MySQL 契约，不实现应用服务、Controller 或页面。

## 原始任务

按顺序完成并分别提交：`T144`、`T145`、`T146`、`T147`、`T148`。

依赖：公共持久化、工作空间上下文和统一并发组件已完成；契约细化由 IU-01 独占，不阻塞本单元按现有规格建模。

## 独占文件

- `modules/public-persistence/src/main/resources/db/changelog/common/040-project-lifecycle.xml`
- `modules/project/src/main/java/com/pdp/project/domain/`
- `modules/project/src/main/java/com/pdp/project/port/`
- `modules/persistence-mysql/src/main/java/com/pdp/mysql/project/`
- `modules/persistence-mysql/src/main/resources/mapper/project/`
- `tests/backend/contract/project/ProjectLifecycleDatabaseContractTest.java`

## 禁止修改

禁止修改模板表、`openapi.yaml`、`events.md`、`coverage.md`、API Controller、Vue 页面及其他模块内部类型。模板模块只能在后续通过公开端口引用项目能力。

## 最小验证

先编译 `project`、`public-persistence`、`persistence-mysql`，最后仅运行 `ProjectLifecycleDatabaseContractTest`。不运行跨模块 E2E。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。摘要列出 Task 与提交号，并说明生命周期、父子无环、工作空间隔离与 revision 语义。

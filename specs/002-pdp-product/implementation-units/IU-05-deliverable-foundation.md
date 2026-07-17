# IU-05 交付件基础

## 目标

建立交付件、不可变版本、附件引用、审核与签核的数据结构、领域模型、公开端口和 MySQL 契约。

## 原始任务

按顺序完成并分别提交：`T184`、`T185`、`T186`、`T187`、`T188`。

依赖：对象存储公共边界和高风险操作组件已完成；契约细化由 IU-01 独占。

## 独占文件

- `modules/public-persistence/src/main/resources/db/changelog/common/070-deliverable.xml`
- `modules/deliverable/src/main/java/com/pdp/deliverable/domain/`
- `modules/deliverable/src/main/java/com/pdp/deliverable/port/`
- `modules/persistence-mysql/src/main/java/com/pdp/mysql/deliverable/`
- `modules/persistence-mysql/src/main/resources/mapper/deliverable/`
- `tests/backend/contract/deliverable/DeliverableDatabaseContractTest.java`

## 禁止修改

禁止修改对象存储实现、审批模块、共享契约、Controller、Vue 页面和 E2E。签核只保存稳定关联，不直接依赖审批内部表。

## 最小验证

编译 `deliverable`、`public-persistence`、`persistence-mysql`，最后只运行 `DeliverableDatabaseContractTest`。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。摘要列出 Task 与提交号，并说明版本不可覆盖、内容哈希、签核绑定和工作空间隔离语义。

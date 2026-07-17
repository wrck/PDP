# IU-04 任务协作基础

## 目标

建立任务、子任务、检查项、指派、评论、关注和活动的数据结构、领域模型、公开端口和 MySQL 契约，不实现应用服务、Controller 或页面。

## 原始任务

按顺序完成并分别提交：`T157`、`T158`、`T159`、`T160`、`T161`。

依赖：项目引用使用稳定公开标识；契约细化由 IU-01 独占。

## 独占文件

- `modules/public-persistence/src/main/resources/db/changelog/common/050-task-collaboration.xml`
- `modules/planning/src/main/java/com/pdp/planning/domain/task/`
- `modules/planning/src/main/java/com/pdp/planning/port/task/`
- `modules/persistence-mysql/src/main/java/com/pdp/mysql/task/`
- `modules/persistence-mysql/src/main/resources/mapper/task/`
- `tests/backend/contract/planning/TaskCollaborationDatabaseContractTest.java`

## 禁止修改

禁止修改 `planning/domain/plan`、项目模块内部类型、共享契约、父 POM、应用服务、Controller、Vue 页面、E2E 和共享 `tasks.md`。跨模块引用只能使用 UUID/公共值对象或公开端口。

## 最小验证

按任务编译受影响模块；T161 只运行 `TaskCollaborationDatabaseContractTest`。最后运行一次与 `domain/task` 直接相关的模块测试，不执行全后端回归。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。摘要列出 Task 与提交号，并说明任务层级、检查项门禁、协作记录和乐观并发语义。

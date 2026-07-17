# IU-07 计划基线基础

## 目标

建立里程碑、依赖、计划基线和不可变快照的数据结构、领域模型、公开端口和 MySQL 契约，不实现进度计算、Controller 或页面。

## 原始任务

按顺序完成并分别提交：`T171`、`T172`、`T173`、`T174`。

依赖：项目和任务只通过稳定公开标识关联；契约细化由 IU-01 独占。

## 独占文件

- `modules/public-persistence/src/main/resources/db/changelog/common/060-plan-baseline.xml`
- `modules/planning/src/main/java/com/pdp/planning/domain/plan/`
- `modules/planning/src/main/java/com/pdp/planning/port/plan/`
- `modules/persistence-mysql/src/main/java/com/pdp/mysql/plan/`
- `modules/persistence-mysql/src/main/resources/mapper/plan/`
- `tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java`

## 禁止修改

禁止修改 `planning/domain/task`、项目模块内部类型、共享契约、父 POM、应用服务、Controller、Vue 页面、E2E 和共享 `tasks.md`。不得直接依赖任务或项目内部持久化记录。

## 最小验证

按任务编译受影响模块；T174 只运行 `PlanBaselineDatabaseContractTest`。最后运行一次与 `domain/plan` 直接相关的模块测试，不执行全后端回归。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。摘要列出 Task 与提交号，并说明依赖无环、里程碑权重、基线快照和乐观并发语义。

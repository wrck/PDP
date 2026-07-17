# IU-06 审批基础

## 目标

建立审批定义、实例、轮次、节点和动作的数据结构、领域模型、公开端口和 MySQL 契约；工作流只通过既有稳定端口关联。

## 原始任务

按顺序完成并分别提交：`T198`、`T199`、`T200`、`T201`、`T202`。

依赖：平台工作流基础 `T077`～`T090` 已完成；契约细化由 IU-01 独占。

## 独占文件

- `modules/public-persistence/src/main/resources/db/changelog/common/080-approval.xml`
- `modules/approval/src/main/java/com/pdp/approval/domain/`
- `modules/approval/src/main/java/com/pdp/approval/port/`
- `modules/persistence-mysql/src/main/java/com/pdp/mysql/approval/`
- `modules/persistence-mysql/src/main/resources/mapper/approval/`
- `tests/backend/contract/approval/ApprovalDatabaseContractTest.java`

## 禁止修改

禁止引用 Flowable API/表，禁止修改 `workflow` 内部实现、共享契约、Controller、Vue 页面和 E2E。业务审批结论必须保存在审批聚合，不写入流程引擎作为唯一事实。

## 最小验证

编译 `approval`、`public-persistence`、`persistence-mysql`，最后只运行 `ApprovalDatabaseContractTest` 和既有 `WorkflowBoundaryTest`。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。摘要列出 Task 与提交号，并说明轮次保留、节点顺序、幂等动作、并发审批与工作流边界。

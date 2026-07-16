# ADR-0005：Flowable 作为平台工作流基础能力

- 状态：已接受
- 日期：2026-07-17
- 追溯：FR-174、SC-046

## 决策

P1 采用 Flowable 8.0.x Process Engine 和 BPMN 2.0.2，仅引入 Process starter。平台 `workflow` 模块提供定义、运行、人工任务和管理四类稳定端口；业务模块不得依赖 Flowable API、表结构、内部标识或异常。

项目、任务、交付件、审批结论、权限和审计仍由 PDP 领域对象保存。Flowable 只负责路由、等待、定时、并行、消息关联、重试和补偿编排。业务事务通过 Outbox 与引擎本地事务异步衔接，不使用 XA。

Flowable 使用独立 schema、账号、HikariCP 和事务管理器。生产关闭自动建表和升级，不暴露 Flowable REST，不启用 IDM、CMMN、DMN 或 JPA/Hibernate。人工任务查询与办理必须实时复核 PDP 权限。

## 版本与恢复

BPMN 使用稳定流程键、业务版本和内容哈希；运行实例固定启动版本。迁移必须预览、分批、可暂停。异步重试耗尽形成 incident/dead-letter，可安全重放或人工补偿，且不得重复业务结论。

## 退出条件

若引擎无法满足恢复、许可、容量或兼容要求，稳定端口允许替换适配器。替换必须先完成实例和定义迁移方案及等价契约验证。


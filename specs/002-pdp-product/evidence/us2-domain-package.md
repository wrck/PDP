# US2 领域包与深度定制独立验收证据

## 验收结论

验收时间：2026-07-17（Asia/Shanghai）。

网络设备割接示例包已覆盖项目与交付件扩展、割接单、割接步骤、割接资源、状态机、页面、视图、规则、权限、工作流绑定、受治理扩展及可回滚迁移。领域包契约、后端模型绑定、生命周期职责分离、影响预览、失败隔离、回滚、前端设计器和 H2 数据库契约均通过自动化验证。

**功能与契约闭环通过；领域包生产级日志、指标和追踪接线以及 MySQL 8.4 实库证据仍是发布门禁。** 本文件不把通用观测组件测试或 H2 结果误记为领域包生产观测、MySQL 实库通过。

## 示例包范围

示例文件：`tests/fixtures/domain-package/network-cutover-package.json`。

| 对象 | 类型 | 关键定制 |
|---|---|---|
| `project.cutover-extension` | 核心项目扩展 | 项目割接窗口；规划、执行、关闭状态 |
| `deliverable.cutover-extension` | 核心交付件扩展 | 割接验收证据；待提交到已验收状态 |
| `cutover.plan` | 新增割接单 | 割接窗口、敏感回退方案、步骤/资源聚合、交付件与项目引用 |
| `cutover.step` | 新增割接步骤 | 顺序、指令、执行日志；待执行、执行中、完成/失败状态 |
| `cutover.resource` | 新增割接资源 | 设备引用、敏感配置快照；预留、就绪、释放状态 |

行业包继承 `pdp.standard-delivery@^1.0.0`。`device.precheck` 扩展声明 SHA-256 制品摘要、签名、最小权限、5 秒超时、128 MB 内存和进程隔离；工作流只引用稳定流程标识、业务版本、BPMN 资源、授权策略和变量映射，不依赖 Flowable 专有 API。

## 状态机与职责分离

### 割接业务状态

割接单主路径为 `draft → reviewing → executing → verified`，受控失败路径为 `executing → rolled-back`。所有状态映射平台顶层生命周期，只有 `draft` 是初始状态，`verified` 和 `rolled-back` 是终态。进入执行前必须通过 `cutover.precheck`，回退必须通过 `cutover.rollback-required` 并要求 `cutover.rollback` 权限。

步骤与资源分别形成 `pending → executing → completed/failed` 和 `reserved → ready → released` 子状态机，不以页面按钮代替领域状态。项目扩展和交付件扩展也具有独立状态与权限门禁。

### 领域包发布状态

版本闭环为：

```text
DRAFT → VALIDATED → REVIEW_PENDING → APPROVED → PUBLISHED（冻结）
```

- 创建者创建版本、校验后通过 `/submit-review` 提交审核；
- 与创建者不同的审核人只能审核 `REVIEW_PENDING` 版本；
- 与创建者、审核人均不同的发布批准人只能发布 `APPROVED` 版本；
- `DomainPackageLifecycleServiceTest` 验证创建者自审被拒绝，发布后版本为 `PUBLISHED` 且 `frozen=true`；
- `DomainPackageControllerLifecycleTest` 验证审核接口不会代替创建者执行提交审核。

## 影响预览、迁移与回滚证据

`0.9.0 → 1.0.0` 迁移为敏感回退方案设置默认值，并声明 `RESTORE_SNAPSHOT`。`DomainPackageMigrationServiceTest` 验证：

- 迁移前必须生成影响预览和高风险确认令牌；
- 250 个受影响实例按每批 100 个拆为 3 批；
- 预览绑定源版本、目标版本、范围、批次大小和版本 revision，任一变化均要求重新预览；
- 单批 99 个成功、1 个失败时保留失败实例清单，其他批次可继续；
- 已处理数量不得超过预览影响数，并可按快照回滚为 `ROLLED_BACK`。

## 日志、指标与追踪证据

通用观测组件的定向测试共 4 个，全部通过，已证明以下基础语义：

- `OperationTelemetry` 创建 `pdp.operation` Observation，低基数键为操作和服务，高基数键为 `request_id`、`trace_id`；
- 成功与失败均输出结构化日志，字段包含 `event`、`operation`、`service`、`requestId`、`traceId`、`durationMs`，失败增加 `errorType`；
- `AvailabilitySliCollector` 采集 `pdp.availability.requests` 和 `pdp.availability.request.duration`，工作空间、用户、请求标识不进入指标标签；
- `RequestContextFilter` 接受或生成 `X-Trace-Id`，并在进入业务处理前建立工作空间、操作者、追踪和幂等上下文。

领域包生命周期已发布 `pdp.domain-package.validated`、`review-requested`、`approved`、`published`、`retired` 和 `rolled-back` 等稳定事件；示例业务规则发布 `pdp.cutover.precheck-completed` 与 `pdp.cutover.rollback-started`。

当前代码尚未发现领域包 Controller/应用服务接入 `OperationTelemetry`，也未发现 `DomainPackageEventPublisher` 的生产审计/指标适配器。因此尚不能证明上述领域事件与请求的日志、指标、trace 已在运行环境形成同一关联链。发布前必须补齐接线，并以同一 `traceId` 查询到请求日志、Observation、生命周期事件和迁移作业。

## 自动化验收结果

执行结果：

- `pnpm exec node --test tests/contracts/us2-domain-package.spec.ts`：3/3 通过；示例包通过 JSON Schema，并断言五类对象、割接状态机、审核策略和快照回滚。
- `pnpm --filter @pdp/tests test:contracts`：77 个 OpenAPI 操作、2 个 JSON Schema 通过。
- `mvnw -pl modules/domainconfig -am test`：`domainconfig` 10/10、`shared-kernel` 11/11 通过。
- API 定向测试：正式示例包成功绑定后端模型，领域校验无错误和警告，审核职责分离通过。
- `pnpm --filter @pdp/web lint/typecheck/test/build`：领域包设计器纳入前端质量门禁，11 个测试文件、18 个测试通过，生产构建成功；仅存在主包超过 500 kB 的非阻断警告。
- `DomainPackageDatabaseContractTest`：3 个测试中 2 个通过、1 个 MySQL Testcontainers 测试跳过；H2 上公共变更集、约束和仓储闭环通过。

复验命令：

```powershell
pnpm exec node --test tests/contracts/us2-domain-package.spec.ts
pnpm --filter @pdp/tests test:contracts
pnpm --filter @pdp/web lint
pnpm --filter @pdp/web typecheck
pnpm --filter @pdp/web test
pnpm --filter @pdp/web build

$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.9+10'
.\mvnw.cmd -pl modules/domainconfig -am test
.\mvnw.cmd -pl apps/api -am "-Dtest=DomainPackageManifestBindingTest,DomainPackageControllerLifecycleTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -pl modules/operations -am "-Dtest=OperationTelemetryTest,AvailabilitySliCollectorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -pl tests/backend "-Dtest=DomainPackageDatabaseContractTest" test
```

## 发布门禁与限制

1. 当前环境无法访问 `npipe:////./pipe/docker_engine`，MySQL 8.4 Testcontainers 用例被跳过；必须在可用 Docker/MySQL 环境复验变更集、索引、唯一约束和真实读写。
2. 将领域包创建、校验、提交审核、审核、发布、迁移和回滚接入 `OperationTelemetry`，并提供低基数操作指标、失败率、耗时和迁移失败实例告警。
3. 实现 `DomainPackageEventPublisher` 生产适配器，将领域事件写入审计/事务事件日志，并验证与 `traceId`、工作空间、操作者、包版本和 revision 的关联。
4. 补充运行环境的成功/失败请求样本、Prometheus 查询结果和分布式追踪截图或导出证据后，才能把 US2 标记为发布级完全通过。

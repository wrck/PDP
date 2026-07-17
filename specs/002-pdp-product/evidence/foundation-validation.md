# 基础阶段门禁证据

## 1. 门禁结论

PDP P1 基础阶段已建立模块边界、平台工作流、MySQL 专有适配隔离、动态数据源、权限撤销、后台作业、缓存降级、对象存储和可观测性基线。所有公共持久化契约保留在 `public-persistence`，MySQL DDL、Mapper、SQL 与适配器保留在 `persistence-mysql`；业务模块未直接依赖 MySQL 或 Flowable 专有 API。

本证据只确认基础能力可进入用户故事实现，不替代具备 Docker 环境的 MySQL 8.4、Flowable schema 和恢复演练门禁。

## 2. 验证环境

| 项目 | 验证值 |
|---|---|
| 验证时间 | 2026-07-17（Asia/Shanghai） |
| 操作系统 | Windows，PowerShell |
| Java | JDK 21 |
| 数据库范围 | P1 MySQL；公共契约采用 H2 等价路径补充验证 |
| 前端 | Vue 3、TypeScript、Vitest、Playwright |
| 已知环境限制 | 当前进程访问 `\\.\pipe\docker_engine` 被拒绝，Testcontainers 的 MySQL 8.4 用例按假设跳过 |

## 3. 可审计验证矩阵

| 门禁 | 自动化证据 | 结论 |
|---|---|---|
| 模块与依赖方向 | `ModuleBoundaryTest`、`DependencyPolicyTest` | 业务模块不反向依赖适配层 |
| MySQL 持久化边界 | `PersistenceBoundaryTest`、`PersistenceProviderExtensionContractTest`、`LiquibaseChangelogValidationTest` | 公共契约与 MySQL 专有实现分离 |
| 动态数据源与连接池 | `DynamicDataSourceRoutingTest`、`DataSourceRoutingGuardTest`、`HikariPoolResilienceTest` | 单一活动写部署、路由守卫和池隔离成立 |
| 历史迁移隔离 | `MigrationDataSourceIsolationTest` | 迁移源、目标与平台运行数据源边界明确 |
| 平台工作流 | `PlatformWorkflowFoundationTest`、`WorkflowBoundaryTest`、`WorkflowDependencyPolicyTest`、`platform-workflow.spec.ts` | Flowable 仅由 workflow 模块适配，定义与运行契约稳定 |
| 权限与撤权 | `PermissionRevocationSlaTest`、`WorkspaceIsolationSecurityTest`、`SearchProjectionConsistencyTest` | 新请求、缓存和搜索投影按时限撤权，跨空间查询被拒绝 |
| 高风险操作 | `HighRiskOperationTest`、`high-risk-operation.spec.ts` | 预览、版本绑定、确认、过期与补偿具有自动化证据 |
| 后台作业与缓存 | `BackgroundJobLifecycleTest`、`ResilientRedisCacheTest` | 暂停、恢复、取消、检查点、失败明细、降级和防击穿成立 |
| 可观测性 | `AvailabilitySliCollectorTest`、`OperationTelemetryTest` | 日志、指标、追踪关联及 FR-165 原始 SLI 口径成立 |

## 4. 本次执行结果

| 验证批次 | 结果 |
|---|---|
| 后端基础聚合门禁 | `backend-tests` 45 项通过，0 失败、0 错误、0 跳过；被选择的 `shared-kernel` 高风险操作 3 项同时通过 |
| operations 能力 | `operations` 10 项通过，0 失败、0 错误、0 跳过；其依赖的 `shared-kernel` 11 项通过 |
| 平台工作流契约 | 2 项通过，0 失败、0 跳过 |
| Vue 单元测试 | 11 个测试文件、18 项测试全部通过 |
| 高风险操作浏览器测试 | Chromium 3 项通过，覆盖预览过期、目标版本变化和补偿发起 |

所有批次均以退出码 `0` 完成。`OperationTelemetryTest` 输出的失败堆栈是“内部异常计入可用性失败”的受控测试场景，不是测试失败。

## 5. 执行命令

```powershell
.\mvnw.cmd -pl tests/backend -am test "-Dtest=ModuleBoundaryTest,DependencyPolicyTest,PersistenceBoundaryTest,PersistenceProviderExtensionContractTest,LiquibaseChangelogValidationTest,DynamicDataSourceRoutingTest,DataSourceRoutingGuardTest,HikariPoolResilienceTest,MigrationDataSourceIsolationTest,PlatformWorkflowFoundationTest,WorkflowBoundaryTest,WorkflowDependencyPolicyTest,PermissionRevocationSlaTest,WorkspaceIsolationSecurityTest,SearchProjectionConsistencyTest,HighRiskOperationTest,BackgroundJobLifecycleTest" "-Dsurefire.failIfNoSpecifiedTests=false"
.\mvnw.cmd -pl modules/operations -am test
pnpm --filter @pdp/tests test:workflow-contracts
pnpm --filter @pdp/web test

$vite = "E:\AICoding\Projects\PDP\apps\web\node_modules\.bin\vite.cmd"
$server = Start-Process -FilePath $vite -ArgumentList "--host","127.0.0.1","--port","4178","--strictPort" `
  -WorkingDirectory "E:\AICoding\Projects\PDP\apps\web" -WindowStyle Hidden -PassThru
$env:PDP_WEB_URL = "http://127.0.0.1:4178"
& "E:\AICoding\Projects\PDP\tests\node_modules\.bin\playwright.cmd" test `
  e2e/high-risk-operation.spec.ts --config "E:\AICoding\Projects\PDP\tests\playwright.config.ts"
Stop-Process -Id $server.Id -Force
```

浏览器测试的实际执行脚本包含服务就绪轮询和 `finally` 清理；上面只保留可复现的核心命令。

## 6. 剩余风险与后续门禁

- MySQL 8.4 和 Flowable schema 的容器路径必须在具备 Docker 权限的 CI 中执行，不得用等价路径替代发布门禁。
- Redis、对象存储和病毒扫描目前通过稳定端口及内存适配验证；生产客户端、原子脚本和故障演练由后续基础设施任务完成。
- FR-165 月度汇总、排除项审批与可用性月报属于 US18；本阶段只确认原始采集口径。
- 任何后续模块引入 MySQL、Flowable 或跨工作空间直连，都必须由现有架构测试阻断。

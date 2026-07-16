# US1 工作空间与组织治理独立验收证据

## 验收结论

验收时间：2026-07-17（Asia/Shanghai）。

US1 的工作空间、组织、成员、角色、数据范围、跨空间授权及前端治理工作台已形成可独立验证的业务闭环。领域服务单元测试和前端质量门禁通过；公共工作空间变更集可被 XML 解析。由于当前执行环境无法连接 Docker Engine，尚未取得 MySQL 8.4 容器内的真实建表、索引和读写证据，因此本记录结论为：

**功能闭环通过，MySQL 实库闭环与发布级日志指标证据待具备运行环境后补验。**

## 范围与需求追踪

| 验收范围 | 需求 | 主要证据 |
|---|---|---|
| 工作空间创建、启用与归档阻断 | FR-003、FR-167 | `WorkspaceGovernanceServiceTest` |
| 独立组织、成员、角色和数据范围 | FR-004、FR-064 | `WorkspaceGovernanceServiceTest`、前端治理工作台 |
| 用户工作空间选择与明确上下文 | FR-005 | `WorkspaceGovernanceView.spec.ts`、API 请求的 `X-Workspace-Id` |
| 限时跨空间授权、撤销和平台保留动作 | FR-006、FR-121～FR-124 | `CollaborationGrantServiceTest`、`WorkspaceIsolationSecurityTest` |
| 撤权传播期限 | FR-068、FR-124、FR-164 | `PermissionRevocationSlaTest` 及 P1 的 5/30/30/60 秒期限定义 |
| 工作空间数据结构 | FR-130 | `010-workspace.xml` |

## 自动化验收结果

### 领域状态机与授权

执行命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.9+10'
.\mvnw.cmd -pl modules/workspace -am test
```

结果：构建成功。`workspace` 模块 5 个测试全部通过，覆盖：

- 草稿工作空间启用后创建组织、角色、数据范围和成员；
- 外部成员缺少到期时间时拒绝；
- 存在活动项目时拒绝归档并返回阻断原因；
- 跨空间授权不得包含平台保留动作；
- 授权撤销后新的权限判断立即失败，到期授权固化为 `EXPIRED`；
- 查询、搜索允许动作与导出、附件下载拒绝动作使用同一隔离守卫。

`WorkspaceGovernanceEvent` 已为授权创建、撤销和到期定义稳定事件类型、工作空间、对象、版本、原因和发生时间，可作为审计日志与指标维度。当前代码中尚未发现该发布端口的生产适配器及对应 Micrometer 指标，因此不能把“日志已入库、指标已采集”记录为已通过。

### 前端工作台

执行命令：

```powershell
pnpm --filter @pdp/web lint
pnpm --filter @pdp/web typecheck
pnpm --filter @pdp/web test
```

结果：

- ESLint 与 Stylelint 通过；
- Vue TypeScript 检查通过；
- 7 个测试文件、12 个测试全部通过。

页面支持工作空间切换、组织维护、成员角色与数据范围分配、角色动作集合维护、跨空间限时授权和带版本号撤销。写请求使用现有 API client，显式携带工作空间上下文、幂等键或 `If-Match`。

## MySQL 数据闭环证据与限制

公共变更集 `db/changelog/common/010-workspace.xml` 定义了：

- `pdp_workspace`
- `pdp_organization_unit`
- `pdp_workspace_role`
- `pdp_data_scope`
- `pdp_workspace_membership`
- `pdp_collaboration_grant`

已执行 XML 解析检查，结果为 `WORKSPACE_CHANGELOG_XML_OK`；公共根变更集通过 `includeAll` 包含该文件。

尝试执行 Docker 检查：

```powershell
docker info --format '{{.ServerVersion}}'
```

结果：无法访问 `npipe:////./pipe/docker_engine`，并出现 Docker 配置访问受限。因此 `LiquibaseMySqlMatrixTest` 所需的 MySQL 8.4 Testcontainers 环境不可用。本次未取得以下证据：

- MySQL 8.4 空库执行工作空间变更集；
- MySQL 实库唯一约束与索引验证；
- MySQL 上创建、授权、撤销后的持久化读写闭环；
- 回滚后重新应用的 MySQL 实证。

这属于环境限制，不应记录为测试通过。具备 Docker 权限后必须重新执行 MySQL 矩阵测试，并把容器版本、变更集数量、表与索引检查结果追加到本文件。

## 未决项与发布门禁

1. 补齐 `WorkspaceGovernanceEventPublisher` 生产适配器，确认授权创建、撤销、到期和越权拒绝进入审计链。
2. 增加工作空间授权创建量、撤销量、到期量、越权拒绝量和撤权传播延迟指标及告警阈值。
3. 当前定向后端安全测试复跑被并行开发中的 `domainconfig` 编译缺口阻断；待该模块恢复可编译后，重新执行 `WorkspaceIsolationSecurityTest` 与 `PermissionRevocationSlaTest`。
4. 在可用 MySQL 8.4 容器中完成 Liquibase 和仓储读写闭环后，方可把 US1 标记为发布级完全通过。

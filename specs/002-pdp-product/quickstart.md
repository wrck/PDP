# PDP P1 快速验证指南

## 1. 用途

本指南定义后续实现完成后必须可运行的本地验证和 P1 端到端验收。当前仓库仍处于规格与规划阶段，以下目录和命令是实施阶段需要建立的目标接口，不代表应用代码已经存在。

## 2. 前置条件

- Java 21、Maven Wrapper
- Node.js 24 LTS、pnpm
- Docker Desktop 或兼容的 Docker/Compose 环境
- 可供开发使用的 OIDC 身份提供方；本地可使用测试身份容器
- PostgreSQL 18、MySQL 8.4 测试目标库
- 独立 MySQL 5.7/8.x 历史源库和脱敏生产等价迁移数据集
- 至少 8 核 CPU、16 GB 内存，用于完整本地依赖和端到端测试

核心设计参见 [plan.md](plan.md)，持久化细节参见 [persistence-design.md](persistence-design.md)，技术选型参见 [technology-comparison.md](technology-comparison.md)，迁移方案参见 [mysql-migration.md](mysql-migration.md)，实体与状态参见 [data-model.md](data-model.md)，接口参见 [contracts/](contracts/)。

## 3. 启动本地依赖

PostgreSQL 目标配置：

```powershell
docker compose -f infra/compose/compose.yaml --profile postgresql up -d
docker compose -f infra/compose/compose.yaml ps
```

MySQL 目标配置：

```powershell
docker compose -f infra/compose/compose.yaml --profile mysql up -d
docker compose -f infra/compose/compose.yaml ps
```

每次只启用一个 PDP 目标数据库 profile；迁移场景另启独立的历史 MySQL 源库。预期还包含 S3 兼容对象存储、Redis 和测试身份服务；所有健康检查必须通过。

## 4. 构建与静态检查

后端：

```powershell
.\mvnw.cmd verify -Pdb-matrix
```

预期完成编译、格式检查、单元测试、模块依赖校验，并在 PostgreSQL 18 与 MySQL 8.4 上分别完成 Liquibase 变更集、MyBatis Mapper、持久化契约和 Testcontainers 集成测试。

前端：

```powershell
pnpm install --frozen-lockfile
pnpm --filter ./apps/web lint
pnpm --filter ./apps/web typecheck
pnpm --filter ./apps/web test
pnpm --filter ./apps/web build
```

根目录 `pnpm-workspace.yaml` 必须包含 `apps/web` 与 `tests`，根 `package.json` 统一锁定 Playwright、Spectral、AJV 和测试辅助依赖。前端 `typecheck` 必须调用 `vue-tsc --noEmit`，组件测试使用 Vitest、Vue Test Utils 和 Testing Library for Vue。

也可在前端目录单独执行：

```powershell
Set-Location apps/web
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

契约：

```powershell
pnpm exec spectral lint specs\002-pdp-product\contracts\openapi.yaml
pnpm exec ajv validate -s specs\002-pdp-product\contracts\domain-package.schema.json -d modules\standarddelivery\src\main\resources\domain-packages\standard-delivery.json
```

预期 OpenAPI 无错误，标准实施领域包样例通过 JSON Schema 校验。

## 5. 启动应用

分别打开两个终端。

PostgreSQL：

```powershell
.\mvnw.cmd -pl apps/api spring-boot:run -Dspring-boot.run.profiles=local,postgresql
```

MySQL：

```powershell
.\mvnw.cmd -pl apps/api spring-boot:run -Dspring-boot.run.profiles=local,mysql
```

```powershell
Set-Location apps/web
pnpm dev
```

预期后端健康接口为 `UP`，前端可通过测试身份登录，并在明确的工作空间上下文中运行。

## 6. P1 必测场景

### 场景一：工作空间与越权隔离

1. 创建工作空间 A、B，并分别添加管理员和项目成员。
2. 在 A 创建项目，验证 B 用户不能搜索、打开、导出或通过历史链接访问。
3. A 向 B 授予指定项目、角色和期限的协作授权。
4. 验证 B 只能执行授权动作，不能改变项目主工作空间或保留责任。
5. 撤销授权，验证新 API 请求立即失效、本地权限缓存 5 秒内失效、搜索投影 30 秒内移除、活动会话 1 分钟内撤销，审计记录完整。

通过标准：对应 SC-007、SC-008、SC-023、SC-036，越权拦截率、时限达标率和审计覆盖率均为 100%。

### 场景二：领域包深度定制与发布治理

1. 导入平台标准包和标准实施领域包。
2. 创建工作空间客户包，扩展项目字段、实施检查对象、页面区域、视图、状态转换、完成规则和审批映射。
3. 注入重复核心字段、不可达状态、循环规则、权限越界和三层以上继承等错误。
4. 验证发布前全部拒绝，并给出定位信息。
5. 使用不同审核人发布合法版本；创建运行实例后发布下一版本，验证旧实例仍固定原快照。
6. 执行迁移预览、分批迁移、失败暂停和回滚。

通过标准：对应 SC-011～SC-014、SC-022、SC-025；不修改平台核心即可表达业务差异。

### 场景三：标准实施项目端到端闭环

1. 从标准实施模板创建项目。
2. 验证工前准备、施工计划、实施方案、部署、验收和归档结构准确生成。
3. 在 5 分钟内建立不少于 10 个任务、2 个里程碑、检查项和依赖。
4. 批准计划基线，更新任务、必需检查项、里程碑和交付件。
5. 提交实施方案与验收交付件审批，完成内部/外部签核。
6. 创建风险、转化问题、发起变更并验证批准后的前后差异。
7. 尝试在必需产出未完成时推进阶段和关闭项目，验证被阻止。
8. 补齐条件并完成归档，验证交付件清单、版本和审计完整。

通过标准：对应 SC-002～SC-006、SC-024、SC-026，强制业务环节通过率 100%。

### 场景四：多视图与可解释进度

1. 在列表、看板、日历、时间线和详情中查看同一项目与任务。
2. 拖动看板状态并验证服务端权限、状态规则和乐观锁。
3. 检查进度值、基线、里程碑贡献、必需产出和阻塞项。
4. 发起经审批的临时人工进度调整并到期恢复。

通过标准：视图数据与权限一致率 100%，无无法解释的手工进度。

### 场景五：异步故障与降级

1. 暂停通知、搜索投影或外部集成消费者。
2. 完成任务、审批和交付件发布。
3. 验证核心事务成功，失败事件进入可见积压。
4. 恢复消费者并重提，验证幂等、无重复业务副作用。
5. 停止 Redis，验证核心读写可降级运行。

通过标准：对应 SC-015、SC-021，非关键能力故障不破坏核心事实。

### 场景六：并发、性能与恢复

1. 两个用户基于同一 `revision/ETag` 并发更新，验证后提交者收到冲突而不是静默覆盖。
2. 使用百万级项目、任务、审批、活动和审计测试数据运行性能脚本。
3. 使用 1000 个并发活跃用户执行常用页面和搜索场景。
4. 在应用副本故障时验证流量自动转移；执行数据库时间点恢复和对象版本恢复。
5. 按 FR-165 统计合格请求、成功请求、超时、排除项和责任方，验证核心与其他在线能力的月度可用性口径。

```powershell
pnpm exec k6 run tests/performance/p1-platform.k6.js
pnpm exec playwright test
```

通过标准：95% 页面不超过 2 秒，搜索/报表不超过 3 秒或先呈现渐进结果；RTO 不超过 30 分钟，RPO 不超过 5 分钟；可用性计算和排除项证据覆盖率 100%。

### 场景七：MySQL 历史数据迁移与切换

1. 向测试 MySQL 导入生产等价数据，包含无符号整数、`TINYINT(1)` 非布尔值、零日期、`ENUM/SET`、字符集冲突、重复业务键、孤儿关系、JSON、BLOB 和附件。
2. 执行源结构盘点和数据画像，验证所有表和字段具有风险等级及迁移结论。
3. 执行迁移预检和试运行，验证阻断问题进入隔离区，目标核心表不被污染。
4. 执行一致性全量迁移，检查 `LegacyKeyMap`、项目层级、审批链、交付件版本和附件哈希。
5. 在源 MySQL 持续新增、更新和删除数据，验证 CDC 从全量位点开始捕获且幂等应用。
6. 模拟切换：冻结 MySQL 写入、记录最终位点、应用最终增量、完成阻断核对、启用 PDP 写入并将旧系统保持只读。
7. 在启用 PDP 写入前模拟核对失败，验证可以安全恢复旧系统；启用 PDP 写入后验证系统拒绝无反向同步的直接回切。
8. 使用同一源快照和映射版本重跑迁移，验证不产生重复业务对象。

通过标准：

- 必迁表和字段映射覆盖率 100%，未决映射为 0；
- 阻断级问题为 0；
- 核心对象、关系、审批链和附件哈希核对通过率 100%；
- 正式冻结前 CDC 延迟连续 30 分钟不超过 30 秒，最终未应用变更为 0；
- 迁移后核心对象来源可追溯率 100%，重复业务结果为 0。

### 场景八：多数据库兼容与受控切换

1. 使用同一应用制品和同一领域包，分别以 `postgresql`、`mysql` profile 初始化空环境。
2. 在两种数据库上执行同一 P1 API、权限、状态机、审批、动态字段、排序分页、搜索、审计和并发测试。
3. 比较同一固定数据集的业务结果，确认 UUID、金额精度、UTC 时间、NULL 排序、大小写唯一性和分页游标一致。
4. 对所有 Mapper 执行工作空间越权、非法排序字段、SQL 注入、全表更新删除和 revision 冲突测试。
5. 注入不受支持版本、非 UTC 会话、MySQL 非 InnoDB 表或不合规排序规则，验证启动/升级预检快速失败。
6. 从 PostgreSQL 导出版本化标准迁移包，装载到全新 MySQL 目标，完成增量追平、冻结、业务核对和切换；再使用反向路径验证迁移工具不依赖单一数据库语法。
7. 在核对失败时保持原库写入主权；目标库启用写入后，验证系统阻止通过连接配置直接回切。

通过标准：对应 SC-032～SC-035；双数据库测试矩阵通过率 100%，跨数据库切换未应用变更、重复结果和阻断问题均为 0。

### 场景九：持久化、动态数据源与连接池

1. 配置 `pdpPrimary`、可选 `pdpRead`、`migrationSource` 和 `migrationTarget`，验证未知数据源键、缺失主库或不支持的数据库版本会阻止启动。
2. 验证在线命令只写入 `pdpPrimary`；可容忍延迟的查询可路由至 `pdpRead`，审批、权限判定、写后读和迁移门禁不得使用只读副本。
3. 在事务开始后尝试切换数据源，验证平台拒绝或保持原数据源；验证领域层和应用层不能直接引用 `@DS`、JDBC 数据源或数据库方言。
4. 同时运行在线业务和迁移任务，验证迁移源、目标和在线主库使用独立账号、独立 HikariCP 池及独立指标；迁移源和目标分别使用独立 `SqlSessionFactory`、Mapper 包和本地事务管理器，连接与持久化上下文不会交叉复用。
5. 注入连接池耗尽、连接泄漏、数据库重启、网络断连和只读副本延迟，验证在线请求快速失败或受控降级，迁移任务暂停且不会抢占在线连接预算。
6. 对签名 keyset cursor 执行翻页期间插入、删除、并发更新、游标篡改、筛选条件变化和权限变化测试，验证无重复、无越权且无页码偏移依赖。
7. 对 UUIDv7、UTC 时间、逻辑 JSON、稳定枚举和对象引用执行 PostgreSQL/MySQL `TypeHandler` 往返测试；验证未知枚举、非法 JSON 和溢出值显式失败。
8. 并发更新同一对象，验证 `revision` 条件更新影响行数为 0 时返回 409；批量更新不得绕过乐观锁。
9. 修改动态字段，验证约束与查询投影在同一事务保持一致，搜索与报表投影通过持久化事件最终追平；重放事件不得产生重复结果。
10. 停止 Spring Modulith 事件消费者后提交核心事务，验证 JDBC 事件发布记录与业务事务原子保存；恢复后按监听器重试并完成幂等消费。

通过标准：

- 路由正确率、跨事务误切换拦截率和类型往返一致率均为 100%；
- 连接池总预算不超过目标数据库可用连接数的 70%，池等待和超时均有指标与告警；
- 游标篡改拦截率、乐观锁冲突识别率和权限过滤率均为 100%；
- 同步投影无脏读，异步投影在约定 SLO 内追平，事件重放无重复业务副作用。

### 场景十：配置完成率与试点可用性

1. 由业务和领域负责人准备不少于 20 项具有代表性的已确认领域需求，覆盖字段、对象、关系、页面、状态、规则、权限、审批和集成映射。
2. 由受培训管理员仅使用领域包和受治理配置完成需求，不允许修改平台核心代码。
3. 记录完成、部分完成、需扩展和无法完成的分类、耗时、阻断原因及复核结论。
4. 选择不少于 20 名具有代表性的试点成员，不提供课堂培训，仅提供产品内页面引导。
5. 要求成员独立完成查看任务、更新进度、上传交付件和处理待办，并记录成功率、耗时和求助点。

通过标准：对应 SC-012、SC-019；配置完成率至少 80%，试点成员独立完成率至少 90%。

### 场景十一：权限撤销全链路时限

1. 同时建立 API 会话、本地权限缓存、搜索/报表结果、附件签名地址、导出任务和 WebSocket/SSE 连接。
2. 撤销角色、跨工作空间授权或停用用户，并记录撤销命令成功时间。
3. 验证新 API 请求立即使用最新权限，本地缓存 5 秒内失效。
4. 验证搜索和报表投影 30 秒内移除无权结果，且对象打开始终实时复核。
5. 验证附件实际下载、导出启动、下载地址生成和下载阶段均重新校验权限。
6. 验证 WebSocket/SSE 在 30 秒内刷新授权或断开，活动会话和刷新凭据在 1 分钟内撤销。

通过标准：对应 FR-124、FR-164、SC-036；所有入口时限达标率和越权拦截率均为 100%。

## 7. 测试与报告

```powershell
Set-Location tests
pnpm exec playwright test --project=chromium
pnpm exec playwright show-report
```

CI 必须保留以下证据：

- 后端、前端、模块边界和契约测试报告；
- P1 端到端场景报告及失败截图/trace；
- 权限矩阵、审计覆盖和领域包发布校验结果；
- 性能测试数据集说明、结果和瓶颈；
- 备份恢复、故障转移和事件补偿演练记录。
- MySQL 数据盘点、映射目录、两次彩排、核对、切换和回退边界报告。
- PostgreSQL/MySQL 双数据库契约、schema 升级、性能、恢复和跨数据库切换报告。
- 动态数据源路由、连接池容量与故障注入、游标、TypeHandler、乐观锁、投影一致性和 JDBC 事件恢复报告。
- 领域配置完成率、试点可用性、权限撤销时限和月度可用性口径报告。

只有当至少一个真实或等价标准实施项目完成从创建到归档，且所有 P1 强制门禁通过后，才可判定首次上线验收完成。

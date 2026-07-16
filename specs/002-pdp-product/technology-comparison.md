# PDP 前端、持久化框架与数据库技术对比

## 1. 结论摘要

- **前端已确定选择 Vue 3.5**。主要依据是细粒度响应式、Composition API、单文件组件、TypeScript 支持，以及动态表单和企业后台的开发效率。React 仍保留为比较对象，但不再是 PDP 实施备选。
- **持久化框架选择 MyBatis-Plus**。PDP 的复杂权限、动态对象、搜索报表和数据迁移更需要显式、可审查的 SQL。P1 不同时引入 Hibernate，避免双持久化上下文和事务语义。
- **工作流引擎选择 Flowable Process Engine**，并建设为平台公共基础能力。只启用 BPMN 流程引擎，
  向审批、项目、交付件、治理和领域包提供统一定义/运行/任务/管理端口；各领域事实继续由平台领域
  模型保存。
- **P1 只实现并认证 MySQL 8.4 LTS**。数据库独立通过领域/应用端口、逻辑类型和适配器边界实现，不等于首期同时交付两种数据库。
- PostgreSQL 在 `jsonb`、复杂索引和高级 SQL 上更灵活，作为 P2 首个候选适配器；MySQL 与既有运维体系及历史数据更接近，优先用于 P1。
- PostgreSQL 适配器、认证矩阵和 PostgreSQL/MySQL 受控切换进入 P2；P1 不支持通过修改连接字符串切换数据库或形成双主。

## 2. React 与 Vue 详细对比

### 2.1 基础定位

| 维度 | React 19.2 | Vue 3.5 | PDP 影响 |
|---|---|---|---|
| 产品定位 | UI 库，路由、状态和工程方案由团队组合 | 渐进式框架，官方生态更一体化 | React 自由度高但架构治理要求更高；Vue 默认路径更清晰 |
| UI 表达 | JSX/TSX，逻辑与视图使用 JavaScript 组合 | SFC + Template，也支持 JSX | 动态设计器和元数据渲染偏向 React；业务表单阅读偏向 Vue |
| 响应式模型 | 组件重新执行，依赖 Hook、状态结构和缓存策略 | 细粒度响应式，自动追踪 computed/watch 依赖 | Vue 对大型表单局部更新更自然；React 需严格控制状态边界 |
| TypeScript | JSX 与类型系统结合直接，生态类型丰富 | 官方一等 TypeScript 支持，SFC 需 `vue-tsc` | 两者均满足大型项目；React 工具链更统一，Vue 模板类型链更专用 |
| 状态管理 | Context、Reducer，以及多种社区方案 | 官方推荐 Pinia，Composition API 可直接抽取逻辑 | Vue 团队约定更容易统一；React 需明确服务器状态和客户端状态边界 |
| 逻辑复用 | 自定义 Hook，生态成熟 | Composable，调用一次且自动依赖追踪 | 两者都适合业务能力封装；Vue 心智负担通常更低 |
| 复杂组件生态 | 数据网格、拖拽、流程图、编辑器、可视化选择更广 | 常用企业组件充分，专业组件选择相对少 | PDP 的领域设计器、时间线和复杂工作区略偏 React |
| 企业组件库 | Ant Design、MUI、Arco、Semi 等 | Element Plus、Ant Design Vue、Naive UI、Arco Vue 等 | 两者均可；React 的大型企业案例与扩展数量更丰富 |
| 性能优化 | 需关注渲染边界、闭包、依赖和 memo；React Compiler 可降低部分负担 | 自动依赖追踪，细粒度更新，手工 memo 较少 | Vue 默认性能心智更轻；React 需要性能规范和剖析门禁 |
| 团队上手 | JSX、Hook 和状态闭包需要经验 | HTML/CSS/JS 分区清晰，学习曲线通常较平滑 | 国内企业团队若 Vue 经验占优，切换 React 的培训成本不可忽略 |
| 架构自由度 | 高，可按平台需要组合 | 中高，官方路线更明确 | React 适合平台团队定制基础设施；Vue 适合减少选型分歧 |
| 招聘与长期生态 | 全球生态和招聘面更大 | 国内生态、文档与培训体验较好 | 需结合团队所在地和供应商能力验证 |

React 官方强调组件化、显式状态结构和单一状态来源；Vue 官方说明 Composition API 更适合复杂逻辑复用、TypeScript 推断和长期扩展，并通过细粒度响应式减少手工依赖管理。[React 状态管理](https://react.dev/learn/managing-state)、[Vue Composition API](https://vuejs.org/guide/extras/composition-api-faq)

### 2.2 PDP 特定场景

| PDP 场景 | React 评价 | Vue 评价 | 倾向 |
|---|---|---|---|
| 领域包对象/页面设计器 | JSX 与组件注册表适合动态组件树；拖拽生态丰富 | 动态组件和渲染函数可实现，但高级设计器生态稍少 | React |
| 动态表单与字段规则 | 成熟表单库多，但需要控制重渲染和状态归属 | `v-model`、computed 和细粒度响应式开发直接 | Vue |
| 看板、甘特、时间线 | 专业库和 React 适配器选择多 | 可选库充足，但部分高级库先支持 React | React |
| 多工作区管理后台 | Ant Design Pro 类生态成熟 | Vue 管理后台模板和 Element Plus 生态成熟 | 持平 |
| 元数据驱动页面 | 函数式组合、Schema renderer 易扩展 | 动态组件、插槽和 SFC 也很自然 | 持平 |
| 外部扩展组件 | React 组件包和 headless 库选择更广 | Vue 插件体系清晰，但跨团队组件数量较少 | React |
| 大量表格和表单 | 需要规范化服务器状态、表单状态和虚拟列表 | 响应式开发体验更直接 | Vue |
| 团队治理 | 自由度会带来库选择分裂，需要平台级规范 | 官方路由、Pinia、SFC 路线减少分歧 | Vue |

### 2.3 评价矩阵

以下分数仅用于当前“团队结构未知”的默认判断，5 分为最匹配：

| 评价项 | 权重 | React | Vue |
|---|---:|---:|---:|
| 复杂设计器和专业组件生态 | 20% | 5.0 | 4.0 |
| 企业级组件与案例 | 15% | 5.0 | 4.5 |
| TypeScript 与重构能力 | 10% | 5.0 | 4.8 |
| 大型团队模块治理 | 15% | 4.5 | 4.5 |
| 动态表单开发效率 | 10% | 4.2 | 5.0 |
| 默认性能心智 | 10% | 4.0 | 5.0 |
| 招聘、供应商与长期生态 | 10% | 5.0 | 4.5 |
| 学习和培训成本 | 10% | 3.8 | 5.0 |
| **加权结果** | **100%** | **4.63** | **4.58** |

分值接近说明两者技术能力都能满足 PDP。结合用户明确决策，Vue 的团队一致性、表单效率和响应式开发优势优先于 React 的部分生态优势。

### 2.4 Vue 实施约束

正式开发前使用 Vue 完成以下技术原型：

- 动态字段和表单校验；
- 拖拽页面布局；
- 任务看板和时间线；
- 1000 行虚拟表格；
- 字段级权限和只读解释；
- 多语言；键盘等无障碍能力仅在 P3 可选子规格获批后评估。

记录开发工时、首屏和交互性能、缺陷数、包体积、测试覆盖及团队主观维护难度。

- 统一使用 Composition API 和 `<script setup lang="ts">`。
- Pinia 只保存跨页面客户端状态；接口数据、缓存和请求状态由 TanStack Vue Query 管理。
- 领域元数据通过受控组件注册表渲染，不允许后端下发任意模板或可执行脚本。
- 复杂组件优先选择 Vue 原生或框架无关实现；React 专属组件不得直接引入。
- 所有公共组件必须支持主题令牌、国际化、字段权限和只读解释；无障碍增强仅在 P3 可选子规格获批后纳入组件规范。
- 禁止同时维护 Vue 与 React 两套业务组件体系。

## 3. MyBatis-Plus 与 Hibernate 详细对比

### 3.1 定位差异

| 维度 | MyBatis-Plus | Hibernate | PDP 影响 |
|---|---|---|---|
| 核心定位 | 基于 MyBatis 的 SQL 映射与 CRUD 增强 | 完整 ORM 与有状态持久化上下文 | PDP 更强调 SQL 控制和读模型 |
| SQL 控制 | SQL、结果映射和执行时机显式 | HQL/Criteria/实体操作由 ORM 生成 SQL | 权限、搜索和报表使用 MyBatis 更易审查 |
| 基础 CRUD | `BaseMapper`、条件构造、分页和批量扩展 | `EntityManager`、Repository、级联和脏检查 | 两者均可减少样板代码 |
| 复杂关系 | 需要显式 JOIN 和 ResultMap | 实体关联、级联和抓取计划表达直接 | 稳定聚合偏 Hibernate，复杂读模型偏 MyBatis |
| 动态查询 | XML 动态 SQL、Wrapper 或自定义 Provider | Criteria、HQL、Specification 或原生 SQL | PDP 动态权限条件更接近 SQL 模型 |
| 多数据库 | 公共 SQL + `databaseId`/适配器维护差异 | 方言自动处理大量基础差异 | Hibernate 基础可移植性更强；复杂能力仍需双实现 |
| 性能可预测性 | SQL、列和 JOIN 显式，便于执行计划治理 | 需治理懒加载、N+1、抓取计划和 flush | 百万级查询下 MyBatis 风险更直观 |
| 聚合状态管理 | 更新显式，不维护实体快照 | 脏检查、一级缓存、级联和实体生命周期完整 | Hibernate 写模型开发体验更强 |
| 批量与迁移 | 显式批量 SQL 和流式装载容易控制 | 需控制持久化上下文大小和批量刷新 | PDP 历史迁移偏 MyBatis |
| 学习与排障 | 需要较强 SQL、索引和数据库能力 | 需要掌握实体状态、缓存和抓取策略 | PDP 无论选哪种都需要数据库能力 |
| Spring Boot 4 | 官方提供 Boot 4 Starter | 由 Spring Data JPA/Boot 生态集成 | 两者均有可行集成路径 |

MyBatis-Plus 官方提供 Spring Boot 4 Starter，并提供分页、乐观锁、租户、动态表名和阻止全表更新删除等插件；MyBatis 原生支持通过 `databaseIdProvider` 加载不同数据库厂商的语句。[MyBatis-Plus 快速开始](https://baomidou.com/en/getting-started/)、[MyBatis-Plus 插件](https://baomidou.com/en/plugins/)、[MyBatis 多数据库支持](https://mybatis.org/mybatis-3/configuration#databaseIdProvider)

Hibernate 的优势是实体关系、级联、脏检查、一级缓存和数据库方言。Hibernate 方言可以自动识别 PostgreSQL 与 MySQL，但复杂 JSON、全文检索、数据库专用索引和报表查询仍需要专门实现。[Hibernate 方言](https://docs.hibernate.org/orm/7.1/dialect/)

### 3.2 PDP 场景适配

| PDP 场景 | MyBatis-Plus | Hibernate | 倾向 |
|---|---|---|---|
| 工作空间与对象权限 | 权限 JOIN、EXISTS 和范围条件显式 | 需 Filter、Specification 或统一查询封装 | MyBatis-Plus |
| 动态字段与索引投影 | 可按类型化投影表编写精确 SQL | 动态 JSON 和通用实例关联映射较复杂 | MyBatis-Plus |
| 项目、任务稳定聚合 | 需要显式保存和更新 | 实体关联、级联和乐观锁方便 | Hibernate |
| 列表、待办和搜索 | 自定义列、JOIN、游标和执行计划直接 | DTO 投影可实现但需严格治理抓取 | MyBatis-Plus |
| 报表与批量导出 | SQL 和流式读取可控 | 通常最终使用投影或原生 SQL | MyBatis-Plus |
| MySQL 历史迁移 | 批量写入、幂等 upsert 和问题隔离直接 | 大批量实体装载需频繁 flush/clear | MyBatis-Plus |
| 后续数据库扩展 | 差异显式，适配器边界清楚 | 基础 CRUD 方言适配更省力 | Hibernate 略优 |
| 模块化单体领域隔离 | 通过仓储端口隔离 Mapper | 通过 Repository 隔离实体 | 持平 |

### 3.3 最终选择

P1 采用 MyBatis-Plus，Hibernate 不进入业务持久化栈：

- PDP 的主要风险集中在复杂权限、动态查询、批量迁移和执行计划，不在实体 CRUD。
- 业务模块只依赖自定义仓储和查询端口；`BaseMapper`、Wrapper、Mapper XML 与表对象均限制在基础设施层。
- 简单单表操作使用 MyBatis-Plus；复杂查询使用参数化 Mapper XML，不强行用 Wrapper 表达所有 SQL。
- P1 公共语句与 MySQL 专用语句分层；后续数据库通过独立适配器 Mapper 加入。
- 使用动态数据源组件登记受控连接并完成在线主库/只读库路由；迁移源库和迁移目标库使用各自独立的 `SqlSessionFactory`、Mapper 包和本地事务管理器，不与在线业务持久化上下文混用，也不用于绕过迁移流程实现数据库热切换。
- MyBatis-Plus 租户插件不能代替 PDP 权限模型，只可作为工作空间条件的纵深校验。
- 后续若稳定写聚合的复杂度显著高于 SQL 查询复杂度，可通过 ADR 评估 Hibernate，但不得在单个聚合或同一事务中混用两套实体状态模型。

### 3.4 关键安全与工程约束

- 禁止 `${}` 直接接收用户输入；排序字段、动态列和表名必须来自不可变白名单。
- 更新和删除必须同时包含主键、工作空间、权限范围和 `revision` 条件；启用阻止全表更新删除插件。
- 核心列表使用显式 keyset SQL 实现平台游标契约；MyBatis-Plus 分页插件仅可用于后台小数据集或非核心适配器查询，不向 API 暴露页码、offset 或数据库方言。
- Mapper 返回持久化记录或查询 DTO，不直接把数据库实体作为 API 模型。
- 每个 Mapper 方法在 MySQL 8.4 上运行契约测试，并检查执行计划和索引命中。
- 框架版本由项目 BOM 锁定；升级 MyBatis-Plus、MyBatis 或 SQL 解析插件必须运行完整 MySQL 回归。

### 3.5 动态数据源与连接池

P1 采用 `dynamic-datasource-spring-boot4-starter 4.5.0` 作为动态数据源路由基线，并使用 Spring Boot BOM 管理的 HikariCP 作为连接池。正式冻结版本前必须验证其与 Spring Boot 4.1、MyBatis-Plus 3.5.17、Liquibase 和 Spring Modulith 的组合兼容性。[动态数据源指南](https://baomidou.com/en/guides/dynamic-datasource/)、[HikariCP](https://github.com/brettwooldridge/HikariCP)

受控数据源键固定为：

| 数据源键 | 用途 | 写入规则 |
|---|---|---|
| `pdpPrimary` | 当前在线业务主库 | 唯一在线写入主权 |
| `pdpRead` | 可选只读副本 | 只允许可容忍延迟的查询 |
| `migrationSource` | 历史或跨数据库迁移源 | 只读提取和 CDC |
| `migrationTarget` | 迁移目标 | 仅迁移执行器在受控阶段写入 |

工程约束：

- 启用严格路由并将 `pdpPrimary` 设为默认数据源；未知键、缺失配置和错误环境必须启动失败。
- `@DS` 和路由上下文只能出现在基础设施适配器或迁移执行器边界；领域层、应用层和 Web 控制器不得选择数据源。
- 事务开始后禁止切换数据源。在线业务事务不得同时访问迁移源和迁移目标；P1 不引入 XA、Seata 或跨数据库两阶段提交。
- `migrationSource` 与 `migrationTarget` 分别配置独立 `SqlSessionFactory`、Mapper 扫描包和本地事务管理器；迁移 Mapper 与在线业务 Mapper 禁止交叉注入。
- 每个数据源拥有独立 HikariCP 池、账号、超时、只读属性和指标，禁止共享连接池或复用迁移高权限账号。
- 所有应用副本的池上限之和不得超过数据库可用连接数的 70%，预留管理、迁移、监控和故障恢复连接。
- 在线池连接获取超时初始值为 3 秒，迁移池为 10 秒；`maxLifetime` 必须低于数据库、代理或网络设备的连接回收上限，并启用连接存活检测。
- 连接池必须暴露活跃、空闲、等待、超时和创建失败指标；池耗尽或目标库故障时快速失败并触发告警，不允许无限排队。

## 4. PostgreSQL 与 MySQL 详细对比

### 4.1 版本基线

- PostgreSQL 18 当前正式支持至 2030 年 11 月，社区每个主版本支持 5 年。[PostgreSQL 版本策略](https://www.postgresql.org/support/versioning/)
- MySQL 8.0 仍可用，但 MySQL 已采用 LTS/Innovation 发布轨道；新建长期生产系统应优先 MySQL 8.4 LTS。MySQL LTS 提供 5 年 Premier Support 和 3 年 Extended Support。[MySQL LTS 说明](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)

### 4.2 能力对比

| 维度 | PostgreSQL 18 | MySQL 8.4 | PDP 影响 |
|---|---|---|---|
| 事务与 MVCC | 强事务、丰富隔离与约束能力 | InnoDB 成熟可靠 | 两者均能支撑核心 OLTP |
| SQL 标准和复杂查询 | CTE、窗口、LATERAL、丰富类型与扩展能力强 | 常用 SQL 完整，复杂查询能力持续增强 | 公共查询使用可移植子集，复杂读模型允许双实现 |
| JSON | `jsonb` 二进制存储，GIN、包含、JSONPath 等索引查询直接 | 原生 JSON；常通过生成列、函数索引或多值索引优化 | 统一为逻辑 JSON，热点查询进入类型化投影 |
| 行级安全 | 原生 RLS，可按角色/命令定义策略 | 无等价的通用原生 RLS，通常由应用或视图实现 | 应用授权是正确性来源；RLS 仅可作 PostgreSQL 纵深防御 |
| 全文检索 | 内置 `tsvector/tsquery`、排名、字典和 GIN/GiST | InnoDB FULLTEXT 可用 | 通过搜索端口提供双实现，结果和权限契约一致 |
| 索引 | B-tree、Hash、GIN、GiST、SP-GiST、BRIN、表达式和部分索引 | B-tree、FULLTEXT、SPATIAL、函数/生成列、多值索引 | 动态字段、稀疏条件和时间序列偏 PostgreSQL |
| 分区 | 声明式 Range/List/Hash，支持表达式与分区裁剪 | Range/List/Hash/Key，约束与分区键限制更多 | 审计、活动和事件按时间分区均可实现，PostgreSQL 更灵活 |
| UUID | 原生 `uuid` 类型并可生成 UUIDv7 | 通常使用二进制或字符列 | PDP 在应用层生成 UUIDv7，消除生成方式差异 |
| 字符集与排序 | ICU 排序规则、数据库级精细控制 | `utf8mb4` 生态成熟，默认排序规则需谨慎 | MySQL 迁移需重点处理大小写和唯一键差异 |
| 复制与高可用 | 流复制、逻辑复制、成熟云托管生态 | 异步复制、Group Replication、InnoDB Cluster | 两者可满足 HA，取决于运维平台 |
| 运维和团队普及 | 能力强，复杂参数和 SQL 需要经验 | 国内团队和既有系统普及度通常更高 | MySQL 可能降低短期人力风险 |
| 许可与生态 | PostgreSQL License，扩展生态丰富 | GPL Community 与商业版并存，需确认企业功能许可 | 采购与运维政策需单独评估 |
| 历史迁移 | 异构迁移，需要类型和语义转换 | 同构迁移，工具与风险最低 | 短期成本明显偏 MySQL |

PostgreSQL 的 `jsonb` 支持 GIN 索引、包含和 JSONPath 查询；MySQL 可以通过生成列或函数索引优化 JSON 字段，但需要显式定义索引表达式。PDP 将这些能力封装在适配器中，关键筛选、排序、唯一性和统计使用公共类型化投影，避免让业务规则依赖某一种 JSON 方言。[PostgreSQL JSON](https://www.postgresql.org/docs/current/datatype-json.html)、[MySQL 生成列索引](https://dev.mysql.com/doc/refman/8.4/en/generated-column-index-optimizations.html)

### 4.3 PDP 核心需求适配

| PDP 要求 | PostgreSQL | MySQL 8.4 | 统一实现策略 |
|---|---|---|---|
| 核心字段 + JSON 扩展字段 | 关系列与 `jsonb`/GIN 组合直接 | JSON + 生成列/函数索引可实现 | 逻辑 JSON + 类型化索引投影 |
| 动态对象通用模型 | JSONPath、数组、表达式索引丰富 | 需要更显式的索引设计 | 通用实例/关系表保持一致，查询由适配器实现 |
| 工作空间与数据范围隔离 | 应用授权 + 可选 RLS | 主要依赖应用授权 | 授权统一在应用查询端口，RLS 不承担正确性 |
| 百万级审计和事件日志 | BRIN、部分索引和分区灵活 | 分区和组合索引可满足 | 公共归档模型 + 数据库专用物理索引 |
| 复杂项目报表 | 高级 SQL 和物化视图更灵活 | 常用报表可满足 | 读模型接口允许双 SQL 实现并做结果契约测试 |
| 运维团队已有经验 | 适合已有 PostgreSQL 平台 | 适合已有 MySQL 平台 | 部署时按企业能力选择 |
| 历史 MySQL 数据迁移 | 异构物理转换更多 | 同产品物理转换较少 | 两者都必须经过业务映射和核对 |
| 未来多领域扩展 | 专有类型与扩展能力强 | 需要更严格的投影约束 | 新能力先定义公共语义，再增加适配器 |

### 4.4 数据库无关架构

```text
领域与应用模块
    ↓ 只依赖端口
持久化公共层
    ├─ 逻辑类型与序列化
    ├─ 仓储/查询/分页/锁契约
    ├─ 能力检测与启动校验
    └─ 公共 Liquibase 变更集
        └─ P1 MySQL 适配器与专用变更集

P2 增量：
        └─ PostgreSQL 适配器、专用变更集与认证矩阵
```

实现约束：

- P1 制品只装配 MySQL 适配器，不按数据库维护业务代码分支；P2 新增适配器时不得修改领域代码和外部契约。
- MyBatis-Plus 解决基础 CRUD、分页和乐观锁；复杂搜索、报表、批量写入和锁查询由显式 Mapper SQL 与数据库适配器实现。
- P1 Liquibase 变更分为 `common`、`mysql`，使用 `dbms=mysql` 隔离专用 DDL；P2 PostgreSQL 变更集加入时复用相同公共逻辑变更。[MyBatis 多数据库支持](https://mybatis.org/mybatis-3/configuration#databaseIdProvider)、[Liquibase `dbms` 属性](https://docs.liquibase.com/oss/reference-guide-4-33/changelog-attributes/dbms)
- UUIDv7 由应用生成；P1 逻辑 JSON 映射为 MySQL `JSON`；关键查询投影为文本、数字、时间、布尔和引用列。
- 会话时区固定 UTC，事务隔离显式设置；并发更新统一使用 `revision` 乐观锁，不依赖数据库默认行为。
- 业务唯一键先生成规范化值，避免大小写、重音、尾随空格和 NULL 语义由数据库默认排序规则决定。
- 存储过程、触发器和数据库函数不得承载平台业务规则；专有索引或查询只能作为可替换优化。

### 4.5 分阶段部署与切换边界

P1 与 P2 的边界如下：

- P1 新环境只部署 MySQL 8.4；历史 MySQL 进入 PDP 仍需执行业务迁移和核对。
- P2 增加 PostgreSQL 后，数据库变更必须执行全量、增量、核对、冻结、切换和回退门禁。
- 任一时刻仅一个数据库拥有写入主权；禁止通过修改连接字符串直接切换到另一库。
- 不同数据库之间不承诺物理 schema 完全相同，只承诺逻辑模型、API 和业务行为相同。
- PostgreSQL 适合希望利用高级 SQL、JSON/索引能力并具备相应运维体系的环境。
- MySQL 适合已有 MySQL 运维平台、希望降低历史迁移与技能成本的环境。

### 4.6 发布认证门禁

P1 每个 PDP 版本发布前必须：

- 在 MySQL 8.4 上从空库完成初始化，并从上一支持版本完成升级。
- 通过仓储、查询、权限、审计、并发、历史迁移和 P1 端到端测试。
- 使用百万级等价数据达到性能目标。
- 完成备份、时间点恢复、故障切换和 RTO/RPO 演练。
- 通过 ArchUnit/静态检查确认领域层和应用层不存在数据库专有依赖。

P2 PostgreSQL 子规格必须额外完成 PostgreSQL 全矩阵及 PostgreSQL ↔ MySQL 双向受控切换验证。

## 5. Flowable 工作流引擎选型与边界

### 5.1 采用原因

| 维度 | Flowable Process Engine | PDP 结论 |
|---|---|---|
| 标准 | 支持 BPMN 2.0 流程模型 | 统一表达审批路由、并行、等待、超时和补偿 |
| Spring 基线 | 官方支持 Spring Boot 4.x | 与 Spring Boot 4.1 计划相容，仍需组合验证 |
| Java 基线 | JDK 17+ | 与 Java 21 LTS 相容 |
| 嵌入方式 | 可通过 Process Engine starter 嵌入 | 适合模块化单体，不需独立流程平台 |
| 引擎范围 | 可分别选择 Process、CMMN、DMN 等 starter | P1 只启用 Process Engine，控制依赖和攻击面 |
| 持久化 | 引擎维护自身运行表 | 与 PDP 业务表隔离，P1 在 MySQL 8.4 验证 |
| 许可 | Apache License 2.0 | 适合开源基础引擎使用，仍需完成依赖与许可证扫描 |

参考：[Flowable Spring Boot 集成](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot/)、
[Flowable Open Source 说明](https://www.flowable.com/open-source/docs/oss-introduction/)、
[Flowable 8.0.0 API](https://www.flowable.com/open-source/docs/all-javadocs/)、
[OMG BPMN 2.0.2](https://www.omg.org/spec/BPMN/2.0.2/)。

### 5.2 P1 冻结方案

- 版本基线为 Flowable 8.0.x，项目 BOM 锁定经验证的补丁版本。
- 依赖仅使用 `flowable-spring-boot-starter-process`；禁止全引擎 starter、REST starter、IDM、
  CMMN、DMN 和 JPA/Hibernate 集成。
- `workflow` 平台模块提供定义、运行、人工任务和管理四类端口；业务模块不得引用 Flowable 类、
  表名、任务对象或异常类型。
- 流程定义使用稳定 key、业务版本、内容哈希和领域包版本；运行实例固定启动版本，迁移受控。
- Flowable 使用独立 schema/表前缀、账号、HikariCP 池和本地事务管理器；与业务库不使用 XA。
- 业务提交通过 outbox 幂等启动或推进流程；Flowable 重试不得重复形成审批结论或业务状态变化。
- 用户任务中的候选人只是路由提示，打开和办理时必须由 PDP 当前授权重新计算。
- 生产关闭自动 schema 更新；引擎 DDL 和升级脚本进入 MySQL 8.4 发布认证矩阵。

### 5.3 与自研及全平台 BPM 的取舍

| 方案 | 优点 | 主要风险 | 结论 |
|---|---|---|---|
| 自研审批路由 | 依赖少、领域控制直接 | 定时、并行、补偿、可视化诊断和迁移成本高 | 不采用 |
| Flowable 受限嵌入 | BPMN 能力成熟，边界可封装 | 需治理 schema、事务、异步执行和版本迁移 | 采用 |
| Flowable 全局业务运行时 | 所有状态可流程化 | 领域事实、查询、授权和迁移被引擎绑定 | 禁止 |
| 独立 Flowable REST 平台 | 部署解耦、API 完整 | 增加网络故障、重复身份授权和外部契约面 | P1 不采用 |

## 6. 最终建议

当前保持：

```text
前端：Vue 3.5 + Composition API + TypeScript + Vite 8
持久化：MyBatis-Plus + 显式 Mapper SQL，不引入 Hibernate
数据源：dynamic-datasource 4.5.0 受控路由 + 每数据源独立 HikariCP
数据库：P1 MySQL 8.4 LTS；PostgreSQL 适配与认证进入 P2
工作流：Flowable 8.0.x Process Engine + BPMN 2.0.2，平台基础模块 + 独立 `workflowEngine` HikariCP
迁移源：现有 MySQL
```

前端框架、主持久化框架和工作流引擎决策已经冻结。阶段 A0 验证 Vue 技术原型、MyBatis-Plus MySQL
Mapper 原型、复杂权限 SQL、Flowable MySQL 部署/升级/恢复和性能预算，不再进行 React/Vue、
MyBatis-Plus/Hibernate 或自研/Flowable 二选一。数据库冻结公共持久化契约和双认证基线。引入
Hibernate、新增第三种数据库、取消某个认证数据库、启用 Flowable CMMN/DMN/REST/IDM/JPA 或引入
运行时跨数据库分片，必须提交包含兼容、迁移、任务重排和验收变化的架构决策记录。

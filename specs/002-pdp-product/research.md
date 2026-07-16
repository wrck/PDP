# PDP 技术研究与决策

## 研究范围

本文件解决 `spec.md` 进入实施规划后需要明确的技术问题。结论服务于 P1“平台基础能力 + 标准实施领域包”，不改变 L0 产品范围。

## 决策 1：P1 采用模块化单体

**决定**：后端使用 Spring Boot 模块化单体，按业务能力划分模块，通过模块公开 API 和领域事件协作；应用保持无状态、多副本部署。

**理由**：P1 的工作空间、权限、领域配置、项目、计划、交付件和审批存在大量需要强一致提交的规则。模块化单体降低分布式事务和运维成本，同时可通过 Spring Modulith 校验依赖、进行模块级集成测试并生成模块文档。高吞吐后台任务可先独立部署执行器，未来再按事实容量拆分模块。

**备选方案**：

- 微服务：边界尚未经过真实业务验证，会过早引入网络、事务、部署和数据一致性复杂度。
- 传统分层单体：技术层分包容易造成跨域耦合，无法有效保护平台域与业务域边界。

## 决策 2：后端采用 Java 21、Spring Boot 4.1、Spring Modulith 2.1

**决定**：Java 21 LTS 作为基线；Spring Boot 4.1 提供运行框架；Spring Modulith 2.1 提供模块校验、模块测试和事件发布登记。

**理由**：Spring Boot 4.1 支持 Java 17 至 26，Java 21 提供成熟 LTS 基线。Spring Modulith 面向领域驱动的模块化应用，并能把事务事件监听记录与原业务事务一起持久化，失败事件可识别和重提，符合 FR-020、FR-062、FR-091 和 SC-015。

**备选方案**：

- Java 25：更新的 LTS，但企业依赖兼容与团队成熟度需额外验证；P1 选 Java 21 降低采用风险。
- Node.js 全栈：开发效率高，但当前复杂权限、强事务、领域模块和企业集成更适合现有 Java 生态。

**来源**：

- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Modulith 概览](https://docs.spring.io/spring-modulith/reference/index.html)
- [Spring Modulith 应用事件](https://docs.spring.io/spring-modulith/reference/events.html)

## 决策 3：前端采用 Vue 3.5、TypeScript 与 Vite 8

**决定**：Vue 3.5 + Composition API + TypeScript 严格模式 + Vite 8；Vue Router 管理路由，Pinia 管理客户端共享状态，TanStack Vue Query 管理服务器状态；Ant Design Vue 提供企业级基础组件，领域设计器、动态表单、看板和时间线在其上封装 PDP 组件。

**理由**：Vue 3.5 提供细粒度响应式、单文件组件、Composition API 和一等 TypeScript 支持，适合 PDP 大量动态表单、配置页面、权限驱动视图和复杂工作区。Composition API 可按业务关注点组织和复用逻辑，Pinia 提供统一且类型友好的客户端状态约定。采用 Vue 还能降低页面模板、表单开发和团队协作的心智负担。

PDP 主应用统一使用 Vue，不维护 React 业务组件。若某个专业组件仅提供非 Vue 实现，应优先选择框架无关的 Web Component、Canvas/SVG 库或在 Vue 中实现适配层，不得因此建立第二套应用框架。

**备选方案**：

- React 19：复杂组件生态更广，但项目已决定统一使用 Vue，避免双框架治理、人员分层和组件重复建设。
- Nuxt：适合 SSR 和全栈 Vue 应用，但 PDP 主要是登录后的企业客户端应用，P1 不需要引入 SSR 运行时。
- 低代码页面引擎完全接管 UI：会限制复杂交互、可访问性与性能；本项目只让领域元数据驱动受控页面区域。

**来源**：

- [Vue TypeScript 支持](https://vuejs.org/guide/typescript/overview)
- [Vue Composition API 说明](https://vuejs.org/guide/extras/composition-api-faq)
- [Vue 状态管理](https://vuejs.org/guide/scaling-up/state-management.html)
- [Vue 发布策略](https://vuejs.org/about/releases.html)
- [Vite 指南](https://vite.dev/guide/)
- [Vite 支持版本](https://vite.dev/releases)
- 详细对比见 [technology-comparison.md](technology-comparison.md)。

## 决策 4：持久化核心不绑定数据库，首批认证 PostgreSQL 18 与 MySQL 8.4 LTS

**决定**：每个部署从 PostgreSQL 18 或 MySQL 8.4 LTS 中选择一个业务事实真源，同一应用制品通过配置切换数据库适配器。核心字段使用规范化关系模型；可扩展对象使用逻辑 JSON、通用实例、关系和类型化索引投影表。领域层和应用层只依赖持久化端口，数据库差异集中在方言、映射、查询实现和 Liquibase 变更集中。

**理由**：PDP 面向私有化和不同企业基础设施，绑定单一数据库会扩大交付阻力和长期迁移成本。MyBatis 支持通过 `databaseIdProvider` 为不同数据库产品选择对应语句，MyBatis-Plus 的分页能力支持 PostgreSQL 和 MySQL，Liquibase 可按 `dbms` 区分公共和数据库专用变更集，因此可以在保持公共领域模型的前提下隔离数据库差异。PostgreSQL 在 `jsonb`、复杂索引和高级 SQL 上更灵活；MySQL 8.4 LTS 能可靠支撑 OLTP，并降低现有 MySQL 团队和历史迁移成本。两者能力差异应影响优化实现，不得影响产品功能和正确性。

运行中直接修改连接字符串并不能安全切换数据库，因为 schema 版本、事务位点、数据内容和写入主权无法自动一致。数据库切换被定义为受控迁移：以平台标准导出模型完成全量快照，以事务内迁移日志记录快照位点后的业务变更，再执行核对、冻结、最终追平、切换和回退门禁。原生 WAL/binlog 可作为适配器级加速，但不进入业务契约。任一时刻只允许一个写入主库；P1 不支持单实例跨数据库分片、在线双主或无迁移热切换。

**备选方案**：

- 完全 EAV：极度灵活，但类型约束、查询、索引和报表成本过高。
- 每个领域包独立建表：运行性能好，但发布、迁移、组合和客户覆盖治理复杂。
- 文档数据库作为主库：领域关系、审批一致性和跨对象报表不如关系数据库直接。
- 仅支持 PostgreSQL：实现和优化最直接，但不满足数据库不绑定与客户基础设施适配要求。
- 仅支持 MySQL：迁移与现有技能成本最低，但同样形成新的单数据库绑定。
- 抽象到所有关系数据库：范围过大且无法形成可信认证矩阵；P1 只承诺 PostgreSQL 与 MySQL，后续数据库必须通过同一适配器和发布门禁加入。

**来源**：

- [PostgreSQL 版本策略](https://www.postgresql.org/support/versioning/)
- [PostgreSQL 18 文档](https://www.postgresql.org/docs/current/)
- [PostgreSQL JSON](https://www.postgresql.org/docs/current/functions-json.html)
- [MySQL 8.4 JSON 生成列索引](https://dev.mysql.com/doc/refman/8.4/en/generated-column-index-optimizations.html)
- [MySQL 8.4 LTS 发布模型](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)
- [MyBatis 多数据库厂商语句](https://mybatis.org/mybatis-3/configuration#databaseIdProvider)
- [MyBatis-Plus 数据库支持](https://baomidou.com/en/introduce/)
- [Liquibase `dbms` 变更集属性](https://docs.liquibase.com/oss/reference-guide-4-33/changelog-attributes/dbms)
- 详细对比见 [technology-comparison.md](technology-comparison.md)。

## 决策 4.1：主持久化框架采用 MyBatis-Plus，不引入 Hibernate

**决定**：P1 以 MyBatis-Plus 3.5.17 Spring Boot 4 Starter 作为首个验证基线和唯一主持久化框架，正式实现时由项目 BOM 锁定经双数据库验证的补丁版本。简单单表操作使用受约束的 `BaseMapper` 能力，复杂查询、权限过滤、批量装载、搜索读模型和报表使用显式 Mapper SQL；业务模块只依赖自定义仓储或查询端口，不依赖 MyBatis-Plus 类型。Hibernate/JPA 不进入 P1 业务持久化栈。

**理由**：PDP 的主要持久化难点不是实体 CRUD，而是工作空间与对象级权限、动态字段投影、跨对象查询、审批待办、搜索、报表、批量迁移和 PostgreSQL/MySQL 双实现。MyBatis/MyBatis-Plus 能让 SQL、索引命中、锁范围和数据库差异保持显式，并通过 `databaseId` 选择厂商专用语句；MyBatis-Plus 还提供分页、乐观锁和阻止全表更新删除等基础插件。其 Spring Boot 4 Starter 已由官方提供。

Hibernate 对稳定聚合、关系映射、级联、脏检查和方言转换更便利，但动态模型和复杂权限查询仍需 HQL、Criteria 或原生 SQL；懒加载、抓取计划和持久化上下文还会增加性能与事务状态排查成本。在本项目同时使用两套框架会形成实体、Mapper、缓存、事务和测试双轨，收益不足以覆盖复杂度。

**实施约束**：

- `BaseMapper`、`IService`、Wrapper 和实体表注解只允许出现在基础设施层，不得成为领域服务或模块公开 API。
- 每个查询必须显式携带工作空间和授权范围；MyBatis-Plus `TenantLineInnerInterceptor` 不能替代 PDP 的跨工作空间协作、对象和字段权限。
- 公共 SQL 使用可移植子集；数据库专用语句使用 MyBatis `databaseId` 或独立 Mapper 适配器，不在业务代码中判断数据库类型。
- 乐观并发统一使用 `revision`；高风险更新同时校验主键、工作空间、当前 revision 和权限范围。
- 禁止使用 `${}` 接收用户输入；动态表名、排序字段和列选择必须来自平台白名单。
- Mapper SQL 必须在 PostgreSQL 与 MySQL Testcontainers 上执行契约、权限和性能测试。

**备选方案**：

- 仅 Hibernate/JPA：基础实体开发快、方言透明度较高，但复杂 SQL 与动态模型仍需适配，查询可预测性不符合当前优先级。
- Hibernate 写模型 + MyBatis 读模型：CQRS 边界成熟后可评估，P1 会过早引入双持久化框架和双测试体系。
- 原生 MyBatis：控制力充分，但 MyBatis-Plus 已能安全减少基础 CRUD、分页和乐观锁样板代码。

**来源**：

- [MyBatis-Plus Spring Boot 4 快速开始](https://baomidou.com/en/getting-started/)
- [MyBatis-Plus 插件体系](https://baomidou.com/en/plugins/)
- [MyBatis 动态 SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)
- [MyBatis `databaseIdProvider`](https://mybatis.org/mybatis-3/configuration#databaseIdProvider)
- [Hibernate 数据库方言](https://docs.hibernate.org/orm/7.1/dialect/)

## 决策 4.2：动态数据源采用严格受控路由，连接池统一使用 HikariCP

**决定**：首个验证基线采用 `dynamic-datasource-spring-boot4-starter 4.5.0`，设置 `primary=pdpPrimary`、`strict=true`。数据源键固定为业务主库 `pdpPrimary`、可选只读副本 `pdpRead`、迁移源 `migrationSource` 和迁移目标 `migrationTarget`。每个数据源使用独立 HikariCP 池，HikariCP 版本由 Spring Boot BOM 管理。

**理由**：历史 MySQL 迁移和 PostgreSQL/MySQL 数据库切换期间需要同时访问源库与目标库；部分后续报表可能需要只读副本。动态数据源能统一路由和连接生命周期，但若允许业务代码自由使用 `@DS`，会把数据主权、事务和读一致性变成隐式行为。因此路由只存在于基础设施层，事务入口确定后不可切换。HikariCP 配置简单、监控成熟，适合为不同用途建立独立连接预算。

P1 不使用 XA、JTA 或 Seata。跨数据库迁移使用只读源、目标批次事务、幂等批次键、检查点和业务核对。动态加载 `migrationSource`/`migrationTarget` 必须由迁移计划控制并审计；普通业务接口不能动态增加或删除数据源。

**连接池策略**：

- 所有应用副本、后台执行器和临时迁移池的连接上限之和不超过数据库可用连接的 70%。
- 在线主库池初始连接获取超时 3 秒；迁移池可放宽至 10 秒，但池容量更小。
- `maxLifetime` 小于数据库、代理或网络上限至少 30 秒，启用 TCP keepalive。
- 固定 `READ_COMMITTED`，`pdpRead` 和 `migrationSource` 标记只读。
- 监控 active、idle、pending、timeout、获取耗时、使用耗时和连接创建失败。

**备选方案**：

- 手工配置多个 `SqlSessionFactory`：边界最明确，但连接注册、路由、监控和临时迁移源管理样板较多。
- 动态数据源承载在线数据库热切换：无法保证数据位点、schema 和事务一致，不采用。
- XA/Seata：能协调多库事务，但迁移批次并不需要同步原子提交，复杂度和故障面过高。

**来源**：

- [MyBatis-Plus 多数据源说明](https://baomidou.com/en/guides/dynamic-datasource/)
- [dynamic-datasource Spring Boot 4 Starter](https://central.sonatype.com/artifact/com.baomidou/dynamic-datasource-spring-boot4-starter)
- [HikariCP 配置](https://github.com/brettwooldridge/HikariCP)

## 决策 4.3：统一游标、乐观锁、类型处理和数据库默认语义

**决定**：

- API 分页采用签名 keyset cursor，不以 MyBatis-Plus `Page/IPage` 作为外部契约。
- 所有自定义更新显式使用 `revision` 条件并执行 `revision + 1`；冲突返回 409。
- UUIDv7、Instant、JSON、稳定枚举和公共值对象使用统一 TypeHandler 与双数据库往返测试。
- MyBatis 二级缓存和懒加载关闭，`localCacheScope=STATEMENT`，未知列映射失败。
- PostgreSQL/MySQL JDBC 会话统一 UTC、`READ_COMMITTED`；业务唯一性使用应用规范值或摘要，不依赖默认 collation。

**理由**：MyBatis-Plus 的默认分页和乐观锁只覆盖部分内置方法，不能自动保证平台游标和所有自定义 SQL 的一致性。PostgreSQL/MySQL 在 UUID、JSON、日期时间、NULL、排序和默认隔离级别上存在差异，必须在持久化契约中消除，而不是依靠开发人员逐查询记忆。

详细契约见 [persistence-design.md](persistence-design.md)。

## 决策 5：搜索与报表先使用数据库读模型

**决定**：P1 使用平台统一分析器生成 `SearchDocument` 和 `SearchTermProjection`，统一词项、字段权重和稳定排序；搜索索引更新由事务事件驱动。PostgreSQL `tsvector`/GIN、MySQL FULLTEXT 或其他数据库索引只作为候选加速，最终权限过滤、结构化过滤、匹配集合和排序由平台搜索契约决定。只有在任一认证数据库的真实压测无法满足 SC-018 时，才引入 OpenSearch 等独立搜索集群。

**理由**：独立搜索引擎会增加权限同步、最终一致性和运维成本。P1 的百万级目标可以先通过组合索引、分区、预计算、异步导出和渐进结果验证。

**备选方案**：

- 首期部署 OpenSearch：能力充足，但在权限模型未稳定前会形成第二套数据授权面。
- 所有报表实时扫描事务表：实现简单，但会影响核心业务和 3 秒目标。

## 决策 6：领域定制采用双通道扩展

**决定**：

1. 声明式通道：对象、字段、关系、状态、动作、表单、页面、视图、规则、权限和集成映射。
2. 受治理扩展通道：签名制品、声明权限、稳定扩展 API、隔离执行、超时和资源配额。

**理由**：只提供字段和表单配置无法承载专业业务；允许任意脚本或直连数据库又会破坏安全与升级。双通道使 80% 常规需求由管理员配置，其余专业算法仍能扩展，但必须服从平台身份、权限、事务、审计和版本规则。

**备选方案**：

- 任意脚本：灵活但不可治理，难以保证性能、安全和升级。
- 只允许平台团队开发：安全但响应慢，无法满足客户级细化。

## 决策 7：核心生命周期与统一审批由平台实现

**决定**：P1 不引入通用 BPM 引擎作为核心依赖。项目/任务/交付件状态机由领域元数据定义并映射统一平台状态；审批使用独立聚合，业务对象通过审批策略与回写动作接入。

**理由**：项目生命周期、业务状态与审批状态必须分离。通用 BPM 容易把业务事实埋入流程实例，削弱统一查询、版本迁移和领域包组合。平台状态机覆盖 P1，复杂编排后续可通过集成或受治理扩展接入。

**备选方案**：

- Camunda/Flowable 作为全局运行时：适合复杂长流程，但会使所有对象依赖流程引擎。
- 每个模块自建审批：会造成待办、委托、审计和权限规则不一致。

## 决策 8：事务事件日志保证异步一致性

**决定**：状态更新、权限校验和审计写入在同一事务完成；通知、搜索投影、统计、集成和非关键自动化使用 Spring Modulith JDBC 事件发布登记。事件表由 Liquibase 管理，生产关闭自动 schema 初始化；每个监听器维护独立发布状态。消费者必须幂等，失败进入可观测重试与人工补偿。

**理由**：满足“外部故障不破坏已完成核心事务”和“失败可见可追踪”。P1 不强制消息代理；当模块拆分或吞吐证明需要时，再把选定事件外部化到 Kafka/AMQP。

**备选方案**：

- 同步调用所有下游：失败会扩大核心事务故障面。
- 仅内存事件：进程崩溃会丢失通知、索引或集成任务。

## 决策 9：企业身份与细粒度授权分离

**决定**：认证优先通过 OIDC 接入企业身份提供方，保留 SAML/目录同步适配层；PDP 自主管理工作空间成员、角色、数据范围、临时授权、字段权限和协作授权。

**理由**：外部 IdP 负责“用户是谁”，PDP 负责“在当前工作空间、项目和对象上能做什么”。权限判定在服务端统一执行，前端隐藏按钮仅用于体验。

**备选方案**：

- 直接把企业组织角色等同 PDP 权限：无法表达项目参与、客户、区域、对象属性和临时授权。
- 仅 RBAC：不能满足 FR-064 的数据范围和属性条件。

## 决策 10：API、配置和事件均契约优先

**决定**：同步接口使用 REST/JSON 与 OpenAPI 3.1；领域包使用 JSON Schema 2020-12；异步事件使用稳定事件信封与版本号。所有写接口使用统一错误模型，并按风险支持幂等键和乐观锁。

**理由**：机器可读契约可生成客户端、执行兼容性检查并减少外部集成歧义。OpenAPI 已定义语言无关的 HTTP API 描述标准。

**备选方案**：

- GraphQL 作为唯一入口：灵活查询有价值，但复杂字段授权、缓存和批量命令需要额外治理；可在后续读场景评估。
- 无版本内部 API：会阻碍前后端独立演进和外部集成。

**来源**：

- [OpenAPI 规范](https://spec.openapis.org/oas/latest.html)

## 决策 11：文件内容与业务元数据分离

**决定**：文件内容存入 S3 兼容对象存储；数据库保存对象关联、版本、内容哈希、密级、扫描状态、保留状态和签核关系。上传使用分段/预签名机制，下载前执行授权并签发短时地址。

**理由**：避免大文件占用业务数据库，同时保留完整的业务版本、审计和访问控制。对象存储不可替代数据库中的交付件事实。

**备选方案**：

- 数据库存二进制：事务简单但备份、扩容和大文件吞吐成本高。
- 仅存外部 URL：无法保证版本、保留、病毒扫描和撤权。

## 决策 12：高可用以无状态多副本和数据库连续保护为核心

**决定**：生产至少三个应用副本跨故障域部署；数据库采用主备自动切换与连续归档，对象存储开启版本和冗余；入口执行健康检查、限流和滚动发布。搜索、报表、通知、扩展和非关键集成均有明确降级。

**理由**：99.95% 月度可用性约束要求避免单实例和长时间人工恢复。RPO 5 分钟要求数据库日志持续保护，而不仅是每日备份。

**备选方案**：

- 双应用副本：单节点维护或故障时容量余量不足。
- 仅依赖虚拟机快照：无法稳定达到 5 分钟数据恢复点。

## 决策 13：统一可观测性

**决定**：使用 OpenTelemetry 语义生成 trace、metric 和结构化日志；所有请求、后台作业、审批、领域包发布、迁移和集成任务携带 `traceId`、工作空间、对象和操作类型。敏感值不得进入日志。

**理由**：模块化单体仍需定位跨模块调用、慢查询、事件积压和权限拒绝；统一上下文也支持审计与运行日志关联。

**备选方案**：

- 各模块自定义日志：无法统一查询或建立 SLO。

## 决策 14：测试采用分层质量门禁

**决定**：

- 单元测试覆盖规则、计算和状态转换。
- 模块测试验证模块边界与模块内集成。
- Testcontainers 对 PostgreSQL 18 与 MySQL 8.4 执行同一套持久化契约、schema 升级、并发和模块集成测试，并独立验证 MySQL 历史迁移源、Redis 和对象存储适配。
- 契约测试验证 OpenAPI、JSON Schema 和事件兼容性。
- Playwright 覆盖 P1 角色化端到端场景。
- k6、故障注入和恢复演练验证性能与连续性。

**理由**：PDP 最大风险在权限、配置发布、迁移、并发和故障恢复，不能只依赖页面测试。Playwright 支持并行、隔离和多浏览器端到端验证。

**备选方案**：

- 仅端到端测试：反馈慢且难以定位模块规则错误。
- 大量模拟外部依赖：无法发现真实数据库约束、事务和序列化问题。

**来源**：

- [Playwright 运行测试](https://playwright.dev/docs/running-tests)
- [Playwright TypeScript 测试](https://playwright.dev/docs/test-typescript)

## 决策 15：版本、迁移和兼容策略

**决定**：API 采用路径主版本；领域包采用语义化版本和不可变发布制品；配置项使用稳定键而非显示名称引用；数据库迁移只前向执行，回滚以应用版本回退加补偿迁移为主。运行实例默认固定创建时快照。

**理由**：PDP 必须同时支持平台升级、领域包升级和存量项目长期运行。不可变版本与稳定键是差异、冲突、追溯和安全迁移的基础。

**备选方案**：

- 原地修改已发布配置：会使历史实例不可解释。
- 自动迁移所有实例：高风险且不符合 FR-018、FR-019。

## 决策 16：MySQL 历史迁移采用暂存层、全量加 CDC 和业务核对

**决定**：迁移分为盘点、映射、全量快照、持续增量、转换装载、业务核对、冻结切换和只读保留。MySQL 原始数据先进入隔离暂存层，再由版本化映射转换为 PDP 核心命令或受控批量装载；目标可以是当前部署选定的 PostgreSQL 或 MySQL。数据库搬运工具不得直接向生产核心表决定业务语义。

**理由**：即使源和目标都是 MySQL，旧系统与 PDP 仍存在无符号整数、`TINYINT(1)`、零日期、字符集/排序规则、时区、`ENUM/SET`、JSON、BLOB、自增主键、存储过程和业务语义差异；目标为 PostgreSQL 时还增加类型和 SQL 方言转换。可靠搬运数据并不等于业务迁移正确。暂存层能保留原始证据、隔离问题并支持幂等重跑；全量加 CDC 可以缩短最终冻结窗口；业务核对用于证明项目层级、审批链、状态和附件未被破坏。

全量提取可使用 MySQL Shell 一致性 dump、数据库迁移服务或并行 JDBC 导出；低停机增量可使用 MySQL binlog CDC。云环境可评估 AWS DMS，平台无关环境可评估 Debezium。工具选择必须通过生产等价数据验证，不写入产品核心契约。

**备选方案**：

- 一次停机全量迁移：数据量小且批准窗口足够时更简单，可作为降级方案。
- DMS 或 CDC 直接写核心表：传输快，但无法充分处理领域语义、状态、权限和新旧标识映射。
- 长期双写：冲突、顺序和补偿复杂度过高，P1 不采用。
- 继续让新平台直接读旧 MySQL：会形成双真源并阻碍权限、审计和领域模型统一。

**来源**：

- [MySQL Shell 一致性导出](https://dev.mysql.com/doc/mysql-shell/8.0/en/mysql-shell-utilities-dump-instance-schema.html)
- [Debezium MySQL Connector](https://debezium.io/documentation/reference/stable/connectors/mysql.html)
- [AWS DMS MySQL 源](https://docs.aws.amazon.com/dms/latest/userguide/CHAP_Source.MySQL.html)
- [AWS DMS PostgreSQL 目标](https://docs.aws.amazon.com/dms/latest/userguide/CHAP_Target.PostgreSQL.html)
- [AWS DMS MySQL 目标](https://docs.aws.amazon.com/dms/latest/userguide/CHAP_Target.MySQL.html)
- 详细方案见 [mysql-migration.md](mysql-migration.md)。

## 研究结论

所有技术背景中的不确定项已转化为明确决策。Vue 作为前端基线保持不变；数据库采用 PostgreSQL/MySQL 双认证而非单库选型。后续新增数据库、改变认证版本或引入跨数据库分片，必须通过子规格或架构决策记录和完整兼容性测试，不应静默扩大本 L0 计划。

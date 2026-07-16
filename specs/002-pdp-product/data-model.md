# PDP P1 数据模型

## 1. 建模原则

- 每个部署从认证的 PostgreSQL 或 MySQL 中选择一个业务事实真源；缓存、搜索投影和报表读模型均可重建。
- 核心对象使用规范化表和统一核心字段，保证跨领域权限、搜索、审计和统计口径一致。
- 核心对象扩展数据使用版本化逻辑 JSON；领域新对象使用通用实例模型。频繁使用且稳定的领域对象可经治理后提升为专用模块。
- 所有业务记录使用由应用生成的 UUIDv7 主键，并包含 `workspace_id`、`created_at/by`、`updated_at/by`、`revision`。跨工作空间共享不改变主工作空间。
- 显示名称允许变化，配置引用必须使用不可变 `stable_key`。
- 金额使用定点数和币种；时间保存 UTC 并记录业务时区；百分比和权重保存明确精度。
- 普通业务删除使用归档或受控处置；审计、发布配置、审批、签核和已发布交付件在保留期内不可物理删除。

### 1.1 跨数据库逻辑类型

领域模型只使用以下逻辑类型，物理映射由数据库适配器负责：

| 逻辑类型 | PostgreSQL 18 | MySQL 8.4 | 统一规则 |
|---|---|---|---|
| `UUIDv7` | `uuid` | `BINARY(16)` | 应用生成和解析，不使用数据库序列或自增键作为平台主键 |
| `Boolean` | `boolean` | `boolean`/`tinyint(1)` | 持久化层只接受真/假，不接受任意整数 |
| `Instant` | `timestamp with time zone` 或规范化 UTC 时间戳 | UTC `datetime(6)` | JDBC 会话固定 UTC，业务时区单独保存 |
| `Decimal` | `numeric(p,s)` | `decimal(p,s)` | 精度、舍入和溢出规则由领域模型定义 |
| `JsonDocument` | `jsonb` | `JSON` | 关键筛选、排序和统计字段进入类型化索引投影，不依赖专有 JSON 查询保证正确性 |
| `Text` | `varchar/text` | `varchar/longtext` | 长度和 UTF-8 规则统一，禁止依赖数据库隐式截断 |
| `BinaryRef` | 对象存储引用 | 对象存储引用 | 大文件不进入业务主库 |

UUIDv7 在 MySQL 中使用 RFC 4122 网络字节序写入 `BINARY(16)`，不得使用驱动或函数的私有重排格式。JSON 使用统一 Jackson `JsonNode` 模型；需要哈希、签名或比较时先生成规范化 JSON。枚举保存稳定键，禁止保存 ordinal。

业务键、搜索词和唯一性比较使用版本化应用规范值；数据库排序规则仅作为物理实现。分页、NULL 排序、大小写、锁和事务隔离必须通过持久化契约测试保持一致。MyBatis 二级缓存和懒加载关闭，单次语句之外不共享本地查询结果。

### 1.2 数据库与并发默认语义

- PostgreSQL/MySQL JDBC 会话均使用 UTC 和 `READ_COMMITTED`。
- PostgreSQL 使用 UTF-8 和批准的可重建 ICU collation，不强制数据库扩展。
- MySQL 核心表使用 InnoDB、`utf8mb4`、批准的 `utf8mb4_0900_bin` 或等价确定性排序规则，并启用严格 `sql_mode`。
- 所有修改使用 `revision` 条件并在成功时递增；影响行数为 0 时区分无权/不存在与版本冲突。
- 外部分页游标包含排序值、UUIDv7 兜底键、过滤与权限摘要，并签名防篡改；数据库类型不得进入游标。

## 2. 公共值对象

| 值对象 | 主要字段 | 规则 |
|---|---|---|
| `ActorRef` | `actor_type`、`actor_id`、`display_snapshot` | 支持用户、组织、角色、外部参与者和系统执行身份 |
| `ObjectRef` | `object_type_key`、`object_id`、`workspace_id` | 必须能解析到有权对象 |
| `TimeRange` | `start_at`、`end_at`、`timezone` | 结束时间不得早于开始时间 |
| `Money` | `amount`、`currency` | P1 仅用于变更影响等预留字段，不承担正式会计核算 |
| `VersionRef` | `package_id`、`version_id`、`snapshot_hash` | 运行实例必须可追溯到不可变发布版本 |
| `AccessScope` | `scope_type`、`scope_id`、`condition` | 条件必须通过受控表达式校验 |
| `FileRef` | `file_id`、`version_id`、`content_hash` | 文件内容与业务元数据分离 |

## 3. 身份、工作空间与权限

### 3.1 `UserAccount`

字段：`id`、`external_subject`、`display_name`、`email`、`status`、`locale`、`timezone`、`last_login_at`。

关系：一个用户可拥有多个 `WorkspaceMembership`。

状态：`INVITED → ACTIVE → SUSPENDED → DISABLED`。停用后会话和授权缓存必须失效，未完成责任允许重新分配。

### 3.2 `Workspace`

字段：`id`、`code`、`name`、`owner_user_id`、`status`、`default_locale`、`default_timezone`、`data_classification_policy_id`。

关系：拥有组织、成员、角色、领域包安装、模板和项目。

状态：`DRAFT → ACTIVE → SUSPENDED → ARCHIVED`。存在活动项目时不得直接归档。

### 3.3 `OrganizationUnit`

字段：`id`、`workspace_id`、`parent_id`、`code`、`name`、`type`、`region_code`、`path`、`status`。

规则：同一工作空间内 `code` 唯一；父子关系不得成环。

### 3.4 `WorkspaceMembership`

字段：`id`、`workspace_id`、`user_id`、`organization_id`、`membership_type`、`status`、`valid_from`、`valid_until`。

规则：有效期外不得用于授权；外部成员必须有明确到期时间。

### 3.5 `RoleDefinition` / `PermissionGrant`

`RoleDefinition` 字段：`stable_key`、`name`、`role_type`、`version`、`status`。

`PermissionGrant` 字段：`role_id`、`capability_key`、`object_type_key`、`operation`、`field_scope`、`data_scope`、`effect`。

规则：显式拒绝优先于允许；字段、附件、导出、自动化和集成必须复用相同授权决策服务。

### 3.6 `CollaborationGrant`

字段：`owner_workspace_id`、`collaborator_workspace_id`、`target_ref`、`role_id`、`allowed_actions`、`valid_from`、`valid_until`、`granted_by`、`status`。

状态：`DRAFT → ACTIVE → EXPIRED/REVOKED`。

规则：不得授予改变主工作空间、数据保留责任、核心模板归属和平台安全策略的能力；撤销后历史链接和缓存立即失效。

## 4. 领域包与配置元模型

### 4.1 `DomainPackage`

字段：`id`、`stable_key`、`name`、`layer`、`owner_workspace_id`、`parent_package_id`、`description`、`status`。

`layer`：`PLATFORM_STANDARD`、`INDUSTRY`、`WORKSPACE_CUSTOMER`。

规则：继承最多三层，客户包只能继承行业包或平台标准包；不得形成循环。

### 4.2 `DomainPackageVersion`

字段：`id`、`package_id`、`semantic_version`、`content_hash`、`manifest`、`base_version_id`、`status`、`designed_by`、`reviewed_by`、`published_at`。

状态：`DRAFT → VALIDATING → REVIEW_PENDING → PUBLISHED → DEPRECATED → RETIRED`，校验失败进入 `REJECTED` 后回到草稿。

规则：设计人与发布审核人不得相同；已发布内容不可修改；发布前必须完成结构、引用、状态、规则、权限、迁移和回滚校验。

### 4.3 配置定义

| 实体 | 关键字段 | 关键规则 |
|---|---|---|
| `ObjectTypeDefinition` | `stable_key`、`kind`、`core_object_type`、`title_field_key` | `kind` 为 `CORE_EXTENSION` 或 `NEW_OBJECT` |
| `FieldDefinition` | `stable_key`、`data_type`、`required`、`unique_scope`、`default_value`、`sensitive`、`index_mode` | 不得与核心字段语义冲突；类型变更必须提供迁移 |
| `RelationDefinition` | `source_type`、`target_type`、`cardinality`、`ownership`、`required` | 不得形成禁止的所有权循环 |
| `StateDefinition` | `stable_key`、`top_lifecycle_state`、`terminal`、`order` | 每个运行阶段必须映射唯一顶层生命周期 |
| `TransitionDefinition` | `from_state`、`to_state`、`action_key`、`guard_rule`、`required_permission` | 不可达状态和无授权路径禁止发布 |
| `RuleDefinition` | `event`、`condition`、`actions`、`execution_identity`、`sync_mode` | 禁止递归、越权和不可控外部副作用 |
| `PageDefinition` | `route_key`、`layout`、`sections`、`visibility_rule` | 只能引用有权字段和受支持组件 |
| `ViewDefinition` | `view_type`、`columns`、`filters`、`grouping`、`sort` | 列表、看板、日历和时间线共享同一查询口径 |
| `ExtensionDefinition` | `artifact_id`、`entrypoint`、`permissions`、`timeouts`、`resource_limits` | 必须签名、隔离并通过稳定 API 调用 |

### 4.4 `PackageOverride`

字段：`target_stable_key`、`property_path`、`old_value`、`new_value`、`reason`、`changed_by`、`source_version_id`。

规则：只允许覆盖平台声明的扩展点；不得改写核心身份、归属、权限、审计、版本和保留动作。

### 4.5 `PackageInstallation` / `RuntimeSnapshot`

`PackageInstallation` 记录工作空间安装的包及当前可创建实例版本。

`RuntimeSnapshot` 字段：`object_ref`、`package_version_id`、`resolved_manifest`、`snapshot_hash`、`created_at`。

规则：运行实例默认固定快照；升级不得直接改变历史事实。

### 4.6 `MigrationJob`

字段：`source_version_id`、`target_version_id`、`scope`、`mapping`、`dry_run_report`、`status`、`progress`、`rollback_checkpoint`、`failure_detail`。

状态：`DRAFT → PREVIEWING → READY → RUNNING → COMPLETED`，异常进入 `PAUSED/FAILED/ROLLING_BACK/ROLLED_BACK`。

## 5. 动态业务数据

### 5.1 核心对象扩展

核心表包含逻辑字段 `extension_data JsonDocument`、`extension_schema_version_id`。适配器分别映射为 PostgreSQL `jsonb` 或 MySQL `JSON`；保存时按对应 `FieldDefinition` 校验类型、必填、唯一、敏感和引用规则。

需要筛选、排序或统计的字段进入 `ExtensionIndexProjection`：

`object_ref`、`field_stable_key`、`value_text`、`value_number`、`value_time`、`value_boolean`、`value_ref`。

仅允许一个类型列有值。用于唯一性、权限、状态规则、流程条件、列表筛选、排序和游标的投影必须与业务对象在同一事务更新；只用于全文搜索、报表和推荐的投影可由事务事件异步重建，但响应必须显示数据截止时间且权限始终回查业务对象。

动态唯一字段额外保存 `normalization_version`、`normalized_value` 和 `normalized_hash`，在 `(workspace_id, object_type_key, field_stable_key, normalized_hash)` 上建立唯一约束；摘要冲突时回查规范值。

### 5.2 `DynamicObjectInstance`

字段：`id`、`workspace_id`、`object_type_version_id`、`runtime_snapshot_id`、`business_key`、`title`、`state_key`、`top_lifecycle_state`、`data JsonDocument`、`owner_actor`、`revision`、`status`。

关系：通过 `DynamicObjectRelation` 连接核心或动态对象。

规则：`business_key` 按定义范围唯一；状态转换必须命中已发布转换；对象类型停用不影响已有实例读取。

### 5.3 `DynamicObjectRelation`

字段：`definition_id`、`source_ref`、`target_ref`、`sequence`、`attributes`。

规则：满足基数、所有权和删除策略；跨工作空间关系必须有显式授权。

### 5.4 `SearchDocument`

字段：`object_ref`、`workspace_id`、`analyzer_version`、`title`、`normalized_text`、`field_weights`、`indexed_revision`、`indexed_at`、`status`。

规则：由平台统一分析器构建；数据库全文索引只加速候选查询，不改变权限、最终匹配集合和稳定排序。

### 5.5 `SearchTermProjection`

字段：`object_ref`、`term`、`field_key`、`term_frequency`、`field_weight`、`analyzer_version`。

规则：相同分析器版本在 PostgreSQL 与 MySQL 上必须产生相同词项；相关度相同按业务时间和 UUIDv7 排序。

## 6. 模板、项目与计划

### 6.1 `ProjectTemplate` / `ProjectTemplateVersion`

模板字段：`stable_key`、`name`、`domain_package_version_id`、`status`。

版本字段：默认阶段、任务、负责人规则、工期、里程碑、检查项、交付件、审批、视图及内容哈希。

规则：项目创建只引用已批准不可变版本；实例化结果必须保存模板版本和领域包快照。

### 6.2 `Project`

字段：`id`、`workspace_id`、`parent_project_id`、`project_no`、`name`、`objective`、`scope`、`customer_ref`、`contract_ref`、`manager_id`、`priority`、`health`、`top_lifecycle_state`、`domain_stage_key`、`planned_range`、`actual_range`、`template_version_id`、`runtime_snapshot_id`、`progress`、`progress_calculated_at`、`status`。

顶层生命周期：`PRE_PLANNING → PLANNING → EXECUTING → ACCEPTING → SERVICING → CLOSED`，可从非终态转为 `CANCELLED`；受控回退必须记录原因和审批。

规则：项目仅有一个主工作空间；父子项目不得成环；关闭前必须满足必需任务、交付件、问题、审批和子项目门禁。

### 6.3 `ProjectStage`

字段：`project_id`、`stable_key`、`name`、`top_lifecycle_state`、`state`、`owner_id`、`planned_range`、`actual_range`、`entry_rule`、`exit_rule`、`required_outputs`、`sequence`。

规则：同一领域子阶段唯一映射一个顶层生命周期；未满足退出条件不得推进。

### 6.4 `ProjectMember`

字段：`project_id`、`actor_ref`、`project_role_key`、`valid_range`、`data_scope`、`source`。

规则：责任人必须是当前有效成员或经明确授权的外部参与者。

### 6.5 `Task` / `ChecklistItem`

`Task` 字段：`project_id`、`parent_task_id`、`stage_id`、`title`、`assignee_id`、`participant_ids`、`priority`、`state`、`planned_range`、`actual_range`、`planned_effort`、`actual_effort`、`blocked_reason`、`progress`。

任务状态：`DRAFT → READY → IN_PROGRESS → BLOCKED → COMPLETED`；允许 `CANCELLED/ARCHIVED`，解除阻塞回到前一可执行状态。

`ChecklistItem` 字段：`task_id`、`text`、`required`、`state`、`evidence_ref`、`completed_by/at`。

规则：必需检查项未完成时任务不得完成。

### 6.6 `Milestone` / `Dependency`

`Milestone` 字段：`project_id`、`stage_id`、`name`、`weight`、`planned_at`、`actual_at`、`owner_id`、`completion_rule`、`state`。

`Dependency` 字段：`predecessor_ref`、`successor_ref`、`type`、`lag_minutes`。

规则：项目有效里程碑权重总和为 100%；未满足必需产出不得完成；依赖图不得成环。

### 6.7 `PlanBaseline` / `ProgressSnapshot`

`PlanBaseline` 字段：`project_id`、`version`、`snapshot`、`status`、`approval_id`、`approved_at`。

状态：`DRAFT → APPROVAL_PENDING → APPROVED → SUPERSEDED`。

`ProgressSnapshot` 字段：`project_id`、`baseline_id`、`calculated_value`、`milestone_contributions`、`blocking_items`、`calculated_at`。

规则：手工调整另存 `ProgressOverride`，记录原值、调整值、原因、期限和审批人，不改变底层事实。

## 7. 交付、审批与治理

### 7.1 `Deliverable` / `DeliverableVersion`

`Deliverable` 字段：`project_id`、`type_key`、`name`、`owner_id`、`required_stage_id`、`state`、`current_version_id`、`due_at`。

交付件状态：`DRAFT → REVIEWING → APPROVED → PUBLISHED → ARCHIVED`，可 `REJECTED` 后修订。

`DeliverableVersion` 字段：`deliverable_id`、`version_no`、`file_version_id`、`metadata`、`content_hash`、`created_by/at`、`publication_status`。

规则：已发布版本不可覆盖；修订必须创建新版本；签核必须绑定确切版本。

### 7.2 `Signature`

字段：`deliverable_version_id`、`signer_actor`、`signature_type`、`decision`、`signed_at`、`evidence`、`verification_status`。

规则：签核时保存签核人身份快照和内容哈希。

### 7.3 `ApprovalDefinition` / `ApprovalInstance` / `ApprovalStep`

定义字段：`stable_key`、`applicable_object_types`、`steps`、`routing_rules`、`version`。

实例字段：`business_object_ref`、`definition_version_id`、`applicant_id`、`state`、`submitted_at`、`completed_at`、`result`。

节点字段：`step_key`、`assignee_rule`、`actual_assignees`、`state`、`decision`、`comment`、`attachments`、`handled_at`。

审批状态：`DRAFT → PENDING → APPROVED/REJECTED/WITHDRAWN/CANCELLED`；节点支持退回、转交、委托、加签和抄送。

规则：审批展示的数据必须再次经过当前审批人的对象与字段权限；状态回写要求业务对象版本匹配。

### 7.4 `Risk` / `Issue` / `ChangeRequest`

`Risk`：概率、影响、等级、负责人、措施、触发条件、目标日期、状态，可转为问题。

`Issue`：来源、类型、严重程度、负责人、关联对象、方案、关闭结论、状态。

`ChangeRequest`：范围/工期/资源/成本/风险/交付影响、差异快照、审批、应用结果、状态。

通用状态：`OPEN → ANALYZING → TREATING → RESOLVED → CLOSED`，允许记录原因后 `REOPENED/CANCELLED`。

规则：批准变更必须在同一受控操作中更新关联对象并保留前后差异。

## 8. 协作、文件、审计与异步作业

### 8.1 `Comment` / `Follow` / `Activity`

均关联 `ObjectRef`；评论支持提及和附件。活动时间线是业务事件的可读投影，不替代不可修改审计。

### 8.2 `FileObject` / `FileVersion`

`FileObject`：工作空间、业务关联、分类、密级、保留策略、状态。

`FileVersion`：对象存储键、版本号、大小、媒体类型、内容哈希、病毒扫描状态、上传人和时间。

状态：`UPLOADING → SCANNING → AVAILABLE → QUARANTINED/ARCHIVED/DISPOSED`。

### 8.3 `AuditEvent`

字段：`occurred_at`、`actor_ref`、`workspace_id`、`action`、`target_ref`、`result`、`reason`、`before_digest`、`after_digest`、`trace_id`、`source_ip`、`metadata`。

规则：只追加、不可由普通业务接口修改；敏感值保存摘要或脱敏快照。

### 8.4 `EventPublication`

字段：`publication_id`、`event_id`、`event_type`、`event_version`、`listener_id`、`aggregate_ref`、`payload`、`status`、`publication_date`、`completion_attempts`、`last_resubmission_date`、`completion_date`、`last_error`、`next_retry_at`。

状态：`PUBLISHED → PROCESSING → COMPLETED`，失败进入 `FAILED`，重提进入 `RESUBMITTED`。

规则：同一业务事件面向每个监听器形成独立发布记录；事件表由 Spring Modulith JDBC 仓储使用，并由 Liquibase 管理 schema。

### 8.5 `BackgroundJob`

字段：`job_type`、`scope`、`requested_by`、`status`、`progress`、`checkpoint`、`failure_items`、`result_file_id`、`started_at`、`finished_at`。

状态：`QUEUED → RUNNING → COMPLETED`，允许 `PAUSED/FAILED/CANCELLED`。

适用：批量导入、导出、归档、统计、领域迁移、搜索重建和集成补偿。

## 9. 数据库部署与 MySQL 历史数据迁移

`DatabaseDeploymentProfile` 记录当前部署的数据库认证事实。领域包运行实例迁移使用前述 `MigrationJob`；旧 MySQL 到 PDP 以及 PDP 认证数据库之间的系统级迁移使用以下独立实体，避免把配置版本迁移和数据平台迁移混为一谈。

### 9.1 `MigrationProgram`

字段：`id`、`stable_key`、`name`、`source_system_id`、`target_database_type`、`target_database_version`、`target_platform_version`、`scope`、`owner_id`、`business_approver_id`、`status`、`planned_cutover_at`、`rollback_deadline`、`created_at`。

状态：`DRAFT → INVENTORY → MAPPING → REHEARSING → READY → CUTTING_OVER → STABILIZING → COMPLETED`，可进入 `PAUSED/CANCELLED`。

规则：进入 `READY` 前必须完成映射审批、至少两次生产等价彩排、阻断问题清零和回退演练。

### 9.2 `DatabaseDeploymentProfile`

字段：`database_type`、`database_version`、`jdbc_driver_version`、`schema_version`、`character_set`、`collation`、`timezone`、`transaction_engine`、`isolation_level`、`capabilities`、`validation_status`、`validated_at`。

规则：配置只保存非敏感部署事实，连接凭据使用密钥引用；启动时实际探测结果必须与认证能力矩阵一致。数据库类型只允许认证值，P1 为 `POSTGRESQL` 或 `MYSQL`。

### 9.3 `DataSourceRegistration`

字段：`data_source_key`、`role`、`database_type`、`connection_secret_ref`、`pool_profile`、`read_only`、`status`、`loaded_at`、`expires_at`、`migration_program_id`。

`role`：`PDP_PRIMARY`、`PDP_READ`、`MIGRATION_SOURCE`、`MIGRATION_TARGET`。

规则：`PDP_PRIMARY` 唯一且始终存在；迁移源/目标必须绑定迁移计划和有效期，卸载后撤销连接与网络权限。该实体记录受控运行事实，不保存明文连接信息。

### 9.4 `SourceSystem`

字段：`id`、`system_key`、`name`、`database_engine`、`database_version`、`topology`、`timezone`、`character_set`、`collation`、`change_capture_mode`、`source_position_retention`、`connection_secret_ref`、`status`。

规则：凭据只保存密钥引用；迁移结束后必须撤销写权限和临时网络授权。

### 9.5 `SourceObjectInventory`

字段：`source_system_id`、`schema_name`、`object_name`、`object_type`、`storage_engine`、`primary_key`、`row_count`、`data_bytes`、`daily_change_count`、`character_set`、`collation`、`has_trigger`、`has_procedure_dependency`、`risk_level`、`profile_result`。

规则：所有纳入或排除的源对象都必须有盘点记录；无主键、高变更、大字段和非 InnoDB 表自动提升风险级别。

### 9.6 `MigrationMapping`

字段：`program_id`、`mapping_version`、`source_object`、`source_field`、`target_object_type`、`target_field_key`、`mapping_type`、`transform_expression`、`default_policy`、`validation_rule`、`reconciliation_rule`、`owner_id`、`status`。

状态：`DRAFT → REVIEW_PENDING → APPROVED → SUPERSEDED`。

规则：不迁移的表和字段也必须使用 `EXCLUDE` 映射类型并记录原因；已用于正式运行的映射版本不可修改。

### 9.7 `MigrationRun`

字段：`id`、`program_id`、`run_type`、`mapping_version`、`source_snapshot_id`、`start_position`、`end_position`、`status`、`started_at`、`finished_at`、`total_records`、`loaded_records`、`quarantined_records`、`failed_records`、`cdc_lag_seconds`、`report_file_id`。

`run_type`：`PROFILE`、`DRY_RUN`、`FULL_LOAD`、`CDC`、`DATABASE_SWITCH`、`RECONCILIATION`、`CUTOVER`、`ROLLBACK_REHEARSAL`。

状态：`QUEUED → RUNNING → VALIDATING → COMPLETED`，允许 `PAUSED/FAILED/CANCELLED`。

规则：相同源快照、映射版本和批次键重复执行不得创建重复目标业务对象。

### 9.8 `MigrationBatch`

字段：`run_id`、`source_object`、`batch_key`、`range_start`、`range_end`、`source_count`、`target_count`、`status`、`checkpoint`、`checksum`、`attempts`、`last_error`。

规则：支持断点续传和独立重跑；批次完成后记录源/目标计数和校验摘要。

### 9.9 `LegacyKeyMap`

字段：`source_system_id`、`source_schema`、`source_table`、`source_key`、`target_object_type`、`target_object_id`、`migration_run_id`、`mapping_version`、`created_at`。

唯一性：

- `(source_system_id, source_schema, source_table, source_key)` 唯一；
- `(target_object_type, target_object_id, source_system_id)` 唯一。

规则：迁移后的核心对象必须可通过该表追溯；业务 API 不向普通用户暴露内部映射。

### 9.10 `MigrationChange`

字段：`source_system_id`、`source_position`、`source_transaction_id`、`source_object`、`source_key`、`operation`、`occurred_at`、`before_data`、`after_data`、`status`、`applied_target_ref`、`attempts`、`last_error`。

状态：`CAPTURED → TRANSFORMED → APPLIED`，异常进入 `QUARANTINED/FAILED/IGNORED`。

规则：`source_position + source_transaction_id + source_object + source_key + operation` 保证幂等；同一源对象按提交顺序应用。

### 9.11 `MigrationIssue`

字段：`program_id`、`run_id`、`source_ref`、`issue_code`、`severity`、`category`、`original_value`、`description`、`proposed_resolution`、`owner_id`、`status`、`resolution`、`resolved_by/at`。

严重程度：`BLOCKER`、`HIGH`、`MEDIUM`、`LOW`。

状态：`OPEN → ASSIGNED → RESOLVED/ACCEPTED_EXCLUSION`。

规则：正式切换前 `BLOCKER` 必须为 0；人工修复保留前值、后值、原因和复核人。

### 9.12 `ReconciliationResult`

字段：`run_id`、`rule_key`、`scope`、`source_value`、`target_value`、`difference`、`tolerance`、`result`、`evidence_file_id`、`checked_at`、`approved_by`。

`result`：`PASS`、`FAIL`、`WAIVED`。

规则：核心对象数量、主键关系、项目层级、审批链和附件哈希不允许容差；豁免必须有业务和审计批准。

### 9.13 `CutoverDecision`

字段：`program_id`、`decision_type`、`checkpoint`、`decision`、`evidence_summary`、`decided_by`、`decided_at`。

`decision_type`：`GO_NO_GO`、`OPEN_TARGET_WRITES`、`ROLLBACK`、`FORWARD_FIX`、`DECOMMISSION_SOURCE`。

规则：开放目标数据库写入必须记录所有门禁结果；开放后如无反向同步能力，不得批准直接恢复源数据库写入。

## 10. 关键唯一性与索引

- `workspace(code)` 全局唯一；工作空间内项目编号唯一。
- `domain_package(stable_key, layer, owner_workspace_id)` 唯一；版本号和内容哈希唯一。
- 配置版本内 `stable_key` 唯一，引用统一使用稳定键。
- 项目按 `workspace_id + lifecycle + manager + updated_at` 建组合索引。
- 任务按 `project_id + state + assignee_id + planned_end` 建组合索引。
- 审批按 `actual_assignee + state + submitted_at` 建待办索引。
- 审计和活动由适配器采用按月分区或等价时间分片归档；大型后台作业和事件日志按时间归档。
- 扩展索引只为已发布且声明 `index_mode` 的字段创建，防止无限索引膨胀。
- 迁移变更按 `source_system_id + source_position` 建顺序索引；迁移问题按 `program_id + severity + status` 建门禁索引。
- `LegacyKeyMap` 按源标识和目标标识双向唯一索引，确保追溯和幂等。

## 11. 跨聚合一致性

- 工作空间归属、权限、状态合法性、关键审计和事件登记必须在同一事务提交。
- 项目进度是可重建快照，不允许直接覆盖任务、里程碑和交付件事实。
- 审批批准后使用对象 `revision` 执行条件更新；冲突时不得静默覆盖，须重新校验或重新审批。
- 外部集成以幂等键、事件编号和版本识别重复、乱序、过期及冲突。
- 缓存、搜索、报表和活动投影失败不回滚核心业务，但必须进入积压监控和补偿队列。
- MySQL 迁移原始数据只能追加；转换和装载使用映射版本、运行编号和批次键保证可重放。
- 正式切换时必须先冻结源数据库写入并应用最终增量，再开放目标数据库写入；禁止无治理的双主写入。

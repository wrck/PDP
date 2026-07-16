# PDP 配置指南

**适用范围**：PDP P1 企业级项目交付管理平台
**追溯来源**：`specs/002-pdp-product/plan.md`（“目标平台”、“存储”）、`specs/002-pdp-product/persistence-design.md`（动态数据源拓扑、数据库默认配置）、`.specify/memory/constitution.md`（原则 VI 最小授权、隔离、审计与数据治理）
**关联文件**：`.env.example`、`infra/compose/compose.yaml`、`infra/k8s/base/`、`docs/security/security-baseline.md`

## 1. 概述

PDP 通过环境变量注入运行时配置。本指南定义各配置分组、变量含义、本地与生产差异，以及安全边界。

- 本地开发与集成测试：所有变量集中由仓库根目录 `.env` 提供，编排文件为 `infra/compose/compose.yaml`。
- 配置模板：`.env.example` 列出全部变量及占位默认值，**禁止填入真实凭据后提交**。
- 生产环境：**禁止使用 `.env` 文件**，必须由外部密钥管理服务（KMS/Vault）注入凭据，非敏感配置通过 ConfigMap 注入（见 `infra/k8s/base/configmap.yaml`）。

所有凭据与签名密钥在生产环境只保存外部密钥引用，不进入代码、配置明文、环境变量明文、日志、APM 或仓库（详见 `docs/security/security-baseline.md` 第 3、4 节）。

## 2. 配置分组

### 2.1 Spring Profile

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | 激活的 Spring Profile。本地为 `dev`；生产为 `prod`，并加载生产专属的连接池、安全与可观测配置。 |

`apps/api/src/main/resources/application.yml` 通过 `${SPRING_PROFILES_ACTIVE:dev}` 读取。

### 2.2 在线业务事实真源（`pdpPrimary`，MySQL 8.4 LTS）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_MYSQL_HOST` | `pdp-mysql` | 业务主库主机。本地指向 compose 服务名；生产为托管 MySQL 地址。 |
| `PDP_MYSQL_PORT` | `3306` | 业务主库端口。 |
| `PDP_MYSQL_DATABASE` | `pdp` | 业务 schema。 |
| `PDP_MYSQL_USER` | `pdp` | 应用账号，**仅拥有 DML 权限，无 DDL 权限**；schema 由 Liquibase 管理。 |
| `PDP_MYSQL_PASSWORD` | `CHANGE_ME` | 应用账号口令。 |
| `PDP_MYSQL_ROOT_PASSWORD` | `CHANGE_ME` | 仅本地 compose 初始化与建库使用；生产不使用 root 账号接入应用。 |

对应数据源键 `pdpPrimary`，写命令、审批、权限判定、审计、事件发布和读后写一致性查询必须访问此库。MySQL 8.4 基线：`utf8mb4` + `utf8mb4_0900_bin`、时区 UTC、`STRICT_TRANS_TABLES`，由 `compose.yaml` 的 `command` 强制（对齐 `persistence-design.md` 第 11 节）。

### 2.3 工作流引擎数据源（`workflowEngine`，独立 schema/账号）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_MYSQL_WORKFLOW_HOST` | `pdp-mysql-workflow` | Flowable 引擎库主机。 |
| `PDP_MYSQL_WORKFLOW_PORT` | `3307` | Flowable 引擎库端口。 |
| `PDP_MYSQL_WORKFLOW_DATABASE` | `pdp_workflow` | 独立 schema，与业务表隔离。 |
| `PDP_MYSQL_WORKFLOW_USER` | `pdp_workflow` | 独立账号，仅 `workflow` 模块使用。 |
| `PDP_MYSQL_WORKFLOW_PASSWORD` | `CHANGE_ME` | 工作流账号口令。 |
| `PDP_MYSQL_WORKFLOW_ROOT_PASSWORD` | `CHANGE_ME` | 仅本地 compose 初始化使用。 |

`workflowEngine` 使用独立 schema/账号/HikariCP 池/事务管理器，**不通过 `@DS` 选择，不加入普通业务动态路由**；业务 Mapper 不得访问 Flowable 表，Flowable 引擎也不得直接更新 PDP 业务表。生产关闭 Flowable 自动建表与自动升级，引擎 DDL 由版本化脚本管理。

### 2.4 历史 MySQL 迁移源/目标（`migrationSource` / `migrationTarget`）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_MYSQL_LEGACY_HOST` | `pdp-mysql-legacy` | 历史 MySQL 5.7 源库主机。 |
| `PDP_MYSQL_LEGACY_PORT` | `3308` | 历史源库端口。 |
| `PDP_MYSQL_LEGACY_DATABASE` | `legacy_pdp` | 历史源库 schema。 |
| `PDP_MYSQL_LEGACY_USER` | `legacy_pdp` | 历史源库账号，只读。 |
| `PDP_MYSQL_LEGACY_PASSWORD` | `CHANGE_ME` | 历史源库账号口令。 |
| `PDP_MYSQL_LEGACY_ROOT_PASSWORD` | `CHANGE_ME` | 仅本地 compose 初始化使用。 |

`migrationSource` 使用独立 `SqlSessionFactory`、Mapper 扫描包、本地事务管理器和 HikariCP 池，源事务只读；`migrationTarget` 仅迁移执行器写入，开放业务写入前不得作为 `pdpPrimary`。迁移源/目标与业务 Mapper 不共用会话工厂或事务管理器，**不使用 XA**。本地 compose 通过 `--read-only` 模拟历史系统冻结后的只读语义。

### 2.5 Redis（缓存、限流、短期协调）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_REDIS_HOST` | `pdp-redis` | Redis 主机。 |
| `PDP_REDIS_PORT` | `6379` | Redis 端口。 |
| `PDP_REDIS_PASSWORD` | （可选，默认空） | 本地开发默认不启用；如需启用，在 `.env` 取消注释并设置。 |

Redis 仅用于缓存可重建数据、限流和短期协调，**不得作为业务真源**；缓存内容必须可在 MySQL 主库失效后完整重建。Redis 不得存储业务凭据、明文密码、OIDC 令牌、签名 HMAC 密钥或审计摘要链密钥（详见 `docs/security/security-baseline.md` 第 3 节）。

### 2.6 S3 兼容对象存储

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_OBJECT_STORAGE_ENDPOINT` | `http://pdp-minio:9000` | 对象存储 API 端点。本地为 MinIO；生产为托管 S3 兼容服务。 |
| `PDP_OBJECT_STORAGE_ACCESS_KEY` | `CHANGE_ME` | 访问密钥。 |
| `PDP_OBJECT_STORAGE_SECRET_KEY` | `CHANGE_ME` | 私有密钥。 |
| `PDP_OBJECT_STORAGE_BUCKET` | `pdp-attachments` | 附件与交付件文件内容桶名。 |

文件内容保存在对象存储，文件元数据（`file_id`、`version_id`、`content_hash`、授权关系）保存在数据库。生产必须启用服务端加密（SSE-KMS 或等价能力），加密密钥由外部 KMS/Vault 托管。附件下载使用 5 分钟短时签名 URL，下载前再次校验对象与字段权限。

### 2.7 OIDC 企业身份集成

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_OIDC_ISSUER_URL` | （空） | OIDC IdP 签发者 URL。 |
| `PDP_OIDC_CLIENT_ID` | （空） | OIDC 客户端 ID。 |
| `PDP_OIDC_CLIENT_SECRET` | `CHANGE_ME` | OIDC 客户端密钥。 |

授权码流程必须校验 `state` 与 `nonce`，令牌交换使用 PKCE，授权码一次性消费，回调地址必须在 IdP 白名单内（详见 `docs/security/security-baseline.md` 第 2 节）。

### 2.8 签名密钥

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PDP_AUDIT_SIGNING_KEY` | `CHANGE_ME` | 审计摘要链/哈希链签名密钥。 |
| `PDP_CURSOR_SIGNING_KEY` | `CHANGE_ME` | keyset cursor 的 HMAC 签名密钥。 |

游标使用 Base64URL 编码和 HMAC 签名，不包含数据库类型、表名或原始 SQL。审计采用只追加和摘要链防篡改校验。**生产环境签名密钥必须由 KMS/Vault 托管，应用只持有引用与短期访问令牌**，禁止明文进入环境变量或仓库。

## 3. 本地开发快速开始

1. 复制配置模板：`cp .env.example .env`，将所有 `CHANGE_ME` 替换为本地随机值。
2. 启动依赖：`docker compose -f infra/compose/compose.yaml --env-file .env up -d`。
3. 健康检查：各服务均配置 healthcheck；MySQL 通过 `mysqladmin ping`，Redis 通过 `redis-cli ping`，MinIO 通过 `mc ready local`。
4. 应用启动：`SPRING_PROFILES_ACTIVE=dev` 由 `.env` 注入，`apps/api` 读取 `application.yml` 并解析各数据源。

本地端口映射：业务主库 `3306`、工作流库 `3307`、历史源库 `3308`、Redis `6379`、MinIO API `9000`、MinIO Console `9001`。

## 4. 生产配置与密钥管理

- **禁止使用 `.env` 文件**：生产凭据不得落盘为环境变量明文文件。
- **外部密钥管理**：所有数据库口令、对象存储密钥、OIDC 客户端密钥、签名密钥统一由 KMS/Vault 托管；应用启动时通过受控引导（Sidecar、Secret 挂载、KMS 解封）获取凭据句柄，业务代码只持有引用与短期访问令牌。
- **Kubernetes 注入**：敏感变量通过 `Secret`（`pdp-secrets`）以 `envFrom` 注入，非敏感配置通过 `ConfigMap`（`pdp-config`）注入，详见 `infra/k8s/base/deployment.yaml` 与 `infra/k8s/base/configmap.yaml`。
- **职责分离**：迁移源/目标/在线主库、Flowable、审计使用独立账号与最小权限；应用账号无 DDL 权限，schema 由 Liquibase 管理。
- **密钥轮换**：轮换周期、撤销流程、泄漏响应及双密钥过渡窗口遵循 `docs/security/security-baseline.md` 第 4 节；轮换不得导致服务中断。
- **传输保护**：生产所有通信使用 HTTPS/TLS 1.2+，内部服务间通信加密，详见 `docs/security/security-baseline.md` 第 2 节。

## 5. 变更与审计

- 新增环境变量必须同步更新 `.env.example`、本指南及 `infra/k8s/base/` 相关清单。
- 涉及密钥分类或轮换策略变更，必须更新 `docs/security/security-baseline.md` 并经安全/合规负责人评审。
- 凭据或密钥泄漏必须按 `docs/security/security-baseline.md` 的泄漏响应步骤处置，并记录事件、影响范围与撤销证据。

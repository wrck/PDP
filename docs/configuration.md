# 配置指南

复制 `.env.example` 为本机 `.env`，只填写本地或密钥系统引用；不得提交真实凭据。启动目标 MySQL：

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml --profile mysql up -d
```

迁移开发环境增加 `migration`，Redis/对象存储增加 `platform`，遥测收集器增加 `observability`。在线、只读、迁移源、迁移目标和工作流必须使用独立账号与连接池。生产配置通过平台密钥系统注入，禁止在 Kubernetes ConfigMap 中放置密码。

应用必须以 UTC、`READ_COMMITTED`、严格 MySQL 8.4 认证基线启动；未知数据源键、缺失主库、非认证版本或 schema 不兼容时快速失败。


# Flowable MySQL Schema 清单

生产环境禁止由 Flowable 自动创建或升级表。发布流程读取
`flowable-schema-manifest.properties`，按清单顺序对独立 `pdp_workflow`
schema 执行 Flowable 8.0.0 官方 SQL。

- 空库安装：依次执行 common、engine、history 创建脚本。
- 升级入口：P1 仅认证 `7.2.2 -> 8.0.0`。
- 未在清单中的源版本必须停止发布，并先建立独立升级验证。
- 执行前必须备份，执行后校验 `ACT_GE_PROPERTY` 中的 schema 版本。

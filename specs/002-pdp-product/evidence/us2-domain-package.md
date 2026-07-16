# US2 领域包与深度定制后端证据

## 验收范围

本证据覆盖领域包契约、三层继承、统一核心字段复用、元模型、状态和规则、职责分离发布、平台工作流绑定边界、受治理扩展、升级影响预览、分批迁移、失败隔离与回滚。前端设计器和 MySQL 专有 Mapper 由独立任务验收，不在本证据范围内。

## 示例闭环

`network-cutover-package.json` 定义网络设备割接对象、规划/执行/验收顶层生命周期映射、独立审核流程、前置检查扩展、回退状态、字段权限及 `0.9.0 → 1.0.0` 可回滚迁移。工作流只使用稳定流程标识、业务版本、变量映射和授权策略，不包含 Flowable 专有 API。

## 已验证证据

- OpenAPI 提供草稿、校验、审核、发布、退役、回滚、影响预览和迁移操作。
- `domain-package.schema.json` 1.1 强制行业包和客户包继承上层包；扩展必须签名并在进程、容器或远程服务中隔离；迁移必须声明回滚类型。
- 核心字段目录按稳定标识、语义名称、别名和数据来源检测重复定义，并提示复用平台字段。
- 发布状态机执行创建、校验、提交审核、独立审核、独立批准和冻结；创建者不能审核，审核者不能批准发布。
- 三层组合生成不可变快照、差异清单和字段类型冲突清单。
- 实例升级通过高风险操作确认令牌启动，记录受影响实例、批次数、不可逆点和补偿方式；失败实例独立记录，可继续其他批次或按快照回滚。

## 验证命令

```powershell
pnpm exec node --test tests/contracts/us2-domain-package.spec.ts
pnpm --filter @pdp/tests test:contracts

$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.9+10'
.\mvnw.cmd -pl modules/domainconfig -am test
.\mvnw.cmd -pl apps/api -am -DskipTests package
```

当前后端证据要求上述命令全部成功；MySQL 数据库契约证据由 T119/T120 补充。

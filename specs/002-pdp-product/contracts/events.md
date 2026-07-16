# PDP P1 业务事件契约

## 1. 事件信封

```json
{
  "eventId": "019c...",
  "eventType": "pdp.project.lifecycle.changed",
  "eventVersion": 1,
  "occurredAt": "2026-07-16T08:00:00Z",
  "workspaceId": "019c...",
  "aggregateType": "project",
  "aggregateId": "019c...",
  "aggregateRevision": 12,
  "actor": {
    "type": "USER",
    "id": "019c..."
  },
  "traceId": "4bf92f...",
  "data": {},
  "metadata": {
    "source": "pdp",
    "classification": "INTERNAL"
  }
}
```

## 2. 投递与消费语义

- 事件使用 Spring Modulith JDBC 事件发布存储，在核心业务事务中与业务事实原子登记，事务提交后按监听器异步投递。
- 投递保证为“至少一次”；消费者必须使用 `eventId` 幂等。
- 同一聚合的事件通过 `aggregateRevision` 判断顺序。发现缺口、乱序或旧版本时，消费者不得覆盖较新状态。
- `data` 只包含订阅者完成动作所需的最小信息。敏感字段、附件内容和签名不得直接进入事件。
- 事件失败进入可查询积压，支持重试、暂停和人工补偿；失败不得回滚已完成核心事务。
- 每个监听器具有独立发布状态和重试记录；清理任务只能删除超过保留期且所有监听器均已完成的记录。
- 生产环境由 Liquibase 管理事件发布表，禁止依赖框架启动时自动创建或修改 schema。
- 增加可选字段属于兼容变更；删除字段、改变含义或改变必填性必须提升 `eventVersion`。

## 3. P1 事件目录

| 事件类型 | 触发时机 | 主要消费者 |
|---|---|---|
| `pdp.workspace.membership.changed` | 成员、角色或有效期改变 | 权限缓存、通知、审计投影 |
| `pdp.workspace.collaboration.changed` | 跨空间授权生效、到期或撤销 | 权限缓存、搜索投影 |
| `pdp.domain-package.published` | 领域包版本发布 | 安装目录、文档、测试报告 |
| `pdp.domain-package.migration.requested` | 存量实例迁移获准 | 后台迁移执行器 |
| `pdp.project.created` | 项目及模板实例化事务完成 | 活动、搜索、通知 |
| `pdp.project.lifecycle.changed` | 顶层生命周期或领域阶段改变 | 组合统计、活动、通知 |
| `pdp.project.progress.recalculation.requested` | 里程碑、任务或交付事实变化 | 进度计算器 |
| `pdp.task.state.changed` | 任务状态变化 | 看板投影、进度、通知 |
| `pdp.milestone.state.changed` | 里程碑状态变化 | 进度、延期预警 |
| `pdp.deliverable.version.published` | 新交付件版本发布 | 归档清单、通知、外部订阅 |
| `pdp.approval.completed` | 审批终态形成 | 业务对象条件回写 |
| `pdp.change.approved` | 变更批准 | 计划/范围更新协调器 |
| `pdp.audit.export.requested` | 审计导出获准 | 后台导出执行器 |
| `pdp.integration.delivery.failed` | 外部投递达到告警条件 | 集成健康度、值守通知 |
| `pdp.migration.run.progressed` | 历史或跨数据库迁移运行进度、源/目标类型、位点或状态变化 | 迁移控制台、告警 |
| `pdp.migration.issue.detected` | 发现阻断、冲突或隔离数据 | 数据责任人、上线门禁 |
| `pdp.migration.cutover.decided` | Go/No-Go、开放写入、回退或前向修复决策形成 | 流量控制、审计、值守通知 |

## 4. 事件外部化

默认事件只在 PDP 内部模块间使用。外部订阅必须经过管理员授权，并配置事件类型、对象范围、字段映射、目标、签名密钥、重试策略和停用开关。外部载荷由映射器生成，不直接暴露内部数据库结构。

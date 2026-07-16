# PDP P1 接口契约

本目录定义 P1 平台对 Web 前端、领域扩展和外部系统暴露的完整、版本化稳定边界：

- [openapi.yaml](openapi.yaml)：P1 同步 HTTP API 契约；不得存在实现已暴露但契约未登记的路由。
- [domain-package.schema.json](domain-package.schema.json)：领域包清单及声明式元数据结构。
- [events.md](events.md)：平台业务事件信封、兼容规则和 P1 事件目录。
- [migration-report.schema.json](migration-report.schema.json)：历史迁移与认证数据库切换报告契约。
- [coverage.md](coverage.md)：P1 用户故事、HTTP、事件、Schema、消费者和验证任务覆盖矩阵。
- [openapi.yaml](openapi.yaml) 中的“搜索与通知”接口：权限过滤搜索、站内通知查询和已读操作。
- [openapi.yaml](openapi.yaml) 中的“数据迁移”接口：迁移计划、试运行、核对和切换门禁。
- [../persistence-design.md](../persistence-design.md)：游标、乐观锁、类型映射、投影、搜索、动态数据源、连接池和事件存储约束。

## 通用约定

- 基础路径为 `/api/v1`，路径主版本只在不兼容变更时升级。
- 所有请求必须具备认证身份；工作空间级接口使用 `X-Workspace-Id`。
- 高风险创建命令使用 `Idempotency-Key`；更新使用 `If-Match` 或请求体 `revision`。
- revision 不匹配时返回 `409 application/problem+json`；无权与不存在不得通过响应差异泄露对象存在性。
- 响应携带 `X-Trace-Id`；错误使用 `application/problem+json`，不得泄露无权字段或对象是否存在。
- 分页默认使用签名 keyset cursor；游标绑定排序、过滤和权限范围，客户端不得解析或构造。批量导入、导出、迁移和统计返回后台作业。
- API 契约只定义平台稳定语义。领域字段通过 `extensionData` 和领域包元数据扩展，不为每个客户复制接口。
- API、事件和领域包不得暴露数据库表名、专有类型、方言函数或物理分页方式；同一契约在 PostgreSQL 和 MySQL 上必须保持一致。
- 高风险命令必须先取得包含目标版本、影响、阻断项、不可逆点和补偿选项的影响预览；确认时对象版本变化必须返回冲突并重新预览。
- 权限撤销后，新请求立即使用最新授权，本地缓存 5 秒内失效，搜索和报表投影 30 秒内移除，实时连接 30 秒内刷新或断开，活动会话和刷新凭据 1 分钟内撤销。
- 搜索结果打开时必须再次校验对象与字段权限；站内通知按事件标识幂等生成，失败允许安全重提。

## 兼容性规则

允许向后兼容地增加可选字段、枚举扩展值和新端点；删除字段、改变字段含义、收紧既有输入或修改状态语义属于不兼容变更。领域包、API、事件和迁移报告必须分别维护版本，不能用显示名称作为稳定引用。不兼容变更必须记录消费者、弃用开始时间、迁移窗口、替代契约和移除批准。

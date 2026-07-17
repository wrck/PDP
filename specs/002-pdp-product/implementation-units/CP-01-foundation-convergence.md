# CP-01 项目执行基础收敛点

## 触发条件

仅在 `IU-01`～`IU-07` 全部完成并合入同一分支后执行。本文件不是功能实施单元，不领取新的产品 Task。

## 收敛内容

1. 检查六个单元没有修改彼此的独占目录或引入跨模块内部依赖。
2. 根据各单元交付的 Task/提交号统一更新 `tasks.md`，不得仅凭摘要勾选未合入或未验证的 Task。
3. 确认公共 Liquibase 通过 `includeAll` 自动发现 040、050、060、070、080 变更集，无需修改根变更集。
4. 统一处理确有必要的父 POM、应用装配或根配置，保持单写。
5. 运行核心契约测试、模块边界测试和相关模块测试；不运行 Playwright、性能、恢复或发布门禁。
6. 记录真正的接口缺口，并据此生成下一批“服务/API/页面”独立实施单元。

## 最小验证

```powershell
pnpm exec node --test tests/contracts/us4-project-lifecycle.spec.ts tests/contracts/us5-task-collaboration.spec.ts tests/contracts/us6-plan-baseline.spec.ts tests/contracts/us7-deliverable.spec.ts tests/contracts/us8-approval.spec.ts
.\mvnw.cmd -pl modules/template,modules/project,modules/planning,modules/deliverable,modules/approval,modules/persistence-mysql -am test
.\mvnw.cmd -pl tests/backend test "-Dtest=ModuleBoundaryTest,DependencyPolicyTest,WorkflowBoundaryTest"
```

## 收敛输出

- 一个简短的收敛提交，只修复合并后真实出现的边界或编译问题；无问题时不创建空提交。
- 下一批单元必须继续使用独占文件、最小验证和最后收敛模式。
- `T130`、`T136`～`T140` 只有在项目、计划、交付件和审批公开基础通过本收敛点后才能进入下一批。

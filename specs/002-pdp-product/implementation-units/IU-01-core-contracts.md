# IU-01 核心执行域契约单写通道

## 目标

一次性消除 `openapi.yaml`、`events.md` 和 `coverage.md` 的并行写冲突，为项目、任务、计划、交付件和审批基础单元冻结稳定公共语义。本单元只处理契约和契约测试，不实现后端或页面。

## 原始任务

按顺序完成并分别提交：`T141`、`T142`、`T154`、`T155`、`T168`、`T169`、`T181`、`T182`、`T195`、`T196`。

依赖：`T128`、`T129` 已完成；平台统一错误、高风险操作和工作流契约已存在。

## 独占文件

- `specs/002-pdp-product/contracts/openapi.yaml`
- `specs/002-pdp-product/contracts/events.md`
- `specs/002-pdp-product/contracts/coverage.md`
- `tests/contracts/us4-project-lifecycle.spec.ts`
- `tests/contracts/us5-task-collaboration.spec.ts`
- `tests/contracts/us6-plan-baseline.spec.ts`
- `tests/contracts/us7-deliverable.spec.ts`
- `tests/contracts/us8-approval.spec.ts`

## 禁止修改

禁止修改任何 Java、Vue、Liquibase、Mapper、共享 `tasks.md`、父 POM、根配置以及其他实施单元文件。不要编写依赖真实页面的 E2E。

## 最小验证

每个 Task 只运行对应的 `node --test tests/contracts/<story>.spec.ts`；最后运行一次 `pnpm --filter @pdp/tests typecheck`。不运行 Maven、Playwright 或全量 `pnpm verify`。

## 完成输出

保持一 Task 一提交，但不修改共享 `tasks.md`。最终摘要列出 Task、提交号、契约测试结果和未解决的兼容问题。

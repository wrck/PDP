# 仓库协作指南

## 项目结构与模块组织

本仓库当前处于“规格先行”阶段，尚未加入应用源代码和自动化测试代码。

- `specs/002-pdp-product/`：当前活动的 L0 PDP 产品规格及质量检查清单。
- `specs/001-domain-template-platform/`：保留的领域模板子规格，后续需补充上位需求追踪。
- `docs/000-init/`：调研、历史需求和参考材料，不作为当前活动规格。
- `.specify/`：Spec Kit 配置、模板、工作流和 PowerShell 辅助脚本。
- `.specify/memory/constitution.md`：不可协商的规格、架构、测试、数据库和迁移治理原则。
- `.agents/skills/`：仓库内使用的 Spec Kit 工作流说明。

新规格应创建于 `specs/NNN-short-name/`。平台域、能力域和业务域规格必须引用上位产品需求，只能细化，不能静默扩大 L0 产品范围。
当前可执行 `tasks.md` 只允许包含已批准的 P1 范围；P2/P3 必须进入独立 backlog 或子规格。
规格还必须明确业务闭环、统一术语、核心对象、状态机、不变量、权限、安全、运行和验收证据；孤立页面或接口不视为完整能力。

## 构建、测试与开发命令

当前没有应用构建或本地运行命令。可在仓库根目录执行以下检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -PathsOnly
```

确认当前活动特性及 Spec Kit 预期文件路径。

```powershell
rg -n "NEEDS CLARIFICATION|TODO|TBD" specs
rg -n "^- \*\*FR-|^- \*\*SC-" specs/002-pdp-product/spec.md
```

用于查找未决标记并检查需求编号。引入应用代码后，应在此补充实际的构建、格式化、检查和测试命令。

## 编写风格与命名约定

所有文档必须使用中文和 UTF-8 编码；命令、路径、代码标识及行业通用缩写可保留英文。使用简洁段落和 ATX 风格 Markdown 标题。

Feature 目录采用 `NNN-kebab-case`，功能需求采用 `FR-001`，成功指标采用 `SC-001`。统一使用工作空间、领域包、项目、阶段、任务、交付件和审批等规范术语。

未来代码应遵循实施计划确定的格式化和检查工具，未经协商不要引入重复工具。
关键架构选择使用 `docs/adr/` 记录。宪章例外必须包含补偿控制、责任人、批准人、失效日期、复审日期和退出条件。

## 测试要求

每个用户故事必须包含独立测试及 Given/When/Then 验收场景。需求必须可测试、可量化且不绑定技术实现。及时维护 `checklists/requirements.md`。未来自动化测试应覆盖权限、迁移、集成失败、并发冲突和恢复场景。

每个持久化用户故事还必须包含公共 DDL、仓储端口、Mapper/适配器和 MySQL 契约测试；领域层与应用层不得依赖 MySQL 专有实现。外部接口必须先更新 OpenAPI、事件或领域包契约，再实现 Controller 或消费者。
进入实现前应满足宪章 DoR；完成时按 DoD 验证权限、可观测、性能、恢复、迁移、运行手册和追踪证据。

## 提交与合并请求要求

当前无法读取有效 Git 历史，暂采用 Conventional Commits，例如：`docs(spec): 明确工作空间归属`。每次提交应保持范围单一。

合并请求应说明变更范围、上位需求编号、修改的规格、验证结果及兼容或迁移影响。仅在实现界面变更时提供截图。禁止提交凭据、客户数据、设备日志或生产环境导出文件。

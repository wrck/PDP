/**
 * 平台工作流持久化聚合与仓储端口包（FR-174、ADR-0005 第 7 节）。
 *
 * <p>本包承载 PDP 自有工作流注册表的持久化聚合（{@code *Record}）与仓储端口
 * （{@code *Repository}），对应 {@code workflow_definition}、{@code workflow_deployment}、
 * {@code workflow_instance_ref}、{@code workflow_incident}、{@code workflow_result_event}、
 * {@code workflow_migration_record} 六张表（Liquibase 变更集
 * {@code db/changelog/common/005-workflow-registry.xml}）。
 *
 * <p><strong>分层约定</strong>：
 * <ul>
 *   <li>{@link com.pdp.workflow.model} 包承载公开稳定值对象（业务模块端口契约的输入输出），
 *       如 {@link com.pdp.workflow.model.WorkflowDefinitionSummary}、
 *       {@link com.pdp.workflow.model.WorkflowInstanceSummary}；</li>
 *   <li>本包（{@code domain}）承载持久化聚合，含完整审计字段与 BPMN 内容，
 *       仅供工作流模块内部的应用层（{@code application/}）与基础设施层
 *       （{@code infrastructure/}）使用，不直接暴露于业务模块；</li>
 *   <li>仓储端口（{@code *Repository}）由 {@code public-persistence} 基础设施适配器实现，
 *       应用层依赖端口，不依赖 MyBatis、MySQL 驱动或 Mapper。</li>
 * </ul>
 *
 * <p><strong>与 Flowable 引擎表的边界</strong>（ADR-0005）：
 * 本包的表位于 {@code pdpPrimary} 数据源，由 Liquibase 管理；Flowable 引擎运行表
 * （{@code ACT_*}）位于 {@code workflowEngine} 数据源，由 Flowable 版本化 DDL 管理。
 * 业务 Mapper MUST NOT 直接查询或更新 {@code ACT_*} 表。
 *
 * <p><strong>端口约定</strong>（与 identity 模块一致）：
 * <ul>
 *   <li>查询返回 {@link java.util.Optional} 或 {@link java.util.List}，不存在时返回 empty/空列表；</li>
 *   <li>状态更新使用乐观锁，返回 {@code boolean} 表达冲突（true=成功，false=版本冲突或不存在）；</li>
 *   <li>分页查询使用 {@link com.pdp.shared.page.PageRequest}/{@link com.pdp.shared.page.PageResult}；</li>
 *   <li>端口签名不抛业务异常，业务规则由应用层校验。</li>
 * </ul>
 */
package com.pdp.workflow.domain;

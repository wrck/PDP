/**
 * 平台工作流基础能力模块（FR-174、ADR-0005）。
 *
 * <p>提供四类稳定端口供业务模块（审批、项目、计划、交付件、领域包）消费工作流能力，
 * 隔离 Flowable 引擎实现细节：
 *
 * <ul>
 *   <li>{@link com.pdp.workflow.definition.WorkflowDefinitionPort}：BPMN 2.0.2 流程定义
 *       校验、版本管理、内容哈希、领域包关联与受控部署；</li>
 *   <li>{@link com.pdp.workflow.runtime.WorkflowRuntimePort}：流程实例启动、推进、
 *       消息关联与结果事件桥接；</li>
 *   <li>{@link com.pdp.workflow.task.WorkflowTaskPort}：人工任务查询、认领、办理、
 *       委派与回写，办理前实时复核 PDP 权限；</li>
 *   <li>{@link com.pdp.workflow.administration.WorkflowAdministrationPort}：流程实例
 *       受控迁移、暂停、恢复、终止与人工补偿管理。</li>
 * </ul>
 *
 * <p><strong>模块边界（ADR-0005）</strong>：
 * <ul>
 *   <li>业务模块 MUST NOT 依赖 {@code org.flowable.*} 包、Flowable 表名或异常类型，
 *       仅通过四类端口消费工作流能力；</li>
 *   <li>端口契约仅含 PDP 自有稳定类型（{@link com.pdp.workflow.model} 包），不携带
 *       Flowable 实体、任务对象或异常；</li>
 *   <li>Flowable 适配器实现位于 {@code infrastructure/flowable/} 子包，
 *       由 Spring Modulith / ArchUnit 校验依赖方向；</li>
 *   <li>流程实例、活动与变量 MUST NOT 成为业务结论、权限或审计事实的唯一存储。</li>
 * </ul>
 *
 * <p><strong>事务边界（ADR-0005 第 8 节）</strong>：
 * Flowable 表与 PDP 业务表使用独立 schema/表前缀、数据库账号、HikariCP 连接池和事务管理器
 * （数据源键 {@code workflowEngine}）。不使用 XA。业务事实先在 {@code pdpPrimary} 本地事务
 * 提交并登记 outbox，再由幂等编排消费者启动或推进流程实例。
 */
package com.pdp.workflow;

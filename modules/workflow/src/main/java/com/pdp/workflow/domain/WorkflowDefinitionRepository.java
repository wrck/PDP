package com.pdp.workflow.domain;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.model.ProcessDefinitionKey;
import com.pdp.workflow.model.ProcessVersion;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowDefinitionStatus;

import java.util.Optional;

/**
 * 流程定义仓储端口（FR-174、ADR-0005 第 7 节）。
 *
 * <p>持久化 {@link WorkflowDefinitionRecord} 聚合到 {@code workflow_definition} 表。
 * 应用层（{@code WorkflowDefinitionService}）依赖此端口，不依赖 MyBatis、MySQL 驱动
 * 或持久化记录内部结构。实现位于 {@code public-persistence} 基础设施适配器边界。
 *
 * <p><strong>约定</strong>（与 identity 模块仓储端口一致）：
 * <ul>
 *   <li>查询返回 {@link Optional}，不存在时返回 empty，不抛业务异常；</li>
 *   <li>状态更新使用乐观锁，返回 boolean 表达冲突（true=成功，false=版本冲突或不存在）；</li>
 *   <li>分页查询使用 {@link PageRequest}/{@link PageResult}，不暴露 MyBatis-Plus Page；</li>
 *   <li>端口签名不抛业务异常，业务规则由应用层校验。</li>
 * </ul>
 */
public interface WorkflowDefinitionRepository {

    /**
     * 保存流程定义（插入或按 id 更新）。
     *
     * @param record 流程定义聚合
     */
    void save(WorkflowDefinitionRecord record);

    /**
     * 按 ID 查询流程定义。
     *
     * @param id 流程定义 ID
     * @return 流程定义聚合，不存在时返回 empty
     */
    Optional<WorkflowDefinitionRecord> findById(WorkflowDefinitionId id);

    /**
     * 按稳定键与业务版本查询流程定义。
     *
     * @param workspaceId     工作空间边界
     * @param stableKey       流程定义稳定键
     * @param businessVersion 业务版本
     * @return 流程定义聚合，不存在时返回 empty
     */
    Optional<WorkflowDefinitionRecord> findByKeyAndVersion(
            com.pdp.shared.context.WorkspaceId workspaceId,
            ProcessDefinitionKey stableKey,
            ProcessVersion businessVersion);

    /**
     * 查询指定流程键的最新已部署版本（status=DEPLOYED 中 businessVersion 最大）。
     *
     * @param workspaceId 工作空间边界
     * @param stableKey   流程定义稳定键
     * @return 最新已部署流程定义聚合，不存在时返回 empty
     */
    Optional<WorkflowDefinitionRecord> findLatestDeployed(
            com.pdp.shared.context.WorkspaceId workspaceId,
            ProcessDefinitionKey stableKey);

    /**
     * 分页查询流程定义。
     *
     * @param workspaceId 工作空间边界
     * @param keyFilter   流程键过滤前缀（可选，null 表示不过滤）
     * @param status      状态过滤（可选，null 表示所有状态）
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    PageResult<WorkflowDefinitionRecord> listDefinitions(
            com.pdp.shared.context.WorkspaceId workspaceId,
            String keyFilter,
            WorkflowDefinitionStatus status,
            PageRequest pageRequest);

    /**
     * 迁移流程定义状态并递增 revision（乐观并发控制）。
     *
     * @param id               流程定义 ID
     * @param targetStatus     目标状态
     * @param expectedRevision 期望版本（乐观锁）
     * @return 是否成功（true=成功，false=版本冲突或不存在）
     */
    boolean transitionStatus(
            WorkflowDefinitionId id,
            WorkflowDefinitionStatus targetStatus,
            int expectedRevision);
}

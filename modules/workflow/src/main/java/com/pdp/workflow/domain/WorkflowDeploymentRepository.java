package com.pdp.workflow.domain;

import com.pdp.workflow.model.WorkflowDefinitionId;

import java.util.Optional;
import java.util.UUID;

/**
 * 流程部署记录仓储端口（FR-174、ADR-0005 第 7 节）。
 *
 * <p>持久化 {@link WorkflowDeploymentRecord} 聚合到 {@code workflow_deployment} 表。
 * 链接 PDP 流程定义到 Flowable 引擎 definition/deployment 标识。
 *
 * <p><strong>约定</strong>：
 * <ul>
 *   <li>查询返回 {@link Optional}，不存在时返回 empty；</li>
 *   <li>同引擎 definition ID 唯一，重复部署时返回已有记录（幂等）；</li>
 *   <li>端口签名不抛业务异常。</li>
 * </ul>
 */
public interface WorkflowDeploymentRepository {

    /**
     * 保存部署记录（插入或按 id 更新）。
     *
     * @param record 部署记录聚合
     */
    void save(WorkflowDeploymentRecord record);

    /**
     * 按 ID 查询部署记录。
     *
     * @param id 部署记录 ID
     * @return 部署记录聚合，不存在时返回 empty
     */
    Optional<WorkflowDeploymentRecord> findById(UUID id);

    /**
     * 按流程定义 ID 查询最新部署记录（按 deployedAt 倒序取首条）。
     *
     * @param definitionId 流程定义 ID
     * @return 最新部署记录聚合，不存在时返回 empty
     */
    Optional<WorkflowDeploymentRecord> findByDefinitionId(WorkflowDefinitionId definitionId);

    /**
     * 按引擎定义 ID 查询部署记录（幂等部署去重）。
     *
     * @param engineType         引擎类型
     * @param engineDefinitionId 引擎定义 ID
     * @return 部署记录聚合，不存在时返回 empty
     */
    Optional<WorkflowDeploymentRecord> findByEngineDefinitionId(
            String engineType,
            String engineDefinitionId);
}

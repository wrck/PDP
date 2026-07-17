package com.pdp.workflow.definition;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.model.ProcessDefinitionKey;
import com.pdp.workflow.model.ProcessVersion;
import com.pdp.workflow.model.ValidationResult;
import com.pdp.workflow.model.WorkflowDefinitionContent;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowDefinitionStatus;
import com.pdp.workflow.model.WorkflowDefinitionSummary;
import com.pdp.workflow.model.WorkflowEngineException;

import java.util.Optional;
import java.util.UUID;

/**
 * 平台流程定义端口（FR-174、ADR-0005 第 6 节）。
 *
 * <p>负责 BPMN 2.0.2 流程定义的校验、版本管理、内容哈希、领域包关联与部署；
 * 对外 <strong>不暴露</strong> Flowable {@code RepositoryService} 类型。
 * 所有输入输出对象为 PDP 自有稳定契约。
 *
 * <p><strong>核心契约（FR-174 / ADR-0005）</strong>：
 * <ol>
 *   <li>BPMN 校验：MUST 校验 BPMN 2.0.2 结构、稳定流程键命名、语义化业务版本、
 *       内容哈希一致性与领域包版本关联；</li>
 *   <li>受控部署：部署请求 MUST 携带校验阶段生成的 {@code contentHash}，确保部署内容
 *       与校验一致；同 {@code (processDefinitionKey, businessVersion)} 已部署时 MUST 返回
 *       已有定义而非重复部署（幂等）；</li>
 *   <li>版本固定：已启动流程实例 MUST 固定为启动时的定义版本，新版本部署不影响运行中实例；</li>
 *   <li>状态机：定义状态 {@link WorkflowDefinitionStatus} 仅允许合法迁移，
 *       {@code RETIRED} 为终态不可恢复；</li>
 *   <li>领域包关联：定义 MUST 可关联领域包版本，便于领域包升级时追溯影响范围。</li>
 * </ol>
 *
 * <p>端口实现位于 {@code workflow} 模块的 {@code infrastructure/flowable/} 子包，
 * 内部完成 Flowable {@code RepositoryService} 调用、异常翻译与关联标识映射。
 */
public interface WorkflowDefinitionPort {

    /**
     * 校验 BPMN 2.0.2 流程定义。
     *
     * <p>校验维度（FR-174、ADR-0005 第 5 节）：
     * <ul>
     *   <li>BPMN 2.0.2 XML 结构合法性；</li>
     *   <li>{@code process id} 符合 {@link ProcessDefinitionKey#PATTERN} 命名规则；</li>
     *   <li>语义化业务版本格式；</li>
     *   <li>内容哈希计算（SHA-256）；</li>
     *   <li>领域包版本关联有效性（若提供）；</li>
     *   <li>流程定义键与版本不冲突（同 key+version 未已部署）。</li>
     * </ul>
     *
     * @param key            流程定义稳定键
     * @param businessVersion 业务版本
     * @param bpmnXml        BPMN 2.0.2 XML 文本
     * @param domainPackageVersionId 关联领域包版本 ID（可选）
     * @return 校验结果（含内容哈希与发现项）
     * @throws WorkflowEngineException 当 BPMN 结构严重错误无法解析时
     */
    ValidationResult validate(
            ProcessDefinitionKey key,
            ProcessVersion businessVersion,
            String bpmnXml,
            UUID domainPackageVersionId);

    /**
     * 部署已校验的 BPMN 流程定义。
     *
     * <p>部署请求 MUST 携带校验阶段生成的 {@code contentHash}，确保部署内容与校验一致。
     * 同 {@code (processDefinitionKey, businessVersion)} 已部署时 MUST 幂等返回已有定义。
     *
     * @param key             流程定义稳定键
     * @param businessVersion 业务版本
     * @param content         流程定义内容（含 BPMN XML 与哈希）
     * @param domainPackageVersionId 关联领域包版本 ID（可选）
     * @param idempotencyKey  幂等键
     * @param deployedBy      部署者
     * @return 部署后的定义摘要
     * @throws WorkflowEngineException 内容哈希不匹配、BPMN 校验失败或部署冲突
     */
    WorkflowDefinitionSummary deploy(
            ProcessDefinitionKey key,
            ProcessVersion businessVersion,
            WorkflowDefinitionContent content,
            UUID domainPackageVersionId,
            IdempotencyKey idempotencyKey,
            ActorRef deployedBy);

    /**
     * 查询流程定义详情。
     *
     * @param id 流程定义 ID
     * @return 定义摘要（含状态、校验发现项），不存在时返回 empty
     */
    Optional<WorkflowDefinitionSummary> findById(WorkflowDefinitionId id);

    /**
     * 按稳定键与版本查询流程定义。
     *
     * @param key             流程定义稳定键
     * @param businessVersion 业务版本
     * @return 定义摘要，不存在时返回 empty
     */
    Optional<WorkflowDefinitionSummary> findByKeyAndVersion(
            ProcessDefinitionKey key,
            ProcessVersion businessVersion);

    /**
     * 查询指定流程键的最新已部署版本。
     *
     * @param key 流程定义稳定键
     * @return 最新已部署定义摘要，不存在时返回 empty
     */
    Optional<WorkflowDefinitionSummary> findLatestDeployed(ProcessDefinitionKey key);

    /**
     * 分页查询流程定义。
     *
     * @param workspaceId 工作空间边界（领域包版本归属工作空间）
     * @param keyFilter   流程键过滤前缀（可选，null 表示不过滤）
     * @param status      状态过滤（可选，null 表示所有状态）
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    PageResult<WorkflowDefinitionSummary> listDefinitions(
            WorkspaceId workspaceId,
            String keyFilter,
            WorkflowDefinitionStatus status,
            PageRequest pageRequest);

    /**
     * 迁移流程定义状态（如 DEPLOYED → DEPRECATED、DEPRECATED → RETIRED）。
     *
     * <p>状态迁移 MUST 遵循 {@link WorkflowDefinitionStatus#canTransitionTo} 约束。
     * {@code RETIRED} 终态不可恢复。
     *
     * @param id              流程定义 ID
     * @param targetStatus    目标状态
     * @param expectedRevision 期望版本（乐观并发控制）
     * @param reason          迁移原因（审计）
     * @param actor           操作者
     * @return 迁移后的定义摘要
     * @throws WorkflowEngineException 状态迁移非法或版本冲突
     */
    WorkflowDefinitionSummary transitionStatus(
            WorkflowDefinitionId id,
            WorkflowDefinitionStatus targetStatus,
            int expectedRevision,
            String reason,
            ActorRef actor);
}

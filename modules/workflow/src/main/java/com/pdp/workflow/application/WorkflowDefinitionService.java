package com.pdp.workflow.application;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.BusinessRuleException;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.ResourceNotFoundException;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.definition.WorkflowDefinitionPort;
import com.pdp.workflow.domain.WorkflowDefinitionRecord;
import com.pdp.workflow.domain.WorkflowDefinitionRepository;
import com.pdp.workflow.model.ProcessDefinitionKey;
import com.pdp.workflow.model.ProcessVersion;
import com.pdp.workflow.model.ValidationResult;
import com.pdp.workflow.model.WorkflowDefinitionContent;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowDefinitionStatus;
import com.pdp.workflow.model.WorkflowDefinitionSummary;
import com.pdp.workflow.model.WorkflowEngineException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 平台流程定义应用服务（FR-174、ADR-0005 第 5-6 节）。
 *
 * <p>编排 {@link WorkflowDefinitionPort}（Flowable 引擎操作）与
 * {@link WorkflowDefinitionRepository}（PDP 自有注册表持久化），对外提供
 * BPMN 2.0.2 校验、受控部署、版本查询与状态迁移的粗粒度应用 API。
 *
 * <p><strong>核心契约（FR-174 / ADR-0005）</strong>：
 * <ol>
 *   <li>BPMN 校验：委托 {@link WorkflowDefinitionPort#validate} 完成 BPMN 2.0.2 结构校验、
 *       稳定流程键命名、语义化业务版本、内容哈希计算（SHA-256）与领域包关联校验，
 *       纯计算无副作用（对应 OpenAPI {@code POST /workflow-definitions/validate}）；</li>
 *   <li>受控部署：同 {@code (workspace, processDefinitionKey, businessVersion)} 已部署且
 *       内容哈希一致时幂等返回已有定义；内容哈希不一致时拒绝（防覆盖）；</li>
 *   <li>版本固定：已启动实例固定为启动时定义版本，新版本部署不影响运行中实例
 *       （由 Flowable 引擎保证）；</li>
 *   <li>状态机：定义状态迁移 MUST 遵循 {@link WorkflowDefinitionStatus#canTransitionTo}，
 *       {@code RETIRED} 为终态不可恢复；</li>
 *   <li>工作空间边界：查询与部署 MUST 在指定工作空间内，跨工作空间访问返回 404 语义。</li>
 * </ol>
 *
 * <p><strong>事务边界（ADR-0005 第 8 节）</strong>：
 * Flowable 引擎操作（{@link WorkflowDefinitionPort}）使用 {@code workflowEngine} 独立事务管理器，
 * PDP 注册表持久化（{@link WorkflowDefinitionRepository}）使用 {@code pdpPrimary} 事务管理器。
 * 两者不使用 XA；{@link #deploy} 方法先做幂等预检，再委托端口完成 Flowable 部署与 PDP 记录持久化。
 *
 * <p><strong>异常翻译</strong>：
 * <ul>
 *   <li>资源不存在/跨工作空间 → {@link ResourceNotFoundException}（HTTP 404）；</li>
 *   <li>状态迁移非法 → {@link BusinessRuleException}（HTTP 422）；</li>
 *   <li>乐观锁冲突 → {@link BusinessRuleException}（HTTP 409）；</li>
 *   <li>引擎层故障 → {@link WorkflowEngineException}（由端口抛出，服务不捕获）。</li>
 * </ul>
 */
@Service
public class WorkflowDefinitionService {

    private final WorkflowDefinitionPort definitionPort;
    private final WorkflowDefinitionRepository definitionRepository;

    public WorkflowDefinitionService(WorkflowDefinitionPort definitionPort,
                                     WorkflowDefinitionRepository definitionRepository) {
        this.definitionPort = definitionPort;
        this.definitionRepository = definitionRepository;
    }

    // ============================================================
    // BPMN 校验（纯计算，无副作用）
    // ============================================================

    /**
     * 校验 BPMN 2.0.2 流程定义。
     *
     * <p>委托 {@link WorkflowDefinitionPort#validate} 完成以下校验维度：
     * <ul>
     *   <li>BPMN 2.0.2 XML 结构合法性；</li>
     *   <li>{@code process id} 符合 {@link ProcessDefinitionKey#PATTERN} 命名规则；</li>
     *   <li>语义化业务版本格式；</li>
     *   <li>内容哈希计算（SHA-256）；</li>
     *   <li>领域包版本关联有效性（若提供）；</li>
     *   <li>流程定义键与版本不冲突。</li>
     * </ul>
     *
     * <p>本方法为纯计算，不持久化、不携带幂等键（对应 OpenAPI
     * {@code POST /workflow-definitions/validate}）。
     *
     * @param key                    流程定义稳定键
     * @param businessVersion        业务版本
     * @param bpmnXml                BPMN 2.0.2 XML 文本
     * @param domainPackageVersionId 关联领域包版本 ID（可选，null 表示不关联）
     * @return 校验结果（含内容哈希与发现项）
     * @throws WorkflowEngineException BPMN 结构严重错误无法解析时
     */
    public ValidationResult validate(
            ProcessDefinitionKey key,
            ProcessVersion businessVersion,
            String bpmnXml,
            java.util.UUID domainPackageVersionId) {
        return definitionPort.validate(key, businessVersion, bpmnXml, domainPackageVersionId);
    }

    // ============================================================
    // 受控部署（幂等）
    // ============================================================

    /**
     * 部署已校验的 BPMN 流程定义。
     *
     * <p>部署请求 MUST 携带校验阶段生成的 {@code contentHash}，确保部署内容与校验一致。
     * 幂等规则：
     * <ul>
     *   <li>同 {@code (workspace, key, businessVersion)} 已部署且 contentHash 一致 →
     *       返回已有定义，不重复部署；</li>
     *   <li>同 {@code (workspace, key, businessVersion)} 已部署但 contentHash 不一致 →
     *       拒绝（防覆盖），抛 {@link WorkflowEngineException}；</li>
     *   <li>已处于 DEPRECATED/RETIRED 状态 → 拒绝重新部署；</li>
     *   <li>不存在或处于 VALIDATED → 执行部署。</li>
     * </ul>
     *
     * @param workspaceId            工作空间边界
     * @param key                    流程定义稳定键
     * @param businessVersion        业务版本
     * @param bpmnXml                BPMN 2.0.2 XML 文本
     * @param contentHash            校验阶段生成的内容哈希（SHA-256 hex）
     * @param domainPackageVersionId 关联领域包版本 ID（可选）
     * @param idempotencyKey         幂等键
     * @param deployedBy             部署者
     * @return 部署后的定义摘要
     * @throws WorkflowEngineException  内容哈希不匹配、BPMN 校验失败或部署冲突
     */
    public WorkflowDefinitionSummary deploy(
            WorkspaceId workspaceId,
            ProcessDefinitionKey key,
            ProcessVersion businessVersion,
            String bpmnXml,
            String contentHash,
            java.util.UUID domainPackageVersionId,
            IdempotencyKey idempotencyKey,
            ActorRef deployedBy) {

        // 幂等预检：同 key+version 已存在时按内容哈希决定幂等返回或拒绝
        Optional<WorkflowDefinitionRecord> existing = definitionRepository
                .findByKeyAndVersion(workspaceId, key, businessVersion);
        if (existing.isPresent()) {
            WorkflowDefinitionRecord record = existing.get();
            if (record.status() == WorkflowDefinitionStatus.DEPLOYED) {
                if (record.contentHash().equals(contentHash)) {
                    // 幂等返回已有定义，不重复部署
                    return toSummary(record);
                }
                // 同 key+version 已部署不同内容，拒绝覆盖
                throw new WorkflowEngineException(
                        WorkflowEngineException.Reason.DEFINITION_INVALID,
                        "流程定义 " + key + "@" + businessVersion
                                + " 已部署不同内容（哈希不匹配），禁止覆盖");
            }
            if (record.status() == WorkflowDefinitionStatus.DEPRECATED
                    || record.status() == WorkflowDefinitionStatus.RETIRED) {
                throw new WorkflowEngineException(
                        WorkflowEngineException.Reason.ILLEGAL_STATE_TRANSITION,
                        "流程定义 " + key + "@" + businessVersion
                                + " 当前状态为 " + record.status() + "，不可重新部署");
            }
            // VALIDATED 状态：继续部署流程，端口内部处理状态迁移
        }

        // 构造流程定义内容（值对象校验长度≥50、哈希非空）
        WorkflowDefinitionContent content = WorkflowDefinitionContent.of(bpmnXml, contentHash);

        // 委托端口完成 Flowable 部署与 PDP 记录持久化
        return definitionPort.deploy(
                key,
                businessVersion,
                content,
                domainPackageVersionId,
                idempotencyKey,
                deployedBy);
    }

    // ============================================================
    // 查询（工作空间边界隔离）
    // ============================================================

    /**
     * 按 ID 查询流程定义详情。
     *
     * @param workspaceId 工作空间边界
     * @param id          流程定义 ID
     * @return 定义摘要
     * @throws ResourceNotFoundException 不存在或跨工作空间访问
     */
    public WorkflowDefinitionSummary getById(WorkspaceId workspaceId, WorkflowDefinitionId id) {
        WorkflowDefinitionRecord record = loadWithinWorkspace(workspaceId, id);
        return toSummary(record);
    }

    /**
     * 按稳定键与业务版本查询流程定义。
     *
     * @param workspaceId     工作空间边界
     * @param key             流程定义稳定键
     * @param businessVersion 业务版本
     * @return 定义摘要，不存在时返回 empty
     */
    public Optional<WorkflowDefinitionSummary> getByKeyAndVersion(
            WorkspaceId workspaceId,
            ProcessDefinitionKey key,
            ProcessVersion businessVersion) {
        return definitionRepository
                .findByKeyAndVersion(workspaceId, key, businessVersion)
                .map(this::toSummary);
    }

    /**
     * 查询指定流程键的最新已部署版本。
     *
     * @param workspaceId 工作空间边界
     * @param key         流程定义稳定键
     * @return 最新已部署定义摘要，不存在时返回 empty
     */
    public Optional<WorkflowDefinitionSummary> getLatestDeployed(
            WorkspaceId workspaceId,
            ProcessDefinitionKey key) {
        return definitionRepository
                .findLatestDeployed(workspaceId, key)
                .map(this::toSummary);
    }

    /**
     * 分页查询流程定义。
     *
     * @param workspaceId 工作空间边界
     * @param keyFilter   流程键过滤前缀（可选，null 表示不过滤）
     * @param status      状态过滤（可选，null 表示所有状态）
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    public PageResult<WorkflowDefinitionSummary> list(
            WorkspaceId workspaceId,
            String keyFilter,
            WorkflowDefinitionStatus status,
            PageRequest pageRequest) {
        PageResult<WorkflowDefinitionRecord> records = definitionRepository
                .listDefinitions(workspaceId, keyFilter, status, pageRequest);
        return new PageResult<>(
                records.data().stream().map(this::toSummary).toList(),
                records.nextCursor(),
                records.hasMore(),
                records.total());
    }

    // ============================================================
    // 状态迁移（乐观并发控制）
    // ============================================================

    /**
     * 迁移流程定义状态（如 DEPLOYED → DEPRECATED、DEPRECATED → RETIRED）。
     *
     * <p>状态迁移 MUST 遵循 {@link WorkflowDefinitionStatus#canTransitionTo} 约束。
     * {@code RETIRED} 为终态不可恢复。
     *
     * @param workspaceId      工作空间边界
     * @param id               流程定义 ID
     * @param targetStatus     目标状态
     * @param expectedRevision 期望版本（乐观并发控制）
     * @param reason           迁移原因（审计）
     * @param actor            操作者
     * @return 迁移后的定义摘要
     * @throws ResourceNotFoundException 不存在或跨工作空间访问
     * @throws BusinessRuleException      状态迁移非法或版本冲突
     */
    public WorkflowDefinitionSummary transition(
            WorkspaceId workspaceId,
            WorkflowDefinitionId id,
            WorkflowDefinitionStatus targetStatus,
            int expectedRevision,
            String reason,
            ActorRef actor) {
        WorkflowDefinitionRecord record = loadWithinWorkspace(workspaceId, id);

        // 状态机校验
        if (!record.status().canTransitionTo(targetStatus)) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "流程定义状态迁移非法：" + record.status() + " → " + targetStatus
                            + "（" + reason + "）");
        }

        // 乐观锁更新
        if (!definitionRepository.transitionStatus(id, targetStatus, expectedRevision)) {
            throw new BusinessRuleException(ErrorCode.CONFLICT,
                    "流程定义状态迁移失败：版本冲突或并发修改");
        }

        // 重新查询返回最新视图
        return toSummary(loadWithinWorkspace(workspaceId, id));
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 加载流程定义并校验工作空间边界。
     *
     * @param workspaceId 工作空间边界
     * @param id          流程定义 ID
     * @return 流程定义聚合
     * @throws ResourceNotFoundException 不存在或跨工作空间访问（统一 404 语义）
     */
    private WorkflowDefinitionRecord loadWithinWorkspace(
            WorkspaceId workspaceId, WorkflowDefinitionId id) {
        WorkflowDefinitionRecord record = definitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", id.value()));
        // 工作空间边界隔离：跨工作空间访问统一返回 404，不泄露存在性
        if (!record.workspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("WorkflowDefinition", id.value());
        }
        return record;
    }

    /**
     * 将持久化聚合投影为公开读模型摘要。
     *
     * <p>丢弃 BPMN XML 内容、审计字段与乐观锁版本，仅保留 OpenAPI
     * {@code WorkflowDefinitionSummary} schema 定义的字段。
     *
     * @param record 流程定义聚合
     * @return 定义摘要
     */
    private WorkflowDefinitionSummary toSummary(WorkflowDefinitionRecord record) {
        // deployedAt：VALIDATED 状态未部署，其余状态以 updatedAt 近似部署时间
        java.time.Instant deployedAt = record.status() == WorkflowDefinitionStatus.VALIDATED
                ? null : record.updatedAt();
        return new WorkflowDefinitionSummary(
                record.id(),
                record.stableKey(),
                record.businessVersion(),
                record.contentHash(),
                record.status(),
                record.domainPackageVersionId(),
                deployedAt,
                record.findings());
    }
}

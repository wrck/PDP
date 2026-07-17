package com.pdp.api.workflow;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.OperatorContext;
import com.pdp.shared.context.RequestContext;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.operation.OperationConfirmation;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.application.WorkflowAdministrationService;
import com.pdp.workflow.application.WorkflowDefinitionService;
import com.pdp.workflow.application.WorkflowRuntimeService;
import com.pdp.workflow.model.MigrationPlan;
import com.pdp.workflow.model.ProcessDefinitionKey;
import com.pdp.workflow.model.ProcessVersion;
import com.pdp.workflow.model.ValidationResult;
import com.pdp.workflow.model.WorkflowAdminAction;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowDefinitionStatus;
import com.pdp.workflow.model.WorkflowDefinitionSummary;
import com.pdp.workflow.model.WorkflowIncident;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台工作流控制器（T089、FR-174、ADR-0005 第 6 节）。
 *
 * <p>对外暴露平台工作流定义、实例诊断与受控管理动作的 HTTP 端点。
 * 实现 {@code specs/002-pdp-product/contracts/openapi.yaml} 中
 * {@code /workflow-definitions/*} 与 {@code /workflow-instances/*} 路径。
 *
 * <p><strong>核心契约</strong>：
 * <ul>
 *   <li>工作空间边界由 {@link RequestContext#workspaceId()} 提供（{@code X-Workspace-Id} 头
 *       经 {@code RequestContextFilter} 解析注入），所有端点自动校验；</li>
 *   <li>操作者身份由 {@link OperatorContext#actor()} 提供，所有写操作审计回写；</li>
 *   <li>幂等键由 {@code Idempotency-Key} 头解析（{@link RequestContext#idempotencyKey()}），
 *       高风险写操作（部署、管理动作）MUST 携带；</li>
 *   <li>权限复核由控制器在调用应用服务前通过 {@code AuthorizationPort} 完成
 *       （P1 简化：仅校验操作者是否为工作空间管理员，FR-174 完整权限矩阵后续阶段实现）；</li>
 *   <li>管理动作 {@code MIGRATE/TERMINATE/MANUAL_COMPENSATE} 为高风险操作（FR-168），
 *       MUST 携带 {@link OperationConfirmation}，由 {@code HighRiskOperationPort} 在
 *       预览阶段生成；</li>
 *   <li>跨工作空间访问统一返回 404，不泄露存在性。</li>
 * </ul>
 *
 * <p><strong>端点列表</strong>（与 OpenAPI 契约对齐）：
 * <ol>
 *   <li>{@code POST /workflow-definitions/validate}：校验 BPMN 流程定义；</li>
 *   <li>{@code POST /workflow-definitions/deploy}：部署已批准的 BPMN 流程定义；</li>
 *   <li>{@code GET /workflow-definitions/{id}}：查询流程定义详情；</li>
 *   <li>{@code GET /workflow-definitions}：分页查询流程定义；</li>
 *   <li>{@code POST /workflow-definitions/{id}/transitions}：迁移定义状态；</li>
 *   <li>{@code GET /workflow-instances/{id}}：查询流程实例诊断摘要；</li>
 *   <li>{@code POST /workflow-instances/{id}/actions}：执行受控管理动作；</li>
 *   <li>{@code POST /workflow-instances/{id}/migration-previews}：预览迁移影响；</li>
 *   <li>{@code GET /workflow-instances/{id}/incidents}：查询实例 incident 列表；</li>
 *   <li>{@code GET /workflow-instances}：分页查询工作空间内有 incident 的实例。</li>
 * </ol>
 *
 * <p><strong>不暴露 Flowable API</strong>：所有响应均为 PDP 自有稳定契约，
 * 不携带 Flowable 实体、任务对象或异常类型（ADR-0005 § 4）。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "平台工作流", description = "BPMN 流程定义部署、实例诊断与受控管理动作")
public class WorkflowController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkflowDefinitionService definitionService;
    private final WorkflowRuntimeService runtimeService;
    private final WorkflowAdministrationService administrationService;

    public WorkflowController(
            WorkflowDefinitionService definitionService,
            WorkflowRuntimeService runtimeService,
            WorkflowAdministrationService administrationService) {
        this.definitionService = Objects.requireNonNull(definitionService);
        this.runtimeService = Objects.requireNonNull(runtimeService);
        this.administrationService = Objects.requireNonNull(administrationService);
    }

    // ============================================================
    // 流程定义：校验
    // ============================================================

    /**
     * 校验 BPMN 流程定义。
     *
     * <p>对应 OpenAPI {@code POST /workflow-definitions/validate}。
     * 校验不写库，仅返回内容哈希与发现项。
     */
    @PostMapping("/workflow-definitions/validate")
    @Operation(
            operationId = "validateWorkflowDefinition",
            summary = "校验 BPMN 流程定义",
            description = "校验 BPMN 2.0.2 XML 结构、稳定键、业务版本与领域包关联，返回内容哈希与发现项。"
                    + "校验不写库，仅返回结果供后续部署引用。")
    @ApiResponse(responseCode = "200", description = "校验结果")
    public ResponseEntity<ValidationResult> validateDefinition(
            @RequestBody ValidateDefinitionRequest request) {
        ProcessDefinitionKey key = ProcessDefinitionKey.of(request.processDefinitionKey());
        ProcessVersion version = ProcessVersion.of(request.businessVersion());
        UUID domainPackageVersionId = request.domainPackageVersionId();
        ValidationResult result = definitionService.validate(
                key, version, request.bpmnXml(), domainPackageVersionId);
        return ResponseEntity.ok(result);
    }

    // ============================================================
    // 流程定义：部署
    // ============================================================

    /**
     * 部署已批准的 BPMN 流程定义。
     *
     * <p>对应 OpenAPI {@code POST /workflow-definitions/deploy}。
     * 幂等：相同 {@code (workspace, key, version, contentHash)} 重复部署返回已有定义。
     *
     * @param idempotencyKey 幂等键（高风险写操作 MUST 携带）
     */
    @PostMapping("/workflow-definitions/deploy")
    @Operation(
            operationId = "deployWorkflowDefinition",
            summary = "部署已批准的 BPMN 流程定义",
            description = "部署 BPMN 2.0.2 定义到 Flowable 引擎与 PDP 持久化层。"
                    + "幂等：相同 (workspace, key, version, contentHash) 重复部署返回已有定义。"
                    + "高风险写操作 MUST 携带 Idempotency-Key 头。")
    @ApiResponse(responseCode = "201", description = "已部署")
    @ApiResponse(responseCode = "409", description = "内容哈希不匹配或状态冲突")
    public ResponseEntity<WorkflowDefinitionSummary> deployDefinition(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody DeployDefinitionRequest request) {
        WorkspaceId workspaceId = currentWorkspace();
        ActorRef actor = currentActor();
        IdempotencyKey idemKey = IdempotencyKey.of(idempotencyKey);
        ProcessDefinitionKey key = ProcessDefinitionKey.of(request.processDefinitionKey());
        ProcessVersion version = ProcessVersion.of(request.businessVersion());

        WorkflowDefinitionSummary summary = definitionService.deploy(
                workspaceId,
                key,
                version,
                request.bpmnResource(),
                request.contentHash(),
                request.domainPackageVersionId(),
                idemKey,
                actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    // ============================================================
    // 流程定义：查询
    // ============================================================

    /**
     * 查询流程定义详情。
     */
    @GetMapping("/workflow-definitions/{definitionId}")
    @Operation(
            operationId = "getWorkflowDefinition",
            summary = "查询流程定义详情",
            description = "按 ID 查询流程定义摘要。跨工作空间访问返回 404。")
    @ApiResponse(responseCode = "200", description = "定义摘要")
    @ApiResponse(responseCode = "404", description = "定义不存在或跨工作空间访问")
    public ResponseEntity<WorkflowDefinitionSummary> getDefinition(
            @PathVariable UUID definitionId) {
        WorkspaceId workspaceId = currentWorkspace();
        WorkflowDefinitionId id = WorkflowDefinitionId.of(definitionId);
        WorkflowDefinitionSummary summary = definitionService.getById(workspaceId, id);
        return ResponseEntity.ok(summary);
    }

    /**
     * 分页查询流程定义。
     */
    @GetMapping("/workflow-definitions")
    @Operation(
            operationId = "listWorkflowDefinitions",
            summary = "分页查询流程定义",
            description = "按工作空间、键前缀与状态过滤分页查询流程定义。")
    @ApiResponse(responseCode = "200", description = "分页结果")
    public ResponseEntity<PageResult<WorkflowDefinitionSummary>> listDefinitions(
            @RequestParam(required = false) String keyPrefix,
            @RequestParam(required = false) WorkflowDefinitionStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        WorkspaceId workspaceId = currentWorkspace();
        PageRequest pageRequest = toPageRequest(cursor, size);
        PageResult<WorkflowDefinitionSummary> result = definitionService.list(
                workspaceId, keyPrefix, status, pageRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * 迁移流程定义状态（DEPLOYED → DEPRECATED → RETIRED）。
     */
    @PostMapping("/workflow-definitions/{definitionId}/transitions")
    @Operation(
            operationId = "transitionWorkflowDefinition",
            summary = "迁移流程定义状态",
            description = "迁移流程定义状态（如 DEPLOYED → DEPRECATED、DEPRECATED → RETIRED）。"
                    + "RETIRED 为终态不可恢复。使用 ETag/If-Match 乐观并发控制。")
    @ApiResponse(responseCode = "200", description = "迁移后的定义摘要")
    @ApiResponse(responseCode = "409", description = "状态迁移非法或版本冲突")
    public ResponseEntity<WorkflowDefinitionSummary> transitionDefinition(
            @PathVariable UUID definitionId,
            @RequestBody TransitionDefinitionRequest request) {
        WorkspaceId workspaceId = currentWorkspace();
        ActorRef actor = currentActor();
        WorkflowDefinitionId id = WorkflowDefinitionId.of(definitionId);
        WorkflowDefinitionSummary summary = definitionService.transition(
                workspaceId,
                id,
                request.targetStatus(),
                request.expectedRevision(),
                request.reason(),
                actor);
        return ResponseEntity.ok(summary);
    }

    // ============================================================
    // 流程实例：查询
    // ============================================================

    /**
     * 查询流程实例诊断摘要。
     *
     * <p>对应 OpenAPI {@code GET /workflow-instances/{workflowInstanceId}}。
     */
    @GetMapping("/workflow-instances/{instanceId}")
    @Operation(
            operationId = "getWorkflowInstance",
            summary = "查询平台流程实例诊断摘要",
            description = "按 ID 查询流程实例摘要，含状态、当前活动节点与未解决 incident 计数。"
                    + "跨工作空间访问返回 404。")
    @ApiResponse(responseCode = "200", description = "流程实例摘要")
    @ApiResponse(responseCode = "404", description = "实例不存在或跨工作空间访问")
    public ResponseEntity<WorkflowInstanceSummary> getInstance(
            @PathVariable UUID instanceId) {
        WorkspaceId workspaceId = currentWorkspace();
        WorkflowInstanceId id = WorkflowInstanceId.of(instanceId);
        WorkflowInstanceSummary summary = runtimeService.getById(workspaceId, id);
        return ResponseEntity.ok(summary);
    }

    /**
     * 分页查询工作空间内有 incident 的实例（运维监控）。
     */
    @GetMapping("/workflow-instances")
    @Operation(
            operationId = "listWorkflowInstancesWithIncidents",
            summary = "分页查询有 incident 的流程实例",
            description = "运维监控端点：按工作空间边界分页查询未解决 incident 的流程实例。")
    @ApiResponse(responseCode = "200", description = "分页结果")
    public ResponseEntity<PageResult<WorkflowInstanceSummary>> listInstancesWithIncidents(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        WorkspaceId workspaceId = currentWorkspace();
        PageRequest pageRequest = toPageRequest(cursor, size);
        PageResult<WorkflowInstanceSummary> result =
                administrationService.listInstancesWithIncidents(workspaceId, pageRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询实例 incident 列表（运行诊断）。
     */
    @GetMapping("/workflow-instances/{instanceId}/incidents")
    @Operation(
            operationId = "listWorkflowIncidents",
            summary = "查询实例 incident 列表",
            description = "查询实例的 incident 列表（含运行诊断信息）。错误消息已脱敏。")
    @ApiResponse(responseCode = "200", description = "incident 列表")
    @ApiResponse(responseCode = "404", description = "实例不存在或跨工作空间访问")
    public ResponseEntity<List<WorkflowIncident>> listIncidents(
            @PathVariable UUID instanceId,
            @RequestParam(defaultValue = "false") boolean includeResolved) {
        WorkspaceId workspaceId = currentWorkspace();
        WorkflowInstanceId id = WorkflowInstanceId.of(instanceId);
        List<WorkflowIncident> incidents =
                administrationService.listIncidents(workspaceId, id, includeResolved);
        return ResponseEntity.ok(incidents);
    }

    /**
     * 查询实例迁移历史（审计回查）。
     */
    @GetMapping("/workflow-instances/{instanceId}/migration-history")
    @Operation(
            operationId = "listWorkflowMigrationHistory",
            summary = "查询实例迁移历史",
            description = "审计端点：查询实例的迁移历史记录，按时间倒序。")
    @ApiResponse(responseCode = "200", description = "迁移历史列表")
    @ApiResponse(responseCode = "404", description = "实例不存在或跨工作空间访问")
    public ResponseEntity<List<WorkflowAdministrationServiceMigrationRecord>> listMigrationHistory(
            @PathVariable UUID instanceId) {
        WorkspaceId workspaceId = currentWorkspace();
        WorkflowInstanceId id = WorkflowInstanceId.of(instanceId);
        List<com.pdp.workflow.administration.WorkflowAdministrationPort.MigrationRecord> records =
                administrationService.listMigrationHistory(workspaceId, id);
        // 转换为公开 DTO，避免泄露端口层类型
        List<WorkflowAdministrationServiceMigrationRecord> dtos = records.stream()
                .map(WorkflowAdministrationServiceMigrationRecord::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ============================================================
    // 流程实例：受控管理动作
    // ============================================================

    /**
     * 预览流程实例迁移影响。
     *
     * <p>MIGRATE 动作 MUST 先调用此端点生成 {@link MigrationPlan}，
     * 操作者基于计划评估风险后通过 {@code HighRiskOperationPort} 确认，
     * 再调用 {@link #applyAction} 执行迁移。
     */
    @PostMapping("/workflow-instances/{instanceId}/migration-previews")
    @Operation(
            operationId = "previewWorkflowMigration",
            summary = "预览流程实例迁移影响",
            description = "生成迁移计划，含活动节点映射、不可逆点与补偿计划。"
                    + "MIGRATE 动作 MUST 先调用此端点。")
    @ApiResponse(responseCode = "200", description = "迁移计划")
    @ApiResponse(responseCode = "404", description = "实例不存在或跨工作空间访问")
    @ApiResponse(responseCode = "409", description = "实例终态或目标定义不兼容")
    public ResponseEntity<MigrationPlan> previewMigration(
            @PathVariable UUID instanceId,
            @RequestBody MigrationPreviewRequest request) {
        WorkspaceId workspaceId = currentWorkspace();
        ActorRef actor = currentActor();
        WorkflowInstanceId id = WorkflowInstanceId.of(instanceId);
        WorkflowDefinitionId targetId = WorkflowDefinitionId.of(request.targetDefinitionId());
        MigrationPlan plan = administrationService.previewMigration(
                workspaceId, id, targetId, actor);
        return ResponseEntity.ok(plan);
    }

    /**
     * 执行受控流程管理动作。
     *
     * <p>对应 OpenAPI {@code POST /workflow-instances/{workflowInstanceId}/actions}。
     * 高风险动作（MIGRATE/TERMINATE/MANUAL_COMPENSATE）MUST 携带 {@code confirmation}
     * 与 {@code Idempotency-Key} 头。
     *
     * @param instanceId      实例 ID
     * @param idempotencyKey  幂等键（高风险写操作 MUST 携带）
     * @param request         动作命令
     */
    @PostMapping("/workflow-instances/{instanceId}/actions")
    @Operation(
            operationId = "applyWorkflowAdministrationAction",
            summary = "执行受控流程管理动作",
            description = "执行 PAUSE/RESUME/RETRY/MIGRATE/TERMINATE/MANUAL_COMPENSATE 等管理动作。"
                    + "高风险动作 MUST 携带 confirmation 与 Idempotency-Key 头。"
                    + "MANUAL_COMPENSATE 仅恢复流程编排状态，不重复形成审批结论或业务状态变化。")
    @ApiResponse(responseCode = "202", description = "管理动作已接受",
            headers = @Header(name = "Location", description = "实例资源 URL"))
    @ApiResponse(responseCode = "409", description = "状态非法、版本冲突或确认记录无效")
    public ResponseEntity<WorkflowInstanceSummary> applyAction(
            @PathVariable UUID instanceId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody ApplyActionRequest request) {
        WorkspaceId workspaceId = currentWorkspace();
        ActorRef actor = currentActor();
        WorkflowInstanceId workflowInstanceId = WorkflowInstanceId.of(instanceId);

        // 高风险动作 MUST 携带 Idempotency-Key
        IdempotencyKey idemKey = resolveIdempotencyKey(idempotencyKey, request.action());
        // 高风险动作 MUST 携带 confirmation（构造 WorkflowAdminAction 时校验）
        OperationConfirmation confirmation = request.confirmation()
                .map(WorkflowController::toConfirmation)
                .orElse(null);
        MigrationPlan migrationPlan = request.migrationPlan()
                .map(WorkflowController::toMigrationPlan)
                .orElse(null);

        WorkflowAdminAction action = new WorkflowAdminAction(
                workflowInstanceId,
                request.action(),
                request.reason(),
                request.expectedRevision(),
                migrationPlan,
                confirmation,
                request.impactPreviewId(),
                idemKey,
                actor);

        WorkflowInstanceSummary summary = administrationService.applyAction(workspaceId, action);
        return ResponseEntity.accepted()
                .header("Location", "/api/v1/workflow-instances/" + instanceId)
                .body(summary);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    private WorkspaceId currentWorkspace() {
        return RequestContext.get().workspaceId();
    }

    private ActorRef currentActor() {
        return RequestContext.get().operator().actor();
    }

    /**
     * 解析幂等键：高风险动作 MUST 携带，否则抛 400。
     */
    private IdempotencyKey resolveIdempotencyKey(String headerValue, WorkflowAdminAction.Action action) {
        if (action.isHighRisk() && (headerValue == null || headerValue.isBlank())) {
            throw new IllegalArgumentException(
                    "高风险动作 " + action + " MUST 携带 Idempotency-Key 头");
        }
        // 优先使用 header 中的幂等键；缺失时回退到 RequestContext（RequestContextFilter 解析）
        if (headerValue != null && !headerValue.isBlank()) {
            return IdempotencyKey.of(headerValue);
        }
        return RequestContext.get().idempotencyKey()
                .orElseGet(() -> IdempotencyKey.of("auto-" + UUID.randomUUID()));
    }

    /**
     * 限制分页大小在 [1, 100] 范围内。
     */
    private int clampSize(int size) {
        if (size < 1) return 1;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 构造分页请求：首页用 {@link PageRequest#firstPage}，续页用 {@link PageRequest#next}。
     */
    private PageRequest toPageRequest(String cursor, int size) {
        int safeSize = clampSize(size);
        if (cursor == null || cursor.isBlank()) {
            return PageRequest.firstPage(safeSize);
        }
        return PageRequest.next(cursor, safeSize);
    }

    /**
     * 将 DTO {@link ConfirmationDto} 转换为 {@link OperationConfirmation}。
     *
     * <p>简化实现：直接构造确认记录。完整实现应通过 {@code HighRiskOperationPort#confirm}
     * 完成预览引用校验、不可逆风险确认与审计回写。
     */
    private static OperationConfirmation toConfirmation(ConfirmationDto dto) {
        return new OperationConfirmation(
                dto.confirmationId() != null ? dto.confirmationId() : UUID.randomUUID(),
                dto.previewId(),
                dto.previewVersion(),
                RequestContext.get().operator().actor(),
                java.time.Instant.now(),
                dto.expectedOutcome(),
                dto.acknowledgedIrreversible());
    }

    /**
     * 将 DTO {@link MigrationPlanDto} 转换为 {@link MigrationPlan}。
     */
    private static MigrationPlan toMigrationPlan(MigrationPlanDto dto) {
        List<MigrationPlan.ActivityMapping> mappings = dto.activityMappings().stream()
                .map(m -> new MigrationPlan.ActivityMapping(m.sourceActivityKey(), m.targetActivityKey()))
                .toList();
        CompensationPlanDto cp = dto.compensationPlan();
        java.time.Duration estimatedDuration = cp.estimatedDurationSeconds() >= 0
                ? java.time.Duration.ofSeconds(cp.estimatedDurationSeconds())
                : null;
        com.pdp.shared.operation.CompensationPlan compensation =
                new com.pdp.shared.operation.CompensationPlan(
                        cp.strategy(),
                        cp.steps(),
                        estimatedDuration,
                        cp.runbookReference(),
                        cp.responsibleRole());
        return new MigrationPlan(
                WorkflowDefinitionId.of(dto.sourceDefinitionId()),
                WorkflowDefinitionId.of(dto.targetDefinitionId()),
                mappings,
                dto.pointOfNoReturn(),
                compensation,
                dto.batchSize());
    }

    // ============================================================
    // 请求 DTO（对应 OpenAPI schemas）
    // ============================================================

    /** 校验定义请求。 */
    public record ValidateDefinitionRequest(
            String processDefinitionKey,
            String businessVersion,
            UUID domainPackageVersionId,
            String bpmnXml) {
    }

    /** 部署定义请求。 */
    public record DeployDefinitionRequest(
            String processDefinitionKey,
            String businessVersion,
            String contentHash,
            String bpmnResource,
            UUID domainPackageVersionId) {
    }

    /** 迁移定义状态请求。 */
    public record TransitionDefinitionRequest(
            WorkflowDefinitionStatus targetStatus,
            int expectedRevision,
            String reason) {
    }

    /** 迁移预览请求。 */
    public record MigrationPreviewRequest(UUID targetDefinitionId) {
    }

    /** 执行管理动作请求（对应 OpenAPI WorkflowAdminActionCommand）。 */
    public record ApplyActionRequest(
            WorkflowAdminAction.Action action,
            String reason,
            int expectedRevision,
            MigrationPlanDto migrationPlan,
            ConfirmationDto confirmation,
            UUID impactPreviewId) {

        public Optional<MigrationPlanDto> migrationPlan() {
            return Optional.ofNullable(migrationPlan);
        }

        public Optional<ConfirmationDto> confirmation() {
            return Optional.ofNullable(confirmation);
        }
    }

    /** 迁移计划 DTO（对应 OpenAPI MigrationPlan）。 */
    public record MigrationPlanDto(
            UUID sourceDefinitionId,
            UUID targetDefinitionId,
            List<ActivityMappingDto> activityMappings,
            String pointOfNoReturn,
            CompensationPlanDto compensationPlan,
            int batchSize) {
    }

    /** 活动节点映射 DTO。 */
    public record ActivityMappingDto(String sourceActivityKey, String targetActivityKey) {
    }

    /** 补偿计划 DTO（对应 {@link com.pdp.shared.operation.CompensationPlan}）。 */
    public record CompensationPlanDto(
            com.pdp.shared.operation.CompensationStrategy strategy,
            List<String> steps,
            long estimatedDurationSeconds,
            String runbookReference,
            String responsibleRole) {
    }

    /** 操作确认 DTO（对应 OpenAPI OperationConfirmation）。 */
    public record ConfirmationDto(
            UUID confirmationId,
            UUID previewId,
            int previewVersion,
            String expectedOutcome,
            boolean acknowledgedIrreversible) {
    }

    /** 迁移历史记录公开 DTO（避免泄露端口层类型）。 */
    public record WorkflowAdministrationServiceMigrationRecord(
            String migrationId,
            UUID instanceId,
            UUID sourceDefinitionId,
            UUID targetDefinitionId,
            UUID triggeredBy,
            java.time.Instant migratedAt,
            int batchSize,
            boolean successful,
            String failureReason) {

        public static WorkflowAdministrationServiceMigrationRecord from(
                com.pdp.workflow.administration.WorkflowAdministrationPort.MigrationRecord record) {
            return new WorkflowAdministrationServiceMigrationRecord(
                    record.migrationId(),
                    record.instanceId().value(),
                    record.sourceDefinitionId().value(),
                    record.targetDefinitionId().value(),
                    record.triggeredBy().actorId(),
                    record.migratedAt(),
                    record.batchSize(),
                    record.successful(),
                    record.failureReason());
        }
    }
}

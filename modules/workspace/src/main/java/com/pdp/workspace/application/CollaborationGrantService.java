package com.pdp.workspace.application;

import com.pdp.shared.error.BusinessRuleException;
import com.pdp.shared.error.ConflictException;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.ResourceNotFoundException;
import com.pdp.shared.id.UuidV7Generator;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.GrantStatus;
import com.pdp.workspace.port.CollaborationGrantRepository;
import com.pdp.workspace.port.GrantDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 跨工作空间协作授权应用服务（FR-006）。
 *
 * <p>授权方工作空间 {@code workspaceId} 将指定目标对象的部分操作权限授予协作方工作空间
 * {@code collaboratorWorkspaceId}，按 {@code roleId} 与 {@code allowedActions} 限定。
 * 授权可撤销（{@link GrantStatus#REVOKED}），到期自动失效（{@link GrantStatus#EXPIRED}）。
 *
 * <p><strong>核心不变量</strong>：
 * <ul>
 *   <li>授权方与协作方不能为同一工作空间（由 {@link CollaborationGrant} 构造器校验）；</li>
 *   <li>授权目标对象必须存在且属于授权方工作空间（由调用方在 controller 层校验，
 *       本服务不直接依赖业务对象仓储）；</li>
 *   <li>授权创建时初始状态为 {@link GrantStatus#ACTIVE}（P1 简化，跳过 DRAFT 审批流）；</li>
 *   <li>授权方工作空间必须为 ACTIVE 状态（由 controller 校验）。</li>
 * </ul>
 *
 * <p><strong>乐观锁</strong>：{@link #revokeGrant} 通过 {@code WHERE revision = #{expectedRevision}}
 * 与 {@code SET revision = revision + 1} 实现；版本冲突或非 ACTIVE 状态时返回 {@code false}，
 * 抛 {@link ConflictException}（HTTP 409）。
 *
 * <p><strong>到期失效</strong>：{@link #expireDueGrants} 由定时任务调度，
 * 将所有 {@code valid_until < now} 的 ACTIVE 授权迁移为 EXPIRED（单条 UPDATE 原子完成）。
 *
 * <p><strong>查询边界</strong>：{@link #listGrants} 按授权方向查询：
 * {@link GrantDirection#OUTGOING} 按 {@code workspaceId}（授权方）；
 * {@link GrantDirection#INCOMING} 按 {@code collaboratorWorkspaceId}（被授权方）。
 * 跨空间访问不会泄露授权方专属信息。
 */
@Service
public class CollaborationGrantService {

    private final CollaborationGrantRepository grantRepository;

    public CollaborationGrantService(CollaborationGrantRepository grantRepository) {
        this.grantRepository = grantRepository;
    }

    /**
     * 创建跨工作空间协作授权。
     *
     * @param workspaceId            授权方工作空间 ID
     * @param collaboratorWorkspaceId 协作方工作空间 ID（不能与授权方相同）
     * @param targetObjectType       目标对象类型（如 {@code "project"}、{@code "deliverable"}）
     * @param targetObjectId          目标对象 ID
     * @param roleId                  授予的角色 ID（必须属于授权方工作空间，由 controller 校验）
     * @param allowedActions          允许的动作键列表（如 {@code ["project.task.create"]}）
     * @param validUntil              有效期；null 表示永久（P1 不推荐）
     * @param reason                  授权原因（可选，记录于审计字段）
     * @return 已创建的 {@link CollaborationGrant}，初始状态 {@link GrantStatus#ACTIVE}
     */
    @Transactional
    public CollaborationGrant createGrant(UUID workspaceId, UUID collaboratorWorkspaceId,
                                          String targetObjectType, UUID targetObjectId,
                                          UUID roleId, List<String> allowedActions,
                                          Instant validUntil, String reason) {
        if (workspaceId.equals(collaboratorWorkspaceId)) {
            throw new BusinessRuleException("协作方工作空间不能与授权方相同");
        }
        if (allowedActions == null || allowedActions.isEmpty()) {
            throw new BusinessRuleException("允许动作列表不能为空");
        }
        Instant now = Instant.now();
        if (validUntil != null && !now.isBefore(validUntil)) {
            throw new BusinessRuleException("有效期必须晚于当前时间");
        }
        CollaborationGrant grant = new CollaborationGrant(
                UuidV7Generator.next(),
                workspaceId,
                collaboratorWorkspaceId,
                targetObjectType,
                targetObjectId,
                roleId,
                allowedActions,
                validUntil,
                GrantStatus.ACTIVE,
                reason,
                null,
                null,
                1,
                now,
                now);
        grantRepository.save(grant);
        return grant;
    }

    /**
     * 按工作空间分页查询授权。
     *
     * @param workspaceId 工作空间 ID
     * @param direction    授权方向；OUTGOING 按 workspaceId（授权方），INCOMING 按 collaboratorWorkspaceId（被授权方）
     * @param status       状态过滤；null 表示不过滤
     */
    public PageResult<CollaborationGrant> listGrants(UUID workspaceId, GrantDirection direction,
                                                     GrantStatus status, PageRequest pageRequest) {
        if (direction == null) {
            direction = GrantDirection.OUTGOING;
        }
        return grantRepository.findByWorkspace(workspaceId, direction, status, pageRequest);
    }

    /** 查询授权详情。 */
    public CollaborationGrant getGrant(UUID grantId) {
        return grantRepository.findById(grantId)
                .orElseThrow(() -> new ResourceNotFoundException("CollaborationGrant", grantId));
    }

    /**
     * 撤销授权（ACTIVE → REVOKED，乐观锁）。
     *
     * <p>记录撤销原因与时间，递增 revision。仅 ACTIVE 状态可撤销；非 ACTIVE 返回 0 行
     * 视为状态冲突，抛 {@link ConflictException}。
     */
    @Transactional
    public void revokeGrant(UUID grantId, String reason, int expectedRevision) {
        CollaborationGrant grant = getGrant(grantId);
        if (!grant.canRevoke()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "授权当前状态不可撤销: " + grant.status());
        }
        if (!grantRepository.revoke(grantId, reason, Instant.now(), expectedRevision)) {
            throw new ConflictException("CollaborationGrant", grantId,
                    grant.status().name(), (long) expectedRevision);
        }
    }

    /**
     * 到期失效授权（定时任务调度）。
     *
     * <p>将所有 {@code valid_until < now} 的 ACTIVE 授权迁移为 EXPIRED（单条 UPDATE 原子完成）。
     * 由 {@code @Scheduled} 后台作业调度（在 apps/api 注册），保证撤权 SLA。
     *
     * @return 受影响行数
     */
    @Transactional
    public int expireDueGrants() {
        return grantRepository.expireDue(Instant.now());
    }
}

package com.pdp.workflow.model;

import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 业务对象引用值对象（FR-174、ADR-0005）。
 *
 * <p>用于流程实例与 PDP 业务对象（审批、项目、任务、交付件、领域包版本）的稳定关联。
 * 流程实例 MUST NOT 直接持有权威业务对象，仅保存最小编排标识用于回查。
 *
 * <p><strong>不变量</strong>（ADR-0005 第 7 节）：
 * <ul>
 *   <li>引用类型使用稳定业务对象类型键（如 {@code approval}、{@code project}、
 *       {@code deliverable}、{@code domain-package-version}），禁止使用 Flowable 表名；</li>
 *   <li>引用 ID 为业务对象主键（UUIDv7），禁止使用 Flowable 引擎内部 ID；</li>
 *   <li>业务对象的状态变化由聚合决定，流程实例 MUST NEVER 作为业务结论唯一存储。</li>
 * </ul>
 *
 * @param workspaceId   工作空间边界（流程实例不跨工作空间）
 * @param objectType    业务对象类型键（稳定字符串）
 * @param objectId      业务对象 ID（UUIDv7）
 * @param correlationKey 可选关联键（如审批定义键、项目模板实例化批次），用于幂等编排
 */
public record BusinessObjectRef(
        WorkspaceId workspaceId,
        String objectType,
        UUID objectId,
        String correlationKey) {

    public BusinessObjectRef {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        if (objectType.isBlank()) {
            throw new IllegalArgumentException("objectType 不能为空白");
        }
        Objects.requireNonNull(objectId, "objectId 不能为 null");
    }

    /**
     * 创建业务对象引用。
     *
     * @param workspaceId 工作空间
     * @param objectType  业务对象类型
     * @param objectId    业务对象 ID
     * @return 引用实例
     */
    public static BusinessObjectRef of(WorkspaceId workspaceId, String objectType, UUID objectId) {
        return new BusinessObjectRef(workspaceId, objectType, objectId, null);
    }

    /**
     * 创建带关联键的业务对象引用。
     *
     * @param workspaceId    工作空间
     * @param objectType     业务对象类型
     * @param objectId       业务对象 ID
     * @param correlationKey 关联键（用于幂等编排，可空）
     * @return 引用实例
     */
    public static BusinessObjectRef of(WorkspaceId workspaceId, String objectType, UUID objectId,
                                       String correlationKey) {
        return new BusinessObjectRef(workspaceId, objectType, objectId, correlationKey);
    }

    /**
     * 是否匹配指定业务对象。
     *
     * @param otherType 业务对象类型
     * @param otherId   业务对象 ID
     * @return true 表示引用同一业务对象
     */
    public boolean matches(String otherType, UUID otherId) {
        return objectType.equals(otherType) && objectId.equals(otherId);
    }
}

package com.pdp.shared.operation;

import java.util.Objects;

/**
 * 不可逆点描述（FR-149、FR-168）。
 *
 * <p>定义操作执行过程中"一旦跨过即无法回退"的临界点。到达不可逆点后：
 * <ul>
 *   <li>操作只能继续执行完成，或触发补偿路径（前向修复或反向同步）；</li>
 *   <li>不能简单回退到操作前状态；</li>
 *   <li>{@link OperationState#CANCELLED} 不再可用，状态机进入 {@link OperationState#EXECUTING} 后
 *       只能流向 {@link OperationState#COMPLETED}、{@link OperationState#COMPENSATED} 或
 *       {@link OperationState#FAILED}。</li>
 * </ul>
 *
 * <p>典型场景：
 * <ul>
 *   <li>历史迁移/数据库切换：PDP 开始产生新业务写入后即达不可逆点
 *       （FR-149：除非已验证反向同步，否则不得直接恢复旧系统为写入主系统）。</li>
 *   <li>数据处置：法律保留解除并物理删除后即达不可逆点。</li>
 *   <li>交付件发布：发布到下游系统并通知外部干系人后即达不可逆点。</li>
 * </ul>
 *
 * @param stage                  不可逆点所处阶段标识（如 DATABASE_SWITCH.SOFTWARE_START）
 * @param description            人类可读描述
 * @param prerequisiteForReversal 反向恢复的前置条件（如"已验证反向同步"），null 表示无前置条件
 * @param compensationStrategy   推荐补偿策略
 */
public record PointOfNoReturn(
        String stage,
        String description,
        String prerequisiteForReversal,
        CompensationStrategy compensationStrategy) {

    public PointOfNoReturn {
        Objects.requireNonNull(stage, "stage 不能为空");
        if (stage.isBlank()) {
            throw new IllegalArgumentException("stage 不能为空白");
        }
        Objects.requireNonNull(description, "description 不能为空");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description 不能为空白");
        }
        Objects.requireNonNull(compensationStrategy, "compensationStrategy 不能为空");
    }

    /**
     * 创建不可逆点（无反向恢复前置条件，使用指定补偿策略）。
     */
    public static PointOfNoReturn of(String stage, String description,
                                      CompensationStrategy strategy) {
        return new PointOfNoReturn(stage, description, null, strategy);
    }

    /**
     * 是否需要前置条件才能反向恢复。
     */
    public boolean requiresPrerequisiteForReversal() {
        return prerequisiteForReversal != null && !prerequisiteForReversal.isBlank();
    }
}

package com.pdp.shared.operation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 补偿计划（FR-168）。
 *
 * <p>定义操作失败或主动触发补偿时的恢复步骤。计划在影响预览生成时同步制定，
 * 操作者基于计划评估风险后确认操作。
 *
 * <p><strong>与不可逆点的关系</strong>：
 * <ul>
 *   <li>未达不可逆点：可使用 {@link CompensationStrategy#ROLLBACK} 恢复操作前状态；</li>
 *   <li>已过不可逆点：只能使用 {@link CompensationStrategy#REVERSE_SYNC}、
 *       {@link CompensationStrategy#MANUAL} 或 {@link CompensationStrategy#NONE}。</li>
 * </ul>
 *
 * @param strategy           补偿策略
 * @param steps              补偿步骤（人类可读，对应运行手册章节）
 * @param estimatedDuration  预估补偿时长（null 表示未估算）
 * @param runbookReference   运行手册引用（如文档路径或章节号），可选
 * @param responsibleRole    负责补偿的角色（如 ONCALL、DBA、PRODUCT_OWNER）
 */
public record CompensationPlan(
        CompensationStrategy strategy,
        List<String> steps,
        Duration estimatedDuration,
        String runbookReference,
        String responsibleRole) {

    public CompensationPlan {
        Objects.requireNonNull(strategy, "strategy 不能为空");
        steps = steps == null ? List.of() : List.copyOf(steps);
        Objects.requireNonNull(responsibleRole, "responsibleRole 不能为空");
        if (responsibleRole.isBlank()) {
            throw new IllegalArgumentException("responsibleRole 不能为空白");
        }
    }

    /**
     * 创建无补偿计划（{@link CompensationStrategy#NONE}）。
     *
     * <p>用于不可逆且无补偿路径的操作。操作者 MUST 显式确认接受风险。
     */
    public static CompensationPlan none(String responsibleRole, String runbookReference) {
        return new CompensationPlan(
                CompensationStrategy.NONE, List.of(), null, runbookReference, responsibleRole);
    }

    /**
     * 创建回滚计划（{@link CompensationStrategy#ROLLBACK}）。
     */
    public static CompensationPlan rollback(List<String> steps, Duration estimatedDuration,
                                             String runbookReference, String responsibleRole) {
        return new CompensationPlan(
                CompensationStrategy.ROLLBACK, steps, estimatedDuration,
                runbookReference, responsibleRole);
    }

    /**
     * 创建人工补偿计划（{@link CompensationStrategy#MANUAL}）。
     */
    public static CompensationPlan manual(List<String> steps, Duration estimatedDuration,
                                           String runbookReference, String responsibleRole) {
        return new CompensationPlan(
                CompensationStrategy.MANUAL, steps, estimatedDuration,
                runbookReference, responsibleRole);
    }

    /**
     * 计划是否可用（NONE 策略时表示无补偿，调用方需特殊处理）。
     */
    public boolean hasCompensation() {
        return strategy != CompensationStrategy.NONE;
    }
}

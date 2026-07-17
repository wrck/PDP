package com.pdp.workflow.model;

import com.pdp.shared.operation.CompensationPlan;

import java.util.List;
import java.util.Objects;

/**
 * 流程实例迁移计划值对象（FR-174、ADR-0005 第 6 节）。
 *
 * <p>迁移 MUST 预览、分批、可暂停并保留证据（ADR-0005）。计划在影响预览生成时同步制定，
 * 操作者基于计划评估风险后确认。
 *
 * <p><strong>迁移规则</strong>：
 * <ul>
 *   <li>目标定义版本 MUST 与源版本主版本兼容或为同主版本更高版本；</li>
 *   <li>活动节点映射 MUST 覆盖源实例所有运行中活动；</li>
 *   <li>不可逆变更（如删除运行中节点）MUST 在 {@link #pointOfNoReturn} 标注；</li>
 *   <li>迁移失败 MUST 可回滚或人工补偿（{@link #compensationPlan}）。</li>
 * </ul>
 *
 * @param sourceDefinitionId  源流程定义 ID
 * @param targetDefinitionId  目标流程定义 ID
 * @param activityMappings    活动节点映射（源活动键 → 目标活动键）
 * @param pointOfNoReturn     不可逆点描述（null 表示无不可逆点）
 * @param compensationPlan    补偿计划（迁移失败时执行）
 * @param batchSize           批次大小（分批迁移，0 表示不分批）
 */
public record MigrationPlan(
        WorkflowDefinitionId sourceDefinitionId,
        WorkflowDefinitionId targetDefinitionId,
        List<ActivityMapping> activityMappings,
        String pointOfNoReturn,
        CompensationPlan compensationPlan,
        int batchSize) {

    /** 活动节点映射。 */
    public record ActivityMapping(String sourceActivityKey, String targetActivityKey) {
        public ActivityMapping {
            Objects.requireNonNull(sourceActivityKey, "sourceActivityKey 不能为 null");
            if (sourceActivityKey.isBlank()) {
                throw new IllegalArgumentException("sourceActivityKey 不能为空白");
            }
            Objects.requireNonNull(targetActivityKey, "targetActivityKey 不能为 null");
            if (targetActivityKey.isBlank()) {
                throw new IllegalArgumentException("targetActivityKey 不能为空白");
            }
        }
    }

    public MigrationPlan {
        Objects.requireNonNull(sourceDefinitionId, "sourceDefinitionId 不能为 null");
        Objects.requireNonNull(targetDefinitionId, "targetDefinitionId 不能为 null");
        activityMappings = activityMappings == null
                ? List.of() : List.copyOf(activityMappings);
        Objects.requireNonNull(compensationPlan, "compensationPlan 不能为 null");
        if (batchSize < 0) {
            throw new IllegalArgumentException("batchSize 不能为负");
        }
    }

    public boolean hasIrreversibleImpact() {
        return pointOfNoReturn != null && !pointOfNoReturn.isBlank();
    }

    public boolean isBatched() {
        return batchSize > 0;
    }
}

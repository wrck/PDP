package com.pdp.domainconfig.domain.behavior;

import com.pdp.domainconfig.domain.packageversion.DomainPackageVersionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 领域包对平台流程定义的版本化绑定（FR-173）。
 *
 * <p>FR-173 平台工作流复用：领域包 MUST 通过本绑定引用平台注册的 BPMN 2.0.2 流程定义，
 * 不允许嵌入 Flowable 专有 API。绑定包含：
 * <ul>
 *   <li>{@link #processDefinitionKey} + {@link #businessVersion}：版本化引用平台流程；</li>
 *   <li>{@link #trigger}：触发器（手动 / 对象创建 / 状态迁移 / 领域事件）；</li>
 *   <li>{@link #variableMappings}：领域对象字段到流程变量的映射；</li>
 *   <li>{@link #declaredPermissions}：声明本绑定所需权限，不允许提升当前用户权限；</li>
 *   <li>{@link #startupConditionRuleKey}：启动条件规则键；</li>
 *   <li>{@link #migrationValidationRuleKey}：迁移校验规则键；</li>
 *   <li>{@link #instanceMigrationPolicy}：版本升级时存量运行实例的迁移策略。</li>
 * </ul>
 *
 * <p>{@link #versionId} 为 null 表示绑定随包内所有版本生效；非 null 表示绑定特定版本。
 */
public record DomainPackageWorkflowBinding(
        UUID id,
        UUID packageId,
        UUID versionId,
        String stableKey,
        String processDefinitionKey,
        String businessVersion,
        String bpmnResource,
        WorkflowBindingTrigger trigger,
        String objectTypeKey,
        String eventType,
        String authorizationPolicyKey,
        Map<String, String> variableMappings,
        String startupConditionRuleKey,
        List<String> declaredPermissions,
        InstanceMigrationPolicy instanceMigrationPolicy,
        String migrationValidationRuleKey,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public DomainPackageWorkflowBinding {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (packageId == null) {
            throw new IllegalArgumentException("packageId 不能为 null");
        }
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("processDefinitionKey 不能为空");
        }
        if (businessVersion == null || businessVersion.isBlank()) {
            throw new IllegalArgumentException("businessVersion 不能为空");
        }
        if (bpmnResource == null || bpmnResource.isBlank()) {
            throw new IllegalArgumentException("bpmnResource 不能为空");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger 不能为 null");
        }
        if (authorizationPolicyKey == null || authorizationPolicyKey.isBlank()) {
            throw new IllegalArgumentException("authorizationPolicyKey 不能为空");
        }
        if (instanceMigrationPolicy == null) {
            instanceMigrationPolicy = InstanceMigrationPolicy.PINNED;
        }
        if (trigger == WorkflowBindingTrigger.DOMAIN_EVENT
                && (eventType == null || eventType.isBlank())) {
            throw new IllegalArgumentException("DOMAIN_EVENT 触发必须指定 eventType");
        }
        variableMappings = variableMappings == null ? Map.of() : Map.copyOf(variableMappings);
        declaredPermissions = declaredPermissions == null ? List.of() : List.copyOf(declaredPermissions);
    }

    /** 判断绑定是否适用于指定版本状态（仅 PUBLISHED/FROZEN/DEPRECATED 状态生效）。 */
    public boolean isEffectiveFor(DomainPackageVersionStatus status) {
        return status == DomainPackageVersionStatus.PUBLISHED
                || status == DomainPackageVersionStatus.FROZEN
                || status == DomainPackageVersionStatus.DEPRECATED;
    }
}

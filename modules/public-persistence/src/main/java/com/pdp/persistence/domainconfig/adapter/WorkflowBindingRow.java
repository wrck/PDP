package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.behavior.DomainPackageWorkflowBinding;
import com.pdp.domainconfig.domain.behavior.InstanceMigrationPolicy;
import com.pdp.domainconfig.domain.behavior.WorkflowBindingTrigger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流绑定持久化行（{@code domain_package_workflow_binding}）。
 *
 * <p>{@code variableMappingsJson} 为 {@code Map<String, String>} 的 JSON 序列化字符串；
 * {@code declaredPermissionsJson} 为 {@code List<String>} 的 JSON 序列化字符串。
 */
public record WorkflowBindingRow(
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
        String variableMappingsJson,
        String startupConditionRuleKey,
        String declaredPermissionsJson,
        InstanceMigrationPolicy instanceMigrationPolicy,
        String migrationValidationRuleKey,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    /** 从行还原 {@link DomainPackageWorkflowBinding}。 */
    public DomainPackageWorkflowBinding toBinding() {
        Map<String, String> variableMappings =
                DomainPackageJsonCodec.readStringMap(variableMappingsJson);
        List<String> declaredPermissions =
                DomainPackageJsonCodec.readStringList(declaredPermissionsJson);
        return new DomainPackageWorkflowBinding(
                id, packageId, versionId, stableKey,
                processDefinitionKey, businessVersion, bpmnResource, trigger,
                objectTypeKey, eventType, authorizationPolicyKey,
                variableMappings, startupConditionRuleKey, declaredPermissions,
                instanceMigrationPolicy, migrationValidationRuleKey,
                revision, createdAt, updatedAt);
    }

    /** 从 {@link DomainPackageWorkflowBinding} 拆解为行。 */
    public static WorkflowBindingRow fromBinding(DomainPackageWorkflowBinding binding) {
        return new WorkflowBindingRow(
                binding.id(),
                binding.packageId(),
                binding.versionId(),
                binding.stableKey(),
                binding.processDefinitionKey(),
                binding.businessVersion(),
                binding.bpmnResource(),
                binding.trigger(),
                binding.objectTypeKey(),
                binding.eventType(),
                binding.authorizationPolicyKey(),
                DomainPackageJsonCodec.writeStringMap(binding.variableMappings()),
                binding.startupConditionRuleKey(),
                DomainPackageJsonCodec.writeStringList(binding.declaredPermissions()),
                binding.instanceMigrationPolicy(),
                binding.migrationValidationRuleKey(),
                binding.revision(),
                binding.createdAt(),
                binding.updatedAt());
    }
}

package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.behavior.WorkflowBindingTrigger;

import java.util.UUID;

/**
 * 领域包工作流绑定分页查询过滤条件。
 *
 * <p>对应 OpenAPI {@code GET /domain-packages/{packageId}/workflow-bindings} 的可选查询参数。
 *
 * @param packageId 所属领域包 ID（必填）
 * @param versionId 版本过滤；null 表示包级绑定
 * @param trigger 触发器过滤
 * @param processDefinitionKey 平台流程定义键过滤
 */
public record WorkflowBindingQueryFilter(
        UUID packageId,
        UUID versionId,
        WorkflowBindingTrigger trigger,
        String processDefinitionKey) {

    public static WorkflowBindingQueryFilter byPackage(UUID packageId) {
        return new WorkflowBindingQueryFilter(packageId, null, null, null);
    }
}

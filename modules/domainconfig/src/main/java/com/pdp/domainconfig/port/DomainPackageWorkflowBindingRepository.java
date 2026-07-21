package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.behavior.DomainPackageWorkflowBinding;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包工作流绑定仓储端口（FR-173 平台工作流复用）。
 *
 * <p>绑定由领域包设计者声明，引用平台注册的 BPMN 2.0.2 流程定义。{@code packageId + stableKey}
 * 唯一；{@code versionId} 为 null 表示包级绑定，对所有版本生效。
 */
public interface DomainPackageWorkflowBindingRepository {

    Optional<DomainPackageWorkflowBinding> findById(UUID id);

    /** 按领域包与稳定键查找。 */
    Optional<DomainPackageWorkflowBinding> findByPackageAndKey(UUID packageId, String stableKey);

    /** 按过滤条件分页查询。 */
    PageResult<DomainPackageWorkflowBinding> findByFilter(WorkflowBindingQueryFilter filter, PageRequest pageRequest);

    /** 查找包内所有有效绑定（versionId=null 与指定 versionId 的并集）。 */
    List<DomainPackageWorkflowBinding> findEffectiveByPackage(UUID packageId, UUID versionId);

    /** 按平台流程定义键与版本查找所有引用（用于流程升级影响分析）。 */
    PageResult<DomainPackageWorkflowBinding> findByProcessDefinition(String processDefinitionKey,
                                                                     String businessVersion,
                                                                     PageRequest pageRequest);

    /** 插入新工作流绑定；stableKey 唯一性由 uniq_workflow_binding_key 索引保证。 */
    void save(DomainPackageWorkflowBinding binding);

    /**
     * 更新绑定字段并递增 revision。
     *
     * <p>不允许修改 {@code processDefinitionKey} 与 {@code businessVersion}；
     * 流程定义升级必须通过新建绑定版本实现。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean update(UUID id, String bpmnResource, String authorizationPolicyKey,
                   java.util.Map<String, String> variableMappings,
                   String startupConditionRuleKey,
                   List<String> declaredPermissions,
                   com.pdp.domainconfig.domain.behavior.InstanceMigrationPolicy instanceMigrationPolicy,
                   String migrationValidationRuleKey,
                   int expectedRevision, Instant now);

    /**
     * 删除绑定（仅 DRAFT 状态的版本草稿可删除；PUBLISHED 状态需走退役流程）。
     *
     * @return {@code true} 成功；{@code false} 不存在
     */
    boolean delete(UUID id);
}

package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.behavior.DomainPackageWorkflowBinding;
import com.pdp.domainconfig.domain.behavior.InstanceMigrationPolicy;
import com.pdp.domainconfig.port.DomainPackageWorkflowBindingRepository;
import com.pdp.domainconfig.port.WorkflowBindingQueryFilter;
import com.pdp.persistence.domainconfig.mapper.DomainPackageWorkflowBindingMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包工作流绑定仓储适配器（MySQL 实现，FR-173 平台工作流复用）。
 *
 * <p>游标分页：游标为 {@link DomainPackageCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class DomainPackageWorkflowBindingRepositoryImpl implements DomainPackageWorkflowBindingRepository {

    private final DomainPackageWorkflowBindingMapper mapper;

    public DomainPackageWorkflowBindingRepositoryImpl(DomainPackageWorkflowBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DomainPackageWorkflowBinding> findById(UUID id) {
        WorkflowBindingRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(row.toBinding());
    }

    @Override
    public Optional<DomainPackageWorkflowBinding> findByPackageAndKey(UUID packageId, String stableKey) {
        WorkflowBindingRow row = mapper.selectByPackageAndKey(packageId, stableKey);
        return row == null ? Optional.empty() : Optional.of(row.toBinding());
    }

    @Override
    public PageResult<DomainPackageWorkflowBinding> findByFilter(WorkflowBindingQueryFilter filter,
                                                                  PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<WorkflowBindingRow> rows = mapper.selectByFilter(filter, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public List<DomainPackageWorkflowBinding> findEffectiveByPackage(UUID packageId, UUID versionId) {
        List<WorkflowBindingRow> rows = mapper.selectEffectiveByPackage(packageId, versionId);
        List<DomainPackageWorkflowBinding> result = new ArrayList<>(rows.size());
        for (WorkflowBindingRow row : rows) {
            result.add(row.toBinding());
        }
        return result;
    }

    @Override
    public PageResult<DomainPackageWorkflowBinding> findByProcessDefinition(String processDefinitionKey,
                                                                             String businessVersion,
                                                                             PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<WorkflowBindingRow> rows = mapper.selectByProcessDefinition(
                processDefinitionKey, businessVersion, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public void save(DomainPackageWorkflowBinding binding) {
        int rows = mapper.insert(WorkflowBindingRow.fromBinding(binding));
        if (rows != 1) {
            throw new IllegalStateException("工作流绑定插入失败: " + binding.id());
        }
    }

    @Override
    public boolean update(UUID id, String bpmnResource, String authorizationPolicyKey,
                          Map<String, String> variableMappings,
                          String startupConditionRuleKey,
                          List<String> declaredPermissions,
                          InstanceMigrationPolicy instanceMigrationPolicy,
                          String migrationValidationRuleKey,
                          int expectedRevision, Instant now) {
        String variableMappingsJson = DomainPackageJsonCodec.writeStringMap(variableMappings);
        String declaredPermissionsJson = DomainPackageJsonCodec.writeStringList(declaredPermissions);
        return mapper.update(id, bpmnResource, authorizationPolicyKey,
                variableMappingsJson, startupConditionRuleKey, declaredPermissionsJson,
                instanceMigrationPolicy, migrationValidationRuleKey,
                expectedRevision, now) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return mapper.delete(id) == 1;
    }

    private PageResult<DomainPackageWorkflowBinding> toPage(List<WorkflowBindingRow> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<WorkflowBindingRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<DomainPackageWorkflowBinding> page = new ArrayList<>(pageRows.size());
        for (WorkflowBindingRow row : pageRows) {
            page.add(row.toBinding());
        }
        String nextCursor = hasMore
                ? DomainPackageCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }
}

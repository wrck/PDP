package com.pdp.persistence.domainconfig.mapper;

import com.pdp.domainconfig.domain.behavior.InstanceMigrationPolicy;
import com.pdp.persistence.domainconfig.adapter.WorkflowBindingRow;
import com.pdp.domainconfig.port.WorkflowBindingQueryFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 领域包工作流绑定 MyBatis Mapper（FR-173 平台工作流复用）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/DomainPackageWorkflowBindingMapper.xml} 中声明。
 */
@Mapper
public interface DomainPackageWorkflowBindingMapper {

    WorkflowBindingRow selectById(@Param("id") UUID id);

    WorkflowBindingRow selectByPackageAndKey(@Param("packageId") UUID packageId,
                                              @Param("stableKey") String stableKey);

    List<WorkflowBindingRow> selectByFilter(@Param("filter") WorkflowBindingQueryFilter filter,
                                              @Param("lastId") UUID lastId,
                                              @Param("size") int size);

    /**
     * 查找包内有效绑定：versionId=null 的包级绑定 + 指定版本绑定。
     * 用于运行时绑定查询，不参与游标分页（一个包内绑定数量有限）。
     */
    List<WorkflowBindingRow> selectEffectiveByPackage(@Param("packageId") UUID packageId,
                                                       @Param("versionId") UUID versionId);

    List<WorkflowBindingRow> selectByProcessDefinition(@Param("processDefinitionKey") String processDefinitionKey,
                                                        @Param("businessVersion") String businessVersion,
                                                        @Param("lastId") UUID lastId,
                                                        @Param("size") int size);

    int insert(WorkflowBindingRow row);

    int update(@Param("id") UUID id,
               @Param("bpmnResource") String bpmnResource,
               @Param("authorizationPolicyKey") String authorizationPolicyKey,
               @Param("variableMappingsJson") String variableMappingsJson,
               @Param("startupConditionRuleKey") String startupConditionRuleKey,
               @Param("declaredPermissionsJson") String declaredPermissionsJson,
               @Param("instanceMigrationPolicy") InstanceMigrationPolicy instanceMigrationPolicy,
               @Param("migrationValidationRuleKey") String migrationValidationRuleKey,
               @Param("expectedRevision") int expectedRevision,
               @Param("now") Instant now);

    int delete(@Param("id") UUID id);
}

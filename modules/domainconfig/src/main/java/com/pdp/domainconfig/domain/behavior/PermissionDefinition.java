package com.pdp.domainconfig.domain.behavior;

import java.util.List;
import java.util.Map;

/**
 * 权限定义（domain-package.schema.json permissionDefinition）。
 *
 * <p>FR-010 领域包不得覆盖核心对象身份、工作空间归属、基础权限、审计、版本和平台保留动作。
 * 本定义仅声明领域包新增对象或扩展操作的权限，由 {@code DomainPackageValidationService}
 * （T121）发布前校验权限越界（SC-013）。
 *
 * <p>{@link #operations} 与 {@link #fieldKeys} 为受治理的操作白名单；
 * {@link #dataScope} 描述数据范围约束，由运行时权限引擎解释执行。
 */
public record PermissionDefinition(
        String capabilityKey,
        String objectKey,
        List<String> operations,
        List<String> fieldKeys,
        Map<String, Object> dataScope) {

    public PermissionDefinition {
        if (capabilityKey == null || capabilityKey.isBlank()) {
            throw new IllegalArgumentException("capabilityKey 不能为空");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("operations 不能为空");
        }
        operations = List.copyOf(operations);
        fieldKeys = fieldKeys == null ? List.of() : List.copyOf(fieldKeys);
        dataScope = dataScope == null ? Map.of() : Map.copyOf(dataScope);
    }
}

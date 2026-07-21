package com.pdp.domainconfig.domain.metamodel;

/**
 * 关系定义（domain-package.schema.json relationDefinition）。
 */
public record RelationDefinition(
        String stableKey,
        String targetObjectKey,
        Cardinality cardinality,
        boolean required,
        RelationOwnership ownership) {

    public RelationDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (targetObjectKey == null || targetObjectKey.isBlank()) {
            throw new IllegalArgumentException("targetObjectKey 不能为空");
        }
        if (cardinality == null) {
            throw new IllegalArgumentException("cardinality 不能为 null");
        }
        if (ownership == null) {
            ownership = RelationOwnership.REFERENCE;
        }
    }
}

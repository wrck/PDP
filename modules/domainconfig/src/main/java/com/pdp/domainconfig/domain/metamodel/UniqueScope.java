package com.pdp.domainconfig.domain.metamodel;

/**
 * 字段唯一性范围（domain-package.schema.json fieldDefinition.uniqueScope）。
 */
public enum UniqueScope {
    NONE,
    WORKSPACE,
    PROJECT,
    OBJECT_TYPE
}

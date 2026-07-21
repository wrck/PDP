package com.pdp.domainconfig.domain.metamodel;

/**
 * 关系基数（domain-package.schema.json relationDefinition.cardinality）。
 */
public enum Cardinality {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
}

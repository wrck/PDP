package com.pdp.domainconfig.domain.metamodel;

/**
 * 关系拥有类型（domain-package.schema.json relationDefinition.ownership）。
 *
 * <ul>
 *   <li>{@link #REFERENCE}：引用关系，目标对象独立生命周期；</li>
 *   <li>{@link #AGGREGATE_CHILD}：聚合子对象，目标对象生命周期由源对象管理。</li>
 * </ul>
 */
public enum RelationOwnership {
    REFERENCE,
    AGGREGATE_CHILD
}

package com.pdp.domainconfig.domain.metamodel;

/**
 * 字段索引模式（domain-package.schema.json fieldDefinition.indexMode）。
 *
 * <p>控制平台为该字段创建的索引与投影类型；FILTER 用于等值过滤、SORT 用于排序、
 * SEARCH 用于全文搜索词项、AGGREGATE 用于统计聚合。
 */
public enum IndexMode {
    NONE,
    FILTER,
    SORT,
    SEARCH,
    AGGREGATE
}

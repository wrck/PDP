package com.pdp.domainconfig.domain.packageversion;

/**
 * 领域包状态（spec.md §197）。
 *
 * <p>状态机：
 * <ul>
 *   <li>{@link #DRAFT} → {@link #ACTIVE}（首个版本发布后激活）</li>
 *   <li>{@link #ACTIVE} → {@link #DEPRECATED}（所有版本弃用后包弃用）</li>
 *   <li>{@link #DEPRECATED} → {@link #RETIRED}（所有版本退役后包退役）</li>
 * </ul>
 */
public enum DomainPackageStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    RETIRED
}

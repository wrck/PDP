package com.pdp.domainconfig.domain.packageversion;

/**
 * 领域包层级（FR-013 三层继承）。
 *
 * <ul>
 *   <li>{@link #PLATFORM_STANDARD}：平台标准包，最顶层；</li>
 *   <li>{@link #INDUSTRY}：行业领域包，继承平台标准包；</li>
 *   <li>{@link #WORKSPACE_CUSTOMER}：工作空间客户包，继承行业领域包。</li>
 * </ul>
 */
public enum DomainPackageLayer {
    PLATFORM_STANDARD,
    INDUSTRY,
    WORKSPACE_CUSTOMER
}

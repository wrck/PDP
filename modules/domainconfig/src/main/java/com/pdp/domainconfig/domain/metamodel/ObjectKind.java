package com.pdp.domainconfig.domain.metamodel;

/**
 * 领域包对象定义类型（domain-package.schema.json objectDefinition.kind）。
 *
 * <ul>
 *   <li>{@link #CORE_EXTENSION}：扩展平台允许扩展的核心对象，必须指定 {@code coreObjectType}；</li>
 *   <li>{@link #NEW_OBJECT}：创建全新业务对象。</li>
 * </ul>
 *
 * <p>FR-008 领域包 MUST 能扩展平台允许扩展的核心对象并创建全新业务对象。
 * FR-010 领域包不得覆盖核心对象身份、工作空间归属、基础权限、审计、版本和平台保留动作。
 */
public enum ObjectKind {
    CORE_EXTENSION,
    NEW_OBJECT
}

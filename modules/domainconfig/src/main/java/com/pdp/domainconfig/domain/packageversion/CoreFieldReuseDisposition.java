package com.pdp.domainconfig.domain.packageversion;

/**
 * 核心字段复用声明处置方式（FR-134、domain-package.schema.json coreFieldReuseDeclaration.disposition）。
 */
public enum CoreFieldReuseDisposition {
    /** 直接复用核心字段，不创建新字段。 */
    REUSE,
    /** 创建语义差异字段；必须声明理由，发布前由校验服务评估语义冲突。 */
    DIFFERENTIATE,
    /** 在核心字段基础上增加受控扩展（如额外校验规则、默认值）。 */
    AUGMENT
}

package com.pdp.domainconfig.domain.packageversion;

/**
 * 校验项分类（覆盖 SC-013 全部发布前识别维度）。
 */
public enum ValidationItemCategory {
    /** 结构校验：Schema、必填字段、JSON 格式。 */
    STRUCTURE,
    /** 引用校验：stableKey 引用是否可达、是否存在不可达状态。 */
    REFERENCE,
    /** 状态机校验：初始/终态、循环迁移、可达性。 */
    STATE_MACHINE,
    /** 规则校验：循环规则（SC-013）。 */
    RULE,
    /** 权限校验：越界、覆盖平台保留动作。 */
    PERMISSION,
    /** 迁移校验：前置条件、可逆性、回滚窗口。 */
    MIGRATION,
    /** 核心字段校验：复用声明、冲突检测（SC-025）。 */
    CORE_FIELD,
    /** 兼容性校验：版本契约、消费者影响（FR-172）。 */
    COMPATIBILITY
}

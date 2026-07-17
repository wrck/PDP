package com.pdp.experience.search;

import java.util.Objects;

/**
 * 可搜索对象类型枚举（稳定键）。
 *
 * <p>对应数据模型 {@code object_type_key} 列，使用稳定字符串键持久化，禁止依赖枚举序号。
 * 领域包扩展的可搜索对象 MUST 通过 {@link #fromStableKey(String)} 反查；未识别键抛出
 * {@link IllegalArgumentException} 而非静默忽略，确保跨认证数据库结果集合一致（SC-033）。
 *
 * <p>P1 覆盖核心业务对象类型；领域包扩展类型由领域包注册，不进入平台核心枚举。
 */
public enum SearchObjectType {

    /** 工作空间（用于跨空间授权检索，受限使用）。 */
    WORKSPACE("WORKSPACE"),
    /** 项目。 */
    PROJECT("PROJECT"),
    /** 项目阶段。 */
    PROJECT_PHASE("PROJECT_PHASE"),
    /** 任务 / 工作项。 */
    TASK("TASK"),
    /** 里程碑。 */
    MILESTONE("MILESTONE"),
    /** 检查项。 */
    CHECKLIST_ITEM("CHECKLIST_ITEM"),
    /** 交付件。 */
    DELIVERABLE("DELIVERABLE"),
    /** 审批单。 */
    APPROVAL("APPROVAL"),
    /** 基线。 */
    BASELINE("BASELINE"),
    /** 领域包 / 模板。 */
    DOMAIN_PACKAGE("DOMAIN_PACKAGE"),
    /** 项目模板。 */
    PROJECT_TEMPLATE("PROJECT_TEMPLATE"),
    /** 站内通知。 */
    NOTIFICATION("NOTIFICATION"),
    /** 审计摘要（仅限管理员检索）。 */
    AUDIT_SUMMARY("AUDIT_SUMMARY");

    private final String stableKey;

    SearchObjectType(String stableKey) {
        this.stableKey = stableKey;
    }

    public String stableKey() {
        return stableKey;
    }

    /**
     * 按稳定键反查枚举。
     *
     * @param stableKey 稳定键
     * @return 枚举值
     * @throws IllegalArgumentException 键不存在
     */
    public static SearchObjectType fromStableKey(String stableKey) {
        Objects.requireNonNull(stableKey, "stableKey 不能为 null");
        for (SearchObjectType t : values()) {
            if (t.stableKey.equals(stableKey)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知搜索对象类型稳定键: " + stableKey);
    }
}

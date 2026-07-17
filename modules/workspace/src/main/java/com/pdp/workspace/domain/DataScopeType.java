package com.pdp.workspace.domain;

/**
 * 数据范围类型（FR-063）。
 *
 * <p>限定角色可见数据边界。权限模型 {@code <domain>.<resource>.<action>} 与数据范围正交：
 * 功能权限决定能否操作，数据范围决定操作哪些数据行。
 */
public enum DataScopeType {
    /** 全工作空间数据。 */
    WORKSPACE,
    /** 按组织树限定。 */
    ORGANIZATION,
    /** 按区域限定。 */
    REGION,
    /** 按客户限定。 */
    CUSTOMER,
    /** 按项目归属限定（项目负责人/成员）。 */
    PROJECT_OWNERSHIP,
    /** 按参与身份限定（任务指派/关注/审批节点）。 */
    PARTICIPATION,
    /** 按对象属性动态限定。 */
    OBJECT_ATTRIBUTE
}

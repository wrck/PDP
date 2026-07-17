package com.pdp.shared.operation;

/**
 * 影响严重度（FR-168）。
 */
public enum ImpactSeverity {

    /** 信息：无副作用或影响可忽略。 */
    INFO,

    /** 警告：有副作用但可逆，操作者需知情。 */
    WARNING,

    /**
     * 不可逆：操作执行后无法恢复原状，操作者 MUST 显式确认。
     *
     * <p>到达不可逆点后，操作只能继续执行或触发补偿路径（前向修复或反向同步），
     * 不能简单回退（FR-149：开始产生新业务写入后，除非已验证反向同步，否则不得直接恢复旧系统）。
     */
    IRREVERSIBLE;

    public boolean isAtLeast(ImpactSeverity other) {
        return this.ordinal() >= other.ordinal();
    }
}

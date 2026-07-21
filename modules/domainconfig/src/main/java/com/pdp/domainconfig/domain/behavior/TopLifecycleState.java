package com.pdp.domainconfig.domain.behavior;

/**
 * 平台统一顶层生命周期状态（FR-118）。
 *
 * <p>领域包可以增加、拆分或命名领域子阶段，但每个可运行子阶段 MUST 映射到且仅映射到
 * 一个统一顶层生命周期状态。{@link #CLOSED} 与 {@link #CANCELLED} 为终态。
 */
public enum TopLifecycleState {
    PRE_PLANNING,
    PLANNING,
    EXECUTING,
    ACCEPTING,
    SERVICING,
    CLOSED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}

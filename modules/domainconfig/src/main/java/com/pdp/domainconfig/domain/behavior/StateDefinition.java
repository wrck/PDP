package com.pdp.domainconfig.domain.behavior;

import com.pdp.domainconfig.domain.metamodel.LocalizedText;

/**
 * 状态定义（domain-package.schema.json stateDefinition）。
 *
 * <p>FR-118 平台统一顶层生命周期：每个领域包子阶段 MUST 通过 {@link #topLifecycleState}
 * 映射到且仅映射到一个 {@link TopLifecycleState}，由 {@code DomainPackageValidationService}
 * （T121）发布前校验唯一性与可达性（SC-022）。
 *
 * <p>{@link #initial} 与 {@link #terminal} 用于状态机前置条件校验：
 * <ul>
 *   <li>每个对象定义 MUST 且只能声明一个 {@code initial=true} 的状态；</li>
 *   <li>{@code terminal=true} 的状态不允许作为 {@link TransitionDefinition#from} 出现。</li>
 * </ul>
 */
public record StateDefinition(
        String stableKey,
        LocalizedText label,
        TopLifecycleState topLifecycleState,
        boolean initial,
        boolean terminal) {

    public StateDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (label == null) {
            throw new IllegalArgumentException("label 不能为 null");
        }
        if (topLifecycleState == null) {
            throw new IllegalArgumentException("topLifecycleState 不能为 null");
        }
        if (initial && terminal) {
            throw new IllegalArgumentException("状态不能同时为 initial 和 terminal");
        }
    }

    /** 校验状态是否可作为迁移起点（非终态）。 */
    public boolean canTransitionFrom() {
        return !terminal;
    }
}

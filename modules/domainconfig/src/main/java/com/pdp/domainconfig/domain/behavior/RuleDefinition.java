package com.pdp.domainconfig.domain.behavior;

import java.util.List;
import java.util.Map;

/**
 * 规则定义（domain-package.schema.json ruleDefinition）。
 *
 * <p>FR-167 确定性状态机：每个规则 MUST 声明 {@link #event}、{@link #actions} 与
 * {@link #executionIdentity}；SC-013 发布前 MUST 识别循环规则。
 *
 * <p>{@link #cycleDetectionKey} 是循环检测分组键，由 {@code DomainPackageValidationService}
 * （T121）在发布前对相同 {@code cycleDetectionKey} 的规则集合做静态可达性分析，
 * 识别 {@code A→B→A} 类循环并阻断发布。
 */
public record RuleDefinition(
        String stableKey,
        String event,
        Map<String, Object> condition,
        List<RuleAction> actions,
        ExecutionIdentity executionIdentity,
        RuleMode mode,
        String cycleDetectionKey) {

    public RuleDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("event 不能为空");
        }
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("actions 不能为空");
        }
        if (executionIdentity == null) {
            throw new IllegalArgumentException("executionIdentity 不能为 null");
        }
        if (mode == null) {
            mode = RuleMode.ASYNCHRONOUS;
        }
        actions = List.copyOf(actions);
        condition = condition == null ? Map.of() : Map.copyOf(condition);
    }
}

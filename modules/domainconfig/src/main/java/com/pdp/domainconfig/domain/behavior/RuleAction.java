package com.pdp.domainconfig.domain.behavior;

import java.util.Map;

/**
 * 规则动作（domain-package.schema.json ruleDefinition.actions[]）。
 *
 * <p>{@link #parameters} 内容由 {@link RuleActionType} 决定，例如：
 * <ul>
 *   <li>{@link RuleActionType#SET_FIELD}：{@code {fieldKey, value}}</li>
 *   <li>{@link RuleActionType#TRANSITION}：{@code {transitionKey}}</li>
 *   <li>{@link RuleActionType#CALL_EXTENSION}：{@code {extensionKey, payload}}</li>
 * </ul>
 */
public record RuleAction(
        RuleActionType type,
        Map<String, Object> parameters) {

    public RuleAction {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}

package com.pdp.workflow.model;

import java.util.Objects;

/**
 * BPMN 校验发现项值对象（FR-174）。
 *
 * <p>对应 OpenAPI 契约 {@code findings} 数组元素，描述 BPMN 2.0.2 校验问题。
 * {@link Severity#ERROR} 阻止部署；{@link Severity#WARNING} 允许部署但需人工确认。
 */
public record ValidationFinding(Severity severity, String code, String message) {

    public enum Severity {
        ERROR,
        WARNING;

        public String stableKey() {
            return name();
        }
    }

    public ValidationFinding {
        Objects.requireNonNull(severity, "severity 不能为 null");
        Objects.requireNonNull(code, "code 不能为 null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空白");
        }
        Objects.requireNonNull(message, "message 不能为 null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空白");
        }
    }

    public static ValidationFinding error(String code, String message) {
        return new ValidationFinding(Severity.ERROR, code, message);
    }

    public static ValidationFinding warning(String code, String message) {
        return new ValidationFinding(Severity.WARNING, code, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isBlocking() {
        return severity == Severity.ERROR;
    }
}

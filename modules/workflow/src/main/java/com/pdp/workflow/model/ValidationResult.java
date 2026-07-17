package com.pdp.workflow.model;

import java.util.List;
import java.util.Objects;

/**
 * BPMN 校验结果值对象（对应 OpenAPI 校验响应）。
 *
 * @param valid       是否通过校验（无 ERROR 级别发现项）
 * @param contentHash BPMN 内容哈希
 * @param findings    校验发现项列表
 */
public record ValidationResult(
        boolean valid,
        String contentHash,
        List<ValidationFinding> findings) {

    public ValidationResult {
        Objects.requireNonNull(contentHash, "contentHash 不能为 null");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空白");
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static ValidationResult of(String contentHash, List<ValidationFinding> findings) {
        List<ValidationFinding> safe = findings == null ? List.of() : findings;
        boolean valid = safe.stream().noneMatch(ValidationFinding::isBlocking);
        return new ValidationResult(valid, contentHash, safe);
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public boolean hasWarnings() {
        return findings.stream().anyMatch(f -> f.severity() == ValidationFinding.Severity.WARNING);
    }
}

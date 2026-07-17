package com.pdp.workflow.model;

import java.util.Objects;

/**
 * 流程定义稳定键值对象（FR-174、ADR-0005）。
 *
 * <p>对应 BPMN {@code process id}，命名规则：小写字母开头，仅含小写字母、数字、点或连字符，
 * 长度 3-100。键跨版本稳定，与 {@link ProcessVersion} 共同唯一标识流程定义。
 *
 * <p>键 MUST NOT 包含 Flowable 表名或引擎内部命名空间。命名建议使用业务语义
 * （如 {@code approval.standard-review}、{@code project.gate-transition}）。
 */
public record ProcessDefinitionKey(String value) {

    /** 键命名规则正则。 */
    public static final String PATTERN = "^[a-z][a-z0-9.-]{2,99}$";

    public ProcessDefinitionKey {
        Objects.requireNonNull(value, "ProcessDefinitionKey 不能为 null");
        if (!value.matches(PATTERN)) {
            throw new IllegalArgumentException(
                    "ProcessDefinitionKey 必须匹配 " + PATTERN + "，实际为 " + value);
        }
    }

    public static ProcessDefinitionKey of(String value) {
        return new ProcessDefinitionKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

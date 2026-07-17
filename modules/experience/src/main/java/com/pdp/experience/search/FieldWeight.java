package com.pdp.experience.search;

import java.util.Objects;

/**
 * 字段权重值对象。
 *
 * <p>对应数据模型 {@code field_weights} 中的单条权重配置。同一分析器版本下，字段权重 MUST 稳定，
 * 用于在 {@link SearchTermProjection} 中计算字段贡献度，并参与相关度评分（仅作为候选排序参考，
 * 不直接进入 API 稳定排序——稳定排序以业务时间和 UUIDv7 兜底，SC-033）。
 *
 * @param fieldKey 字段稳定键（如 {@code title}、{@code description}、{@code deliverable.name}）
 * @param weight   权重，取值范围 [0.0, 100.0]，权重为 0 表示不参与词项投影
 */
public record FieldWeight(String fieldKey, double weight) {

    public FieldWeight {
        Objects.requireNonNull(fieldKey, "fieldKey 不能为 null");
        if (fieldKey.isBlank()) {
            throw new IllegalArgumentException("fieldKey 不能为空白");
        }
        if (weight < 0.0 || weight > 100.0) {
            throw new IllegalArgumentException("weight 必须在 [0.0, 100.0] 之间: " + weight);
        }
    }

    public static FieldWeight of(String fieldKey, double weight) {
        return new FieldWeight(fieldKey, weight);
    }

    /** 该字段是否参与词项投影（权重 > 0）。 */
    public boolean contributes() {
        return weight > 0.0;
    }
}

package com.pdp.experience.search;

import java.util.Objects;

/**
 * 搜索词项投影（数据模型 5.5 SearchTermProjection）。
 *
 * <p>对应 spec.md 第 8 节"数据库无关搜索语义"。分析器输出由平台契约决定，<strong>不得</strong>
 * 依赖 MySQL FULLTEXT 分词；同一分析器版本和数据集 MUST 产生相同词项、匹配集合和稳定业务排序（SC-033）。
 * 相关度相同按业务时间和 UUIDv7 排序（由调用方在结果集合层应用，本投影仅提供词项与权重）。
 *
 * <p>本投影用于：
 * <ul>
 *   <li>加速候选检索：通过 {@code (workspace_id, term, field_key)} 索引快速命中候选对象；</li>
 *   <li>相关度计算：{@code termFrequency * fieldWeight} 作为候选相关度参考；</li>
 *   <li>跨数据库一致性校验：同一分析器版本下词项集合必须一致，保证切换数据库后结果集合不变。</li>
 * </ul>
 *
 * @param objectRef       业务对象引用
 * @param term            规范化词项
 * @param fieldKey        字段稳定键（词项来源字段）
 * @param termFrequency   词项在该字段的出现频次（>= 1）
 * @param fieldWeight     字段权重快照（索引时权重，避免后续权重变化影响历史投影一致性）
 * @param analyzerVersion 分析器版本
 */
public record SearchTermProjection(
        ObjectRef objectRef,
        String term,
        String fieldKey,
        int termFrequency,
        double fieldWeight,
        AnalyzerVersion analyzerVersion) {

    public SearchTermProjection {
        Objects.requireNonNull(objectRef, "objectRef 不能为 null");
        Objects.requireNonNull(term, "term 不能为 null");
        if (term.isBlank()) {
            throw new IllegalArgumentException("term 不能为空白");
        }
        Objects.requireNonNull(fieldKey, "fieldKey 不能为 null");
        if (fieldKey.isBlank()) {
            throw new IllegalArgumentException("fieldKey 不能为空白");
        }
        if (termFrequency < 1) {
            throw new IllegalArgumentException("termFrequency 必须 >= 1: " + termFrequency);
        }
        if (fieldWeight < 0.0) {
            throw new IllegalArgumentException("fieldWeight 不能为负: " + fieldWeight);
        }
        Objects.requireNonNull(analyzerVersion, "analyzerVersion 不能为 null");
    }

    public static SearchTermProjection of(
            ObjectRef objectRef,
            String term,
            String fieldKey,
            int termFrequency,
            double fieldWeight,
            AnalyzerVersion analyzerVersion) {
        return new SearchTermProjection(objectRef, term, fieldKey, termFrequency, fieldWeight, analyzerVersion);
    }

    /** 候选相关度参考分（词频 × 字段权重），仅用于候选排序，不进入 API 稳定排序。 */
    public double relevanceScore() {
        return termFrequency * fieldWeight;
    }
}

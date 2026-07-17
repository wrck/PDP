package com.pdp.experience.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分词结果值对象。
 *
 * <p>对应统一分析器对单字段的输出：规范化文本、词项列表（去停用词后）和词项频次表。
 * 词项列表保留首次出现顺序（用于高亮和审计），频次表用于构造 {@link SearchTermProjection}。
 *
 * @param normalizedText 规范化文本
 * @param terms          词项列表（去停用词，保留顺序，不含重复）
 * @param termFrequencies 词项频次（词项 → 出现次数）
 */
public record TokenizationResult(
        NormalizedText normalizedText,
        List<String> terms,
        Map<String, Integer> termFrequencies) {

    public TokenizationResult {
        Objects.requireNonNull(normalizedText, "normalizedText 不能为 null");
        Objects.requireNonNull(terms, "terms 不能为 null");
        Objects.requireNonNull(termFrequencies, "termFrequencies 不能为 null");
        terms = List.copyOf(terms);
        termFrequencies = Collections.unmodifiableMap(new LinkedHashMap<>(termFrequencies));
    }

    public static TokenizationResult of(
            NormalizedText normalizedText,
            List<String> terms,
            Map<String, Integer> termFrequencies) {
        return new TokenizationResult(normalizedText, terms, termFrequencies);
    }

    /** 词项数量（去重后）。 */
    public int termCount() {
        return terms.size();
    }

    /** 是否为空（无词项）。 */
    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /** 获取指定词项的频次（不存在返回 0）。 */
    public int frequencyOf(String term) {
        return termFrequencies.getOrDefault(Objects.requireNonNull(term), 0);
    }
}

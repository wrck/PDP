package com.pdp.experience.search;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 默认停用词词典实现。
 *
 * <p>包含中英文常见停用词，对应 P1 平台统一分析器初始版本（{@link AnalyzerVersion#P1_INITIAL}）。
 * 词典在构造时确定，运行期不可变，线程安全。
 *
 * <p>停用词集合使用 {@link TreeSet} 配合 {@link String#CASE_INSENSITIVE_ORDER} 实现，
 * 但由于分析器调用前文本已通过 {@link TextNormalization} 大小写折叠，词典实际比对均在小写形式下完成。
 */
public final class DefaultStopWordDictionary implements StopWordDictionary {

    private final Set<String> stopWords;

    /**
     * 默认构造：使用 P1 初始停用词集合。
     */
    public DefaultStopWordDictionary() {
        this(defaultP1StopWords());
    }

    /**
     * 自定义构造：用于扩展词典（如领域包注册专有停用词）。
     *
     * @param stopWords 停用词集合（不可变快照）
     */
    public DefaultStopWordDictionary(Set<String> stopWords) {
        Objects.requireNonNull(stopWords, "stopWords 不能为 null");
        TreeSet<String> normalized = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String w : stopWords) {
            if (w != null && !w.isBlank()) {
                normalized.add(w.toLowerCase(Locale.ROOT));
            }
        }
        this.stopWords = Collections.unmodifiableSet(normalized);
    }

    @Override
    public boolean isStopWord(String term) {
        Objects.requireNonNull(term, "term 不能为 null");
        if (term.isBlank()) {
            return true;
        }
        return stopWords.contains(term.toLowerCase(Locale.ROOT));
    }

    @Override
    public Set<String> stopWords() {
        return stopWords;
    }

    /**
     * P1 初始停用词集合。
     * <p>覆盖中英文常见虚词、代词、连词、介词和标点占位词。领域包扩展不得修改此集合，
     * 只能通过 {@link #DefaultStopWordDictionary(Set)} 构造新实例并提升 {@link AnalyzerVersion#minor()}。
     */
    private static Set<String> defaultP1StopWords() {
        return Set.of(
                // 英文常见停用词
                "a", "an", "the", "and", "or", "but", "if", "then", "else", "of", "at", "by",
                "for", "with", "about", "against", "between", "into", "through", "during",
                "before", "after", "above", "below", "to", "from", "up", "down", "in", "out",
                "on", "off", "over", "under", "again", "further", "is", "are", "was", "were",
                "be", "been", "being", "have", "has", "had", "do", "does", "did", "will",
                "would", "should", "could", "can", "may", "might", "must", "shall",
                "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they",
                "me", "him", "her", "us", "them", "my", "your", "his", "its", "our", "their",
                "as", "so", "than", "too", "very", "just", "also", "not", "no", "nor", "only",
                "own", "same", "such", "each", "every", "both", "few", "more", "most", "other",
                "some", "any", "all",
                // 中文常见停用词（单字虚词，避免过滤多字业务术语）
                "的", "了", "在", "是", "我", "你", "他", "她", "它", "们", "个", "为", "和",
                "与", "或", "及", "或", "把", "被", "让", "使", "给", "向", "从", "到", "于",
                "在", "上", "下", "中", "里", "外", "前", "后", "去", "来", "着", "过", "地",
                "这", "那", "些", "哪", "什么", "怎么", "如何", "为", "什", "么", "因", "所以",
                "但", "而", "则", "若", "如", "且", "并", "且");
    }
}

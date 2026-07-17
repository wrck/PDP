package com.pdp.experience.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 平台统一搜索分析器默认实现（spec.md 第 8 节、SC-033）。
 *
 * <p>P1 默认分析策略：
 * <ol>
 *   <li><strong>规范化</strong>：{@link TextNormalization}（NFC + 全角半角 + ROOT Locale 大小写折叠 + 空白压缩）；</li>
 *   <li><strong>分词</strong>：
 *     <ul>
 *       <li>英文/拉丁字符：按空白和非字母数字边界分词，保留 ASCII 字母数字和下划线；</li>
 *       <li>中文/CJK 字符：单字切分（unigram），适配 P1 通用场景；领域包可注册专有词典扩展（P2）；</li>
 *       <li>数字：连续数字作为单一词项；</li>
 *       <li>其他 Unicode 字母：按 Unicode 字母簇切分。</li>
 *     </ul>
 *   </li>
 *   <li><strong>去停用词</strong>：基于 {@link StopWordDictionary} 过滤；</li>
 *   <li><strong>词频统计</strong>：保留首次出现顺序，统计每个词项出现次数；</li>
 *   <li><strong>字段权重</strong>：在构造时确定，运行期不可变；权重为 0 的字段仍参与规范化文本构建，
 *       但不进入词项投影（由调用方按 {@link #weightOf(String)} 判断）。</li>
 * </ol>
 *
 * <p>本实现为无状态（除构造时确定的版本、词典和权重配置），线程安全。
 */
public final class DefaultSearchAnalyzer implements UnifiedSearchAnalyzer {

    private final AnalyzerVersion version;
    private final StopWordDictionary stopWords;
    private final List<FieldWeight> fieldWeights;
    private final Map<String, FieldWeight> fieldWeightByKey;

    /**
     * 构造默认分析器（P1 初始版本 + 默认停用词词典 + 默认字段权重）。
     */
    public DefaultSearchAnalyzer() {
        this(AnalyzerVersion.P1_INITIAL, new DefaultStopWordDictionary(), defaultP1FieldWeights());
    }

    /**
     * 构造自定义分析器。
     *
     * @param version       分析器版本
     * @param stopWords     停用词词典
     * @param fieldWeights  字段权重列表
     */
    public DefaultSearchAnalyzer(AnalyzerVersion version, StopWordDictionary stopWords, List<FieldWeight> fieldWeights) {
        this.version = Objects.requireNonNull(version, "version 不能为 null");
        this.stopWords = Objects.requireNonNull(stopWords, "stopWords 不能为 null");
        Objects.requireNonNull(fieldWeights, "fieldWeights 不能为 null");
        this.fieldWeights = List.copyOf(fieldWeights);
        Map<String, FieldWeight> byKey = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FieldWeight fw : this.fieldWeights) {
            byKey.put(fw.fieldKey(), fw);
        }
        this.fieldWeightByKey = Collections.unmodifiableMap(byKey);
    }

    @Override
    public AnalyzerVersion version() {
        return version;
    }

    @Override
    public List<FieldWeight> fieldWeights() {
        return fieldWeights;
    }

    @Override
    public double weightOf(String fieldKey) {
        Objects.requireNonNull(fieldKey, "fieldKey 不能为 null");
        FieldWeight fw = fieldWeightByKey.get(fieldKey);
        return fw == null ? 0.0 : fw.weight();
    }

    @Override
    public TokenizationResult analyze(String rawText) {
        NormalizedText normalized = TextNormalization.toNormalized(rawText);
        if (normalized.isEmpty()) {
            return TokenizationResult.of(normalized, List.of(), Map.of());
        }
        List<String> rawTokens = tokenize(normalized.text());
        // 去停用词 + 保留首次顺序 + 统计词频
        List<String> ordered = new ArrayList<>();
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String token : rawTokens) {
            if (token.isBlank() || stopWords.isStopWord(token)) {
                continue;
            }
            if (!frequencies.containsKey(token)) {
                ordered.add(token);
            }
            frequencies.merge(token, 1, Integer::sum);
        }
        return TokenizationResult.of(normalized, Collections.unmodifiableList(ordered), frequencies);
    }

    @Override
    public Map<String, TokenizationResult> analyzeFields(Map<String, String> fieldTexts) {
        Objects.requireNonNull(fieldTexts, "fieldTexts 不能为 null");
        Map<String, TokenizationResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fieldTexts.entrySet()) {
            results.put(e.getKey(), analyze(e.getValue()));
        }
        return Collections.unmodifiableMap(results);
    }

    @Override
    public NormalizedText composeNormalizedText(Map<String, TokenizationResult> fieldResults) {
        Objects.requireNonNull(fieldResults, "fieldResults 不能为 null");
        // 按字段权重降序拼接规范化文本；权重相同按字段稳定键字典序
        List<Map.Entry<String, TokenizationResult>> sorted = new ArrayList<>(fieldResults.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, TokenizationResult>>comparingDouble(
                        (e) -> -weightOf(e.getKey()))
                .thenComparing(Map.Entry::getKey));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, TokenizationResult> e : sorted) {
            String text = e.getValue().normalizedText().text();
            if (!text.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(text);
            }
        }
        return NormalizedText.of(sb.toString());
    }

    /**
     * 分词核心逻辑。
     *
     * <p>策略：
     * <ul>
     *   <li>连续 ASCII 字母/数字/下划线作为一个词项（小写化）；</li>
     *   <li>CJK 字符（U+4E00-U+9FFF、U+3400-U+4DBF 扩展 A、U+F900-U+FAFF 兼容）单字切分；</li>
     *   <li>其他 Unicode 字母按 Unicode 字母簇切分；</li>
     *   <li>其他字符作为分隔符。</li>
     * </ul>
     *
     * @param normalizedText 已规范化的文本
     * @return 词项列表（未去停用词）
     */
    private List<String> tokenize(String normalizedText) {
        List<String> tokens = new ArrayList<>();
        int len = normalizedText.length();
        int i = 0;
        StringBuilder latinBuffer = new StringBuilder();
        while (i < len) {
            int cp = normalizedText.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (isAsciiAlphanumericOrUnderscore(cp)) {
                latinBuffer.appendCodePoint(cp);
            } else {
                flushLatin(latinBuffer, tokens);
                if (isCjkCodePoint(cp)) {
                    // CJK 单字切分
                    tokens.add(new String(Character.toChars(cp)));
                } else if (Character.isLetterOrDigit(cp)) {
                    // 其他 Unicode 字母/数字作为单字
                    tokens.add(new String(Character.toChars(cp)));
                }
                // 其他字符（空格、标点）作为分隔符，丢弃
            }
            i += charCount;
        }
        flushLatin(latinBuffer, tokens);
        return tokens;
    }

    private static void flushLatin(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() > 0) {
            tokens.add(buffer.toString().toLowerCase(Locale.ROOT));
            buffer.setLength(0);
        }
    }

    private static boolean isAsciiAlphanumericOrUnderscore(int cp) {
        return (cp >= 'a' && cp <= 'z')
                || (cp >= 'A' && cp <= 'Z')
                || (cp >= '0' && cp <= '9')
                || cp == '_'
                || cp == '-';
    }

    private static boolean isCjkCodePoint(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)          // CJK 统一表意文字
                || (cp >= 0x3400 && cp <= 0x4DBF)       // CJK 扩展 A
                || (cp >= 0xF900 && cp <= 0xFAFF)       // CJK 兼容表意文字
                || (cp >= 0x3000 && cp <= 0x303F);      // CJK 标点（部分）
    }

    /**
     * P1 默认字段权重配置。
     * <p>标题权重最高，名称次之，描述最低；其他扩展字段默认不参与词项投影（权重 0），
     * 但仍参与规范化文本构建（由调用方决定是否传入）。
     */
    private static List<FieldWeight> defaultP1FieldWeights() {
        return List.of(
                FieldWeight.of("title", 10.0),
                FieldWeight.of("name", 8.0),
                FieldWeight.of("code", 6.0),
                FieldWeight.of("description", 3.0),
                FieldWeight.of("content", 2.0),
                FieldWeight.of("tags", 5.0));
    }
}

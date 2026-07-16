package com.pdp.experience.search;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** 平台统一搜索分析器；数据库全文检索只能使用其结果作候选加速。 */
public final class SearchAnalyzer {

    public Set<String> analyze(String input) {
        if (input == null || input.isBlank()) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank()) {
                if (token.codePoints().anyMatch(SearchAnalyzer::isCjk)) {
                    addCjkBigrams(token, terms);
                } else {
                    terms.add(token);
                }
            }
        }
        return Set.copyOf(terms);
    }

    private static void addCjkBigrams(String token, Set<String> target) {
        int[] points = token.codePoints().toArray();
        boolean containsCjk = token.codePoints().anyMatch(SearchAnalyzer::isCjk);
        if (!containsCjk) {
            return;
        }
        if (points.length == 1) {
            target.add(token);
            return;
        }
        for (int index = 0; index < points.length - 1; index++) {
            target.add(new String(points, index, 2));
        }
    }

    private static boolean isCjk(int point) {
        Character.UnicodeScript script = Character.UnicodeScript.of(point);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}

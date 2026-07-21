package com.pdp.domainconfig.domain.metamodel;

import java.util.Map;

/**
 * 本地化文本（domain-package.schema.json localizedText）。
 *
 * <p>键为 BCP 47 语言标签（如 {@code zh-CN}、{@code en-US}），值为对应语言的文案。
 * 至少包含一种语言。
 */
public record LocalizedText(Map<String, String> values) {

    public LocalizedText {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("LocalizedText 必须包含至少一种语言");
        }
        values.forEach((lang, text) -> {
            if (lang == null || lang.isBlank()) {
                throw new IllegalArgumentException("语言标签不能为空");
            }
            if (text == null) {
                throw new IllegalArgumentException("文案不能为 null（语言：" + lang + "）");
            }
            if (text.length() > 500) {
                throw new IllegalArgumentException("文案不能超过 500 字符（语言：" + lang + "）");
            }
        });
        values = Map.copyOf(values);
    }

    public static LocalizedText of(String lang, String text) {
        return new LocalizedText(Map.of(lang, text));
    }

    public String get(String lang) {
        return values.get(lang);
    }

    public String getOrDefault(String lang, String fallback) {
        return values.getOrDefault(lang, fallback);
    }
}

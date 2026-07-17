package com.pdp.experience.search;

import java.util.Objects;

/**
 * 规范化文本值对象。
 *
 * <p>对应数据模型 {@code normalized_text} 列。由平台统一分析器对原始文本应用
 * Unicode NFC 规范化、大小写折叠和停用词过滤后得到，用于 {@link SearchDocument} 持久化
 * 和候选检索（persistence-design.md 第 8 节）。
 *
 * <p>规范化 MUST 数据库无关：同一分析器版本和同一原始文本 MUST 产生相同规范化结果（SC-033），
 * 不依赖数据库原生排序规则或大小写语义。
 *
 * @param text 规范化后的文本，不可为 null
 */
public record NormalizedText(String text) {

    public NormalizedText {
        Objects.requireNonNull(text, "text 不能为 null");
    }

    public static NormalizedText of(String text) {
        return new NormalizedText(text);
    }

    /** 是否为空（仅停用词或无内容）。 */
    public boolean isEmpty() {
        return text.isBlank();
    }

    @Override
    public String toString() {
        return text;
    }
}

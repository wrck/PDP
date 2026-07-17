package com.pdp.experience.search;

import java.text.Normalizer;
import java.util.Objects;

/**
 * 文本规范化工具（数据库无关）。
 *
 * <p>实现 spec.md 第 8 节"数据库无关搜索语义"中的统一 Unicode 规范化和大小写折叠。
 * 同一分析器版本和同一原始文本 MUST 产生相同规范化结果（SC-033），不依赖数据库原生排序规则或
 * 大小写语义（plan.md：业务键在应用层规范化，避免依赖数据库默认排序规则和大小写语义）。
 *
 * <p>规范化步骤（顺序敏感）：
 * <ol>
 *   <li>{@link Normalizer.Form#NFC}：Unicode 规范化组合形式，消除等价字符差异；</li>
 *   <li>全角转半角：ASCII 范围全角字符转半角，避免中英混排输入差异；</li>
 *   <li>{@link String#toLowerCase(java.util.Locale)}：使用 ROOT Locale 大小写折叠，
 *       避免土耳其语 i 等地区差异；</li>
 *   <li>压缩连续空白为单个空格，去除首尾空白。</li>
 * </ol>
 *
 * <p>本类为纯函数工具，无状态，线程安全。
 */
public final class TextNormalization {

    private TextNormalization() {
    }

    /**
     * 规范化原始文本。
     *
     * @param raw 原始文本，可为 null（返回空字符串）
     * @return 规范化后的文本
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // 1. NFC 规范化
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC);
        // 2. 全角转半角（ASCII 范围）
        s = toHalfWidth(s);
        // 3. 大小写折叠（ROOT Locale，避免地区差异）
        s = s.toLowerCase(java.util.Locale.ROOT);
        // 4. 压缩连续空白为单个空格，去除首尾
        s = s.trim().replaceAll("\\s+", " ");
        return s;
    }

    /**
     * 规范化并构造 {@link NormalizedText} 值对象。
     *
     * @param raw 原始文本
     * @return 规范化文本值对象
     */
    public static NormalizedText toNormalized(String raw) {
        return NormalizedText.of(normalize(raw));
    }

    /**
     * 全角字符转半角（ASCII 范围：!-~，以及全角空格）。
     *
     * @param s 原始字符串
     * @return 半角化字符串
     */
    private static String toHalfWidth(String s) {
        Objects.requireNonNull(s, "s 不能为 null");
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\u3000') {
                // 全角空格
                chars[i] = ' ';
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角 !-~ 转半角
                chars[i] = (char) (c - '\uFF01' + '!');
            }
        }
        return new String(chars);
    }
}

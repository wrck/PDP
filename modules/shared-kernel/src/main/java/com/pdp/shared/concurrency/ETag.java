package com.pdp.shared.concurrency;

/**
 * ETag 强校验值对象。
 *
 * <p>对应 HTTP {@code If-Match} 头，绑定资源当前 revision。
 * HTTP 更新使用 {@code If-Match} 或请求体 revision，不匹配统一返回 409。
 *
 * <p>格式：{@code "revision:<value>"} 弱校验值，由平台签发，客户端不得自行构造。
 */
public record ETag(String value) {

    private static final String PREFIX = "revision:";

    public ETag {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ETag 不能为空");
        }
    }

    /** 由 revision 构造 ETag。 */
    public static ETag of(Revision revision) {
        return new ETag(PREFIX + revision.value());
    }

    /** 从 If-Match 头值解析 revision。 */
    public static Revision parseRevision(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new IllegalArgumentException("If-Match 不能为空");
        }
        String trimmed = ifMatch.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("If-Match 格式无效，期望 " + PREFIX + "<revision>");
        }
        try {
            return Revision.of(Integer.parseInt(trimmed.substring(PREFIX.length())));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match revision 不是有效整数", e);
        }
    }
}

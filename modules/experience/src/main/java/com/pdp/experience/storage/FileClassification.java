package com.pdp.experience.storage;

import java.util.Objects;

/**
 * 文件分类级别枚举（稳定键）。
 *
 * <p>对应 docs/security/data-classification.md 数据分类目录。控制文件存储加密强度、
 * 访问审计粒度、签名 URL 有效期和下载权限复核级别。
 *
 * <p>不同分类级别的安全控制（security-baseline.md）：
 * <ul>
 *   <li>{@link #PUBLIC}：可公开，无额外控制；</li>
 *   <li>{@link #INTERNAL}：内部可见，标准加密和审计；</li>
 *   <li>{@link #CONFIDENTIAL}：机密，签名 URL ≤ 5 分钟，下载需双重复核；</li>
 *   <li>{@link #RESTRICTED}：受限（含合同、客户、成本、签字数据），
 *       签名 URL ≤ 5 分钟，下载需法律保留检查，禁止外部参与者访问。</li>
 * </ul>
 */
public enum FileClassification {

    PUBLIC("PUBLIC"),
    INTERNAL("INTERNAL"),
    CONFIDENTIAL("CONFIDENTIAL"),
    RESTRICTED("RESTRICTED");

    private final String stableKey;

    FileClassification(String stableKey) {
        this.stableKey = stableKey;
    }

    public String stableKey() {
        return stableKey;
    }

    public static FileClassification fromStableKey(String stableKey) {
        Objects.requireNonNull(stableKey, "stableKey 不能为 null");
        for (FileClassification c : values()) {
            if (c.stableKey.equals(stableKey)) {
                return c;
            }
        }
        throw new IllegalArgumentException("未知文件分类级别稳定键: " + stableKey);
    }

    /** 是否需要加密存储（CONFIDENTIAL 及以上）。 */
    public boolean requiresEncryption() {
        return this == CONFIDENTIAL || this == RESTRICTED;
    }

    /** 是否需要下载权限双重复核（RESTRICTED）。 */
    public boolean requiresDualRevalidation() {
        return this == RESTRICTED;
    }

    /** 签名 URL 最大有效期（秒，FR-164：所有级别 ≤ 300 秒 = 5 分钟）。 */
    public long maxSignedUrlTtlSeconds() {
        return switch (this) {
            case PUBLIC -> 600;      // 公开文件放宽到 10 分钟
            case INTERNAL -> 300;    // 5 分钟
            case CONFIDENTIAL -> 180; // 3 分钟
            case RESTRICTED -> 120;   // 2 分钟
        };
    }
}

package com.pdp.experience.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 签名 URL 值对象（短时预签名下载/上传地址）。
 *
 * <p>对应 FR-164：附件签名地址有效期不得超过 5 分钟（300 秒）且实际下载前必须复核权限。
 * 由 {@link ObjectStoragePort#signDownloadUrl} 或 {@link ObjectStoragePort#signUploadUrl} 生成，
 * 返回给前端后由前端直接访问对象存储，不经过应用服务器（减轻带宽压力）。
 *
 * <p><strong>有效期约束</strong>（FR-164）：
 * <ul>
 *   <li>所有分类级别的签名 URL 有效期 MUST ≤ 5 分钟（300 秒）；</li>
 *   <li>{@link FileClassification#CONFIDENTIAL} 和 {@link FileClassification#RESTRICTED} 进一步缩短
 *       （见 {@link FileClassification#maxSignedUrlTtlSeconds()}）；</li>
 *   <li>实际下载前 MUST 复核权限（由 {@link FileStorageService#requestDownload} 实现）。</li>
 * </ul>
 *
 * @param url          预签名 URL
 * @param method       HTTP 方法（GET 下载 / PUT 上传）
 * @param objectRef    关联的对象存储引用
 * @param expiresAt    过期时间
 * @param allowedOrigin 允许的来源（可选，用于 CORS 控制）
 */
public record SignedUrl(
        String url,
        String method,
        StorageObjectRef objectRef,
        Instant expiresAt,
        String allowedOrigin) {

    /** FR-164 强制最大有效期：5 分钟。 */
    public static final Duration MAX_TTL = Duration.ofMinutes(5);

    public SignedUrl {
        Objects.requireNonNull(url, "url 不能为 null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url 不能为空白");
        }
        Objects.requireNonNull(method, "method 不能为 null");
        if (!"GET".equals(method) && !"PUT".equals(method)) {
            throw new IllegalArgumentException("method 必须为 GET 或 PUT: " + method);
        }
        Objects.requireNonNull(objectRef, "objectRef 不能为 null");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    }

    public static SignedUrl forDownload(String url, StorageObjectRef objectRef,
                                        Instant expiresAt, String allowedOrigin) {
        return new SignedUrl(url, "GET", objectRef, expiresAt, allowedOrigin);
    }

    public static SignedUrl forUpload(String url, StorageObjectRef objectRef,
                                      Instant expiresAt, String allowedOrigin) {
        return new SignedUrl(url, "PUT", objectRef, expiresAt, allowedOrigin);
    }

    /** 是否已过期。 */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 剩余有效期。 */
    public Duration remainingTtl(Instant now) {
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * 校验有效期是否符合 FR-164（≤ 5 分钟）。
     *
     * @param issuedAt 签发时间
     * @return true 表示合规
     */
    public boolean isTtlCompliant(Instant issuedAt) {
        Duration ttl = Duration.between(issuedAt, expiresAt);
        return !ttl.isNegative() && ttl.compareTo(MAX_TTL) <= 0;
    }
}

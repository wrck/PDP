package com.pdp.experience.storage;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

/**
 * 对象存储与文件处理异常。
 *
 * <p>对应 FR-164 和文件状态机相关失败：对象存储不可用、签名 URL 复核失败、
 * 病毒扫描失败、文件状态非法迁移等。所有失败 MUST 携带稳定原因分类和下一步建议，
 * 符合 spec.md "所有非法迁移、并发冲突和补偿操作必须具有稳定原因分类、下一步建议和关联证据"。
 *
 * <p>错误码映射：
 * <ul>
 *   <li>{@link Reason#OBJECT_UNAVAILABLE} / {@link Reason#SCAN_UNAVAILABLE}：
 *       {@link ErrorCode#SERVICE_UNAVAILABLE}（HTTP 503，调用方可重试）；</li>
 *   <li>{@link Reason#PERMISSION_REVALIDATION_FAILED}：
 *       {@link ErrorCode#FORBIDDEN}（HTTP 403，权限复核失败）；</li>
 *   <li>{@link Reason#FILE_NOT_AVAILABLE}：
 *       {@link ErrorCode#RESOURCE_NOT_FOUND}（HTTP 404，不泄露存在性）；</li>
 *   <li>{@link Reason#ILLEGAL_STATE_TRANSITION} / {@link Reason#SIGN_URL_TTL_VIOLATION} /
 *       {@link Reason#CONTENT_HASH_MISMATCH}：
 *       {@link ErrorCode#BUSINESS_RULE_VIOLATED}（HTTP 400/409，业务规则违反）；</li>
 *   <li>{@link Reason#QUARANTINE_REQUIRED}：
 *       {@link ErrorCode#BUSINESS_RULE_VIOLATED}（HTTP 422，文件被隔离禁止访问）。</li>
 * </ul>
 */
public class StorageException extends PdpException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final StorageObjectRef objectRef;

    /**
     * 异常原因分类（稳定键）。
     */
    public enum Reason {
        /** 对象存储临时不可用（网络、5xx、超时）。 */
        OBJECT_UNAVAILABLE("STORAGE.OBJECT_UNAVAILABLE"),
        /** 病毒扫描器临时不可用。 */
        SCAN_UNAVAILABLE("STORAGE.SCAN_UNAVAILABLE"),
        /** 下载前权限复核失败（FR-164）。 */
        PERMISSION_REVALIDATION_FAILED("STORAGE.PERMISSION_REVALIDATION_FAILED"),
        /** 文件状态非 AVAILABLE，禁止访问（不泄露存在性细节）。 */
        FILE_NOT_AVAILABLE("STORAGE.FILE_NOT_AVAILABLE"),
        /** 文件状态非法迁移（违反 FileScanStatus 状态机）。 */
        ILLEGAL_STATE_TRANSITION("STORAGE.ILLEGAL_STATE_TRANSITION"),
        /** 签名 URL 有效期违反 FR-164（> 5 分钟）。 */
        SIGN_URL_TTL_VIOLATION("STORAGE.SIGN_URL_TTL_VIOLATION"),
        /** 内容哈希不匹配（上传或下载完整性校验失败）。 */
        CONTENT_HASH_MISMATCH("STORAGE.CONTENT_HASH_MISMATCH"),
        /** 文件被隔离，必须隔离访问（FR-071 / 安全基线）。 */
        QUARANTINE_REQUIRED("STORAGE.QUARANTINE_REQUIRED");

        private final String stableKey;

        Reason(String stableKey) {
            this.stableKey = stableKey;
        }

        public String stableKey() {
            return stableKey;
        }
    }

    public StorageException(Reason reason, StorageObjectRef objectRef, String message) {
        super(errorCodeFor(reason), message);
        this.reason = reason;
        this.objectRef = objectRef;
    }

    public StorageException(Reason reason, StorageObjectRef objectRef, String message, Throwable cause) {
        super(errorCodeFor(reason), message, cause);
        this.reason = reason;
        this.objectRef = objectRef;
    }

    private static ErrorCode errorCodeFor(Reason reason) {
        return switch (reason) {
            case OBJECT_UNAVAILABLE, SCAN_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            case PERMISSION_REVALIDATION_FAILED -> ErrorCode.FORBIDDEN;
            case FILE_NOT_AVAILABLE -> ErrorCode.RESOURCE_NOT_FOUND;
            case ILLEGAL_STATE_TRANSITION, SIGN_URL_TTL_VIOLATION,
                 CONTENT_HASH_MISMATCH, QUARANTINE_REQUIRED -> ErrorCode.BUSINESS_RULE_VIOLATED;
        };
    }

    public Reason reason() {
        return reason;
    }

    public StorageObjectRef objectRef() {
        return objectRef;
    }

    @Override
    protected int httpStatus() {
        return switch (reason) {
            case OBJECT_UNAVAILABLE, SCAN_UNAVAILABLE -> 503;
            case PERMISSION_REVALIDATION_FAILED -> 403;
            case FILE_NOT_AVAILABLE -> 404;
            case ILLEGAL_STATE_TRANSITION -> 409;
            case SIGN_URL_TTL_VIOLATION, CONTENT_HASH_MISMATCH, QUARANTINE_REQUIRED -> 422;
        };
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        return "[" + reason.stableKey() + "] " + base
                + (objectRef != null ? " (objectRef=" + objectRef + ")" : "");
    }

    /** 对象存储临时不可用。 */
    public static StorageException objectUnavailable(StorageObjectRef ref, String message, Throwable cause) {
        return new StorageException(Reason.OBJECT_UNAVAILABLE, ref, message, cause);
    }

    /** 病毒扫描器临时不可用。 */
    public static StorageException scanUnavailable(StorageObjectRef ref, String message, Throwable cause) {
        return new StorageException(Reason.SCAN_UNAVAILABLE, ref, message, cause);
    }

    /** 下载前权限复核失败（FR-164）。 */
    public static StorageException permissionRevalidationFailed(StorageObjectRef ref, String message) {
        return new StorageException(Reason.PERMISSION_REVALIDATION_FAILED, ref, message);
    }

    /** 文件状态非 AVAILABLE，禁止访问。 */
    public static StorageException fileNotAvailable(StorageObjectRef ref, FileScanStatus current) {
        return new StorageException(Reason.FILE_NOT_AVAILABLE, ref,
                "文件当前状态禁止访问: " + (current != null ? current.stableKey() : "UNKNOWN"));
    }

    /** 文件状态非法迁移。 */
    public static StorageException illegalStateTransition(StorageObjectRef ref,
                                                          FileScanStatus from, FileScanStatus to) {
        StorageException ex = new StorageException(Reason.ILLEGAL_STATE_TRANSITION, ref,
                "非法状态迁移: " + (from != null ? from.stableKey() : "null")
                        + " → " + (to != null ? to.stableKey() : "null"));
        ex.reason("非法状态迁移违反 FileScanStatus 状态机");
        ex.nextStep("检查文件当前状态和目标状态，仅允许 FileScanStatus.canTransitionTo 允许的迁移");
        return ex;
    }

    /** 签名 URL 有效期违反 FR-164。 */
    public static StorageException signUrlTtlViolation(StorageObjectRef ref, long actualTtlSeconds) {
        StorageException ex = new StorageException(Reason.SIGN_URL_TTL_VIOLATION, ref,
                "签名 URL 有效期 " + actualTtlSeconds + "s 超过 FR-164 上限 300s");
        ex.reason("FR-164 限制签名 URL 有效期 ≤ 5 分钟（300 秒）");
        ex.nextStep("缩短签名 URL 有效期至分类允许的最大值（FileClassification.maxSignedUrlTtlSeconds）");
        return ex;
    }

    /** 内容哈希不匹配。 */
    public static StorageException contentHashMismatch(StorageObjectRef ref,
                                                       String expected, String actual) {
        StorageException ex = new StorageException(Reason.CONTENT_HASH_MISMATCH, ref,
                "内容哈希不匹配: expected=" + expected + ", actual=" + actual);
        ex.reason("上传或下载过程内容完整性校验失败");
        ex.nextStep("重新上传或下载文件；若持续失败检查对象存储一致性");
        return ex;
    }

    /** 文件被隔离，禁止访问。 */
    public static StorageException quarantineRequired(StorageObjectRef ref, String threatName) {
        StorageException ex = new StorageException(Reason.QUARANTINE_REQUIRED, ref,
                "文件被隔离，禁止访问" + (threatName != null ? "（威胁: " + threatName + "）" : ""));
        ex.reason("文件扫描发现威胁或异常，已隔离");
        ex.nextStep("联系安全管理员复核；误报可通过审批流程释放隔离");
        return ex;
    }
}

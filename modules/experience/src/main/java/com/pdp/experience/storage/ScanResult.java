package com.pdp.experience.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * 病毒扫描结果值对象。
 *
 * <p>由 {@link VirusScannerPort#scan} 返回。结果决定 {@link FileScanStatus} 状态迁移：
 * <ul>
 *   <li>{@link Verdict#CLEAN} → 文件从 SCANNING 迁移到 AVAILABLE；</li>
 *   <li>{@link Verdict#INFECTED} → 文件迁移到 QUARANTINED，禁止访问；</li>
 *   <li>{@link Verdict#SUSPICIOUS} → 文件迁移到 QUARANTINED，等待人工复核；</li>
 *   <li>{@link Verdict#SCAN_ERROR} → 文件保持 SCANNING，由重试机制处理（不迁移到 AVAILABLE）。</li>
 * </ul>
 *
 * @param verdict      扫描结论
 * @param scannerName  扫描器标识（如 {@code clamav-1.4}），用于审计和兼容性追踪
 * @param threatName   威胁名称（INFECTED/SUSPICIOUS 时非空，如 {@code Win.Test.EICAR_HDB-1}）
 * @param scannedAt    扫描完成时间
 * @param scannerMessage 扫描器原始消息（用于诊断，可能为空）
 */
public record ScanResult(
        Verdict verdict,
        String scannerName,
        String threatName,
        Instant scannedAt,
        String scannerMessage) {

    public ScanResult {
        Objects.requireNonNull(verdict, "verdict 不能为 null");
        Objects.requireNonNull(scannerName, "scannerName 不能为 null");
        if (scannerName.isBlank()) {
            throw new IllegalArgumentException("scannerName 不能为空白");
        }
        Objects.requireNonNull(scannedAt, "scannedAt 不能为 null");
        if ((verdict == Verdict.INFECTED || verdict == Verdict.SUSPICIOUS)
                && (threatName == null || threatName.isBlank())) {
            throw new IllegalArgumentException(
                    "verdict=" + verdict + " 时 threatName 不能为空白");
        }
    }

    /**
     * 扫描结论枚举（稳定键）。
     */
    public enum Verdict {
        /** 干净：未发现威胁，可迁移到 AVAILABLE。 */
        CLEAN("CLEAN"),
        /** 已感染：发现明确威胁，必须隔离。 */
        INFECTED("INFECTED"),
        /** 可疑：异常特征但未确认，隔离等待人工复核。 */
        SUSPICIOUS("SUSPICIOUS"),
        /** 扫描错误：扫描器临时不可用或返回错误，应重试，不迁移状态。 */
        SCAN_ERROR("SCAN_ERROR");

        private final String stableKey;

        Verdict(String stableKey) {
            this.stableKey = stableKey;
        }

        public String stableKey() {
            return stableKey;
        }

        public static Verdict fromStableKey(String stableKey) {
            Objects.requireNonNull(stableKey, "stableKey 不能为 null");
            for (Verdict v : values()) {
                if (v.stableKey.equals(stableKey)) {
                    return v;
                }
            }
            throw new IllegalArgumentException("未知扫描结论稳定键: " + stableKey);
        }

        /** 是否应隔离文件。 */
        public boolean shouldQuarantine() {
            return this == INFECTED || this == SUSPICIOUS;
        }

        /** 是否可重试（扫描错误）。 */
        public boolean isRetryable() {
            return this == SCAN_ERROR;
        }
    }

    public static ScanResult clean(String scannerName, Instant scannedAt, String message) {
        return new ScanResult(Verdict.CLEAN, scannerName, null, scannedAt, message);
    }

    public static ScanResult infected(String scannerName, String threatName,
                                       Instant scannedAt, String message) {
        return new ScanResult(Verdict.INFECTED, scannerName, threatName, scannedAt, message);
    }

    public static ScanResult suspicious(String scannerName, String threatName,
                                         Instant scannedAt, String message) {
        return new ScanResult(Verdict.SUSPICIOUS, scannerName, threatName, scannedAt, message);
    }

    public static ScanResult scanError(String scannerName, Instant scannedAt, String message) {
        return new ScanResult(Verdict.SCAN_ERROR, scannerName, null, scannedAt, message);
    }

    /** 是否应隔离文件。 */
    public boolean shouldQuarantine() {
        return verdict.shouldQuarantine();
    }
}

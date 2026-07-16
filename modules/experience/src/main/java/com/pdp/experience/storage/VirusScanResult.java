package com.pdp.experience.storage;

import java.time.Instant;
import java.util.Objects;

/** 病毒扫描的供应商中立结果。 */
public record VirusScanResult(
        Verdict verdict,
        String engine,
        String signatureVersion,
        String reason,
        Instant scannedAt) {

    public enum Verdict {
        CLEAN,
        MALICIOUS,
        SUSPICIOUS,
        ERROR
    }

    public VirusScanResult {
        Objects.requireNonNull(verdict, "verdict");
        engine = requireText(engine, "engine");
        signatureVersion = requireText(signatureVersion, "signatureVersion");
        Objects.requireNonNull(scannedAt, "scannedAt");
        reason = reason == null ? "" : reason;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}

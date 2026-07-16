package com.pdp.experience.storage;

import java.util.Objects;

/** 经上传校验和病毒扫描后的对象引用；只有 CLEAN 状态允许下载。 */
public record StoredObject(
        ObjectLocation location,
        ObjectMetadata metadata,
        ScanStatus scanStatus,
        VirusScanResult scanResult) {

    public enum ScanStatus {
        PENDING,
        CLEAN,
        QUARANTINED
    }

    public StoredObject {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(scanStatus, "scanStatus");
        if (scanStatus != ScanStatus.PENDING) {
            Objects.requireNonNull(scanResult, "scanResult");
        }
    }
}

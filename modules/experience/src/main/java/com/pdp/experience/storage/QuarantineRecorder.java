package com.pdp.experience.storage;

/** 登记隔离对象及原因，供审计、告警和后续处置使用。 */
@FunctionalInterface
public interface QuarantineRecorder {

    void record(ObjectLocation location, ObjectMetadata metadata, VirusScanResult result);
}

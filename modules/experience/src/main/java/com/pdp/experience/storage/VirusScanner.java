package com.pdp.experience.storage;

/** 对已进入隔离区的对象执行病毒和恶意内容扫描。 */
@FunctionalInterface
public interface VirusScanner {

    VirusScanResult scan(ObjectLocation location);
}

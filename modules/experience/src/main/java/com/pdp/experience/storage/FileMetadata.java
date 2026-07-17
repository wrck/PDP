package com.pdp.experience.storage;

import java.util.Objects;

/**
 * 文件元数据值对象。
 *
 * <p>对应数据模型 {@code FileVersion}：对象存储键、版本号、大小、媒体类型、内容哈希、
 * 病毒扫描状态、上传人和时间。本类封装与对象存储相关的元数据（不含业务版本状态机，
 * 状态机由 {@link FileScanStatus} 独立管理）。
 *
 * @param objectRef       对象存储引用
 * @param versionNumber   文件版本号（同一业务对象的文件版本，从 1 递增）
 * @param sizeBytes       文件大小（字节）
 * @param mediaType       媒体类型（MIME，如 {@code application/pdf}）
 * @param contentHash     内容哈希（SHA-256，十六进制字符串）
 * @param classification  分类级别
 * @param originalFilename 原始文件名（用户上传时的文件名，用于展示）
 */
public record FileMetadata(
        StorageObjectRef objectRef,
        int versionNumber,
        long sizeBytes,
        String mediaType,
        String contentHash,
        FileClassification classification,
        String originalFilename) {

    public FileMetadata {
        Objects.requireNonNull(objectRef, "objectRef 不能为 null");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber 必须 >= 1: " + versionNumber);
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes 不能为负: " + sizeBytes);
        }
        Objects.requireNonNull(mediaType, "mediaType 不能为 null");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType 不能为空白");
        }
        Objects.requireNonNull(contentHash, "contentHash 不能为 null");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空白");
        }
        Objects.requireNonNull(classification, "classification 不能为 null");
        Objects.requireNonNull(originalFilename, "originalFilename 不能为 null");
    }

    public static FileMetadata of(
            StorageObjectRef objectRef,
            int versionNumber,
            long sizeBytes,
            String mediaType,
            String contentHash,
            FileClassification classification,
            String originalFilename) {
        return new FileMetadata(objectRef, versionNumber, sizeBytes, mediaType,
                contentHash, classification, originalFilename);
    }
}

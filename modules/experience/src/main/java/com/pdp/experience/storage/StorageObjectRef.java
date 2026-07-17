package com.pdp.experience.storage;

import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 对象存储引用值对象。
 *
 * <p>对应数据模型 {@code BinaryRef}（对象存储引用）。大文件不进入业务主库，
 * 数据库保存此引用 + 内容哈希 + 版本元数据（research.md 决策 11）。
 *
 * <p>引用由存储桶、对象键和可选的版本 ID 组成。S3 兼容对象存储（MinIO）使用 bucket + key + versionId
 * 定位对象；上传后返回 ETag 用于完整性校验。
 *
 * @param workspaceId 所属工作空间（多租户隔离边界，映射为 bucket 前缀或独立 bucket）
 * @param bucket      存储桶名
 * @param objectKey   对象键（含工作空间前缀，如 {@code ws-uuid/deliverable/objectId/v1}）
 * @param versionId   对象版本 ID（启用版本控制时返回，可为空）
 */
public record StorageObjectRef(
        WorkspaceId workspaceId,
        String bucket,
        String objectKey,
        String versionId) {

    public StorageObjectRef {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(bucket, "bucket 不能为 null");
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("bucket 不能为空白");
        }
        Objects.requireNonNull(objectKey, "objectKey 不能为 null");
        if (objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空白");
        }
    }

    public static StorageObjectRef of(WorkspaceId workspaceId, String bucket, String objectKey) {
        return new StorageObjectRef(workspaceId, bucket, objectKey, null);
    }

    public static StorageObjectRef of(WorkspaceId workspaceId, String bucket, String objectKey, String versionId) {
        return new StorageObjectRef(workspaceId, bucket, objectKey, versionId);
    }

    /**
     * 构造默认对象键（工作空间前缀 + 业务对象类型 + 对象 ID + 版本号）。
     *
     * @param workspaceId  工作空间
     * @param objectType   业务对象类型稳定键（如 {@code deliverable}）
     * @param objectId     业务对象 ID
     * @param version      文件版本号
     * @return 对象键
     */
    public static String buildObjectKey(WorkspaceId workspaceId, String objectType,
                                        UUID objectId, int version) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        Objects.requireNonNull(objectId, "objectId 不能为 null");
        if (version < 1) {
            throw new IllegalArgumentException("version 必须 >= 1: " + version);
        }
        return workspaceId.value() + "/" + objectType + "/" + objectId + "/v" + version;
    }

    /** 是否启用版本控制。 */
    public boolean isVersioned() {
        return versionId != null && !versionId.isBlank();
    }
}

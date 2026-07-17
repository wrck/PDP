package com.pdp.experience.storage;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 文件上传准备请求值对象。
 *
 * <p>由业务模块构造，传递给 {@link FileStorageService#prepareUpload} 申请短时上传 URL。
 * 业务对象类型和 ID 用于构造对象键（{@link StorageObjectRef#buildObjectKey}）和权限校验。
 *
 * @param workspaceId    工作空间
 * @param objectType     业务对象类型稳定键（如 {@code deliverable}）
 * @param objectId       业务对象 ID
 * @param versionNumber  文件版本号（同一业务对象的文件版本，从 1 递增）
 * @param classification 文件分类级别
 * @param mediaType      媒体类型
 * @param originalFilename 原始文件名
 * @param contentHash    内容哈希（SHA-256，前端预计算）
 */
public record UploadRequest(
        WorkspaceId workspaceId,
        String objectType,
        UUID objectId,
        int versionNumber,
        FileClassification classification,
        String mediaType,
        String originalFilename,
        String contentHash) {

    public UploadRequest {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        if (objectType.isBlank()) {
            throw new IllegalArgumentException("objectType 不能为空白");
        }
        Objects.requireNonNull(objectId, "objectId 不能为 null");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber 必须 >= 1: " + versionNumber);
        }
        Objects.requireNonNull(classification, "classification 不能为 null");
        Objects.requireNonNull(mediaType, "mediaType 不能为 null");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType 不能为空白");
        }
        Objects.requireNonNull(originalFilename, "originalFilename 不能为 null");
        Objects.requireNonNull(contentHash, "contentHash 不能为 null");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空白");
        }
    }
}

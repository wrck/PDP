package com.pdp.experience.storage;

import java.util.Objects;
import java.util.UUID;

/** S3 兼容对象的工作空间隔离位置。 */
public record ObjectLocation(UUID workspaceId, String key, String versionId) {

    public ObjectLocation {
        Objects.requireNonNull(workspaceId, "workspaceId");
        key = requireSafeKey(key);
        versionId = requireText(versionId, "versionId");
    }

    private static String requireSafeKey(String value) {
        String key = requireText(value, "key");
        if (key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("对象键包含不安全路径");
        }
        return key;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}

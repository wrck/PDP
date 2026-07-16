package com.pdp.experience.storage;

import java.util.Locale;

/** 数据库文件版本事实所需的对象存储校验元数据。 */
public record ObjectMetadata(
        long size,
        String mediaType,
        String checksumSha256,
        String eTag) {

    public ObjectMetadata {
        if (size < 0) {
            throw new IllegalArgumentException("对象大小不能为负数");
        }
        mediaType = requireText(mediaType, "mediaType");
        checksumSha256 = requireSha256(checksumSha256);
        eTag = requireText(eTag, "eTag");
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "checksumSha256").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksumSha256 必须是 64 位十六进制摘要");
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}

package com.pdp.experience.storage;

import java.time.Instant;
import java.util.Map;

/**
 * S3 兼容供应商的最小能力边界。
 *
 * <p>具体 AWS S3、MinIO 或其他兼容 SDK 实现留在基础设施装配层，业务模块不依赖供应商类型。
 */
public interface S3ObjectClient {

    SignedObjectUrl presignPut(
            ObjectLocation location,
            Instant expiresAt,
            Map<String, String> requiredHeaders);

    SignedObjectUrl presignGet(ObjectLocation location, Instant expiresAt);

    ObjectMetadata head(ObjectLocation location);

    ObjectMetadata copy(ObjectLocation source, String targetKey);

    void delete(ObjectLocation location);
}

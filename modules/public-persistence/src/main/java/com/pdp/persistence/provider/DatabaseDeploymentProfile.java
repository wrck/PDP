package com.pdp.persistence.provider;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 受管数据库部署事实，不包含连接串或凭据。
 */
public record DatabaseDeploymentProfile(
        UUID deploymentId,
        String stableKey,
        DatabaseCapabilityProfile capabilityProfile,
        WriteAuthority writeAuthority,
        DeploymentStatus status) {

    private static final Pattern STABLE_KEY = Pattern.compile("[a-z][a-z0-9-]{2,63}");

    public DatabaseDeploymentProfile {
        deploymentId = Objects.requireNonNull(deploymentId, "deploymentId");
        stableKey = Objects.requireNonNull(stableKey, "stableKey").trim();
        if (!STABLE_KEY.matcher(stableKey).matches()) {
            throw new IllegalArgumentException("部署稳定标识格式非法: " + stableKey);
        }
        capabilityProfile = Objects.requireNonNull(capabilityProfile, "capabilityProfile");
        writeAuthority = Objects.requireNonNull(writeAuthority, "writeAuthority");
        status = Objects.requireNonNull(status, "status");
    }

    public enum WriteAuthority {
        PRIMARY,
        NONE
    }

    public enum DeploymentStatus {
        CERTIFIED,
        DEGRADED,
        UNSUPPORTED
    }
}

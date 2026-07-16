package com.pdp.persistence.provider;

import java.util.Objects;
import java.util.Set;

/**
 * 数据库部署经过探测后得到的公共能力画像。
 */
public record DatabaseCapabilityProfile(
        DatabaseProduct databaseProduct,
        DatabaseVersion databaseVersion,
        String certificationBaseline,
        Set<String> capabilities) {

    public DatabaseCapabilityProfile {
        databaseProduct = Objects.requireNonNull(databaseProduct, "databaseProduct");
        databaseVersion = Objects.requireNonNull(databaseVersion, "databaseVersion");
        certificationBaseline = requireText(certificationBaseline, "certificationBaseline");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}

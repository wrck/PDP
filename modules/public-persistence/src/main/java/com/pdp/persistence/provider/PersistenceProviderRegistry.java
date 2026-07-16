package com.pdp.persistence.provider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 严格提供方注册表：同一数据库产品只能有一个激活提供方，未知产品立即失败。
 */
public final class PersistenceProviderRegistry {

    private final Map<DatabaseProduct, PersistenceProvider> providers;

    public PersistenceProviderRegistry(Collection<? extends PersistenceProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        Map<DatabaseProduct, PersistenceProvider> byProduct = new LinkedHashMap<>();
        Map<String, DatabaseProduct> providerKeys = new LinkedHashMap<>();
        for (PersistenceProvider provider : providers) {
            Objects.requireNonNull(provider, "provider");
            DatabaseProduct duplicateKey = providerKeys.putIfAbsent(
                    requireText(provider.providerKey()), provider.databaseProduct());
            if (duplicateKey != null) {
                throw new IllegalStateException("持久化提供方键重复: " + provider.providerKey());
            }
            PersistenceProvider duplicateProduct = byProduct.putIfAbsent(provider.databaseProduct(), provider);
            if (duplicateProduct != null) {
                throw new IllegalStateException(
                        "数据库产品只能激活一个持久化提供方: " + provider.databaseProduct().stableKey());
            }
        }
        this.providers = Map.copyOf(byProduct);
    }

    public PersistenceProvider require(DatabaseProduct databaseProduct) {
        PersistenceProvider provider = providers.get(Objects.requireNonNull(databaseProduct, "databaseProduct"));
        if (provider == null) {
            throw new UnsupportedDatabaseCapabilityException(
                    "没有已激活的数据库提供方: " + databaseProduct.stableKey());
        }
        return provider;
    }

    public DatabaseSwitchCapability requireSwitchCapability(
            DatabaseCapabilityProfile source,
            DatabaseCapabilityProfile target) {
        return require(source.databaseProduct()).switchCapabilities().stream()
                .filter(capability -> capability.supports(source, target))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDatabaseCapabilityException(
                        "未认证数据库切换组合: "
                                + source.databaseProduct().stableKey()
                                + "→"
                                + target.databaseProduct().stableKey()));
    }

    public Collection<PersistenceProvider> providers() {
        return providers.values();
    }

    private static String requireText(String value) {
        value = Objects.requireNonNull(value, "providerKey").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("providerKey 不能为空");
        }
        return value;
    }
}

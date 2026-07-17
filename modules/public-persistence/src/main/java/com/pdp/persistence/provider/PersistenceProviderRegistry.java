package com.pdp.persistence.provider;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化适配器注册表。
 *
 * <p>P1 仅允许一个已认证适配器激活（单写主权）。
 * 维护 {@link PersistenceProvider} 与 {@link DataSourceRegistration} 两张注册表，
 * 在启动与运行期校验唯一写主、唯一 workflow engine 与迁移源/目标的有效期。
 */
@Component
public class PersistenceProviderRegistry {

    private final Map<DatabaseType, PersistenceProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, DataSourceRegistration> registrations = new ConcurrentHashMap<>();

    /** 注册持久化适配器；同一 DatabaseType 仅允许一个。 */
    public void register(PersistenceProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为 null");
        }
        providers.merge(provider.capabilityProfile().databaseType(), provider,
                (existing, incoming) -> {
                    throw new IllegalStateException("数据库类型 " + existing.capabilityProfile().databaseType()
                            + " 已注册适配器，P1 仅允许一个已认证适配器激活");
                });
    }

    /** 注册数据源；同一 dataSourceKey 仅允许一个。 */
    public void register(DataSourceRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration 不能为 null");
        }
        registrations.merge(registration.dataSourceKey(), registration,
                (existing, incoming) -> {
                    throw new IllegalStateException("数据源键 " + existing.dataSourceKey() + " 已注册");
                });
    }

    public Optional<PersistenceProvider> provider(DatabaseType type) {
        return Optional.ofNullable(providers.get(type));
    }

    public Optional<DataSourceRegistration> registration(String dataSourceKey) {
        return Optional.ofNullable(registrations.get(dataSourceKey));
    }

    public Collection<DataSourceRegistration> registrations() {
        return registrations.values();
    }

    public boolean isActive(String dataSourceKey) {
        return Optional.ofNullable(registrations.get(dataSourceKey))
                .map(r -> r.status() == RegistrationStatus.ACTIVE)
                .orElse(false);
    }

    /** 当前激活的 PDP_PRIMARY 适配器（P1 唯一）。 */
    public PersistenceProvider activeProvider() {
        if (providers.size() != 1) {
            throw new IllegalStateException("P1 仅允许一个已认证适配器激活，当前注册数: " + providers.size());
        }
        return providers.values().iterator().next();
    }

    /**
     * 校验单写主权：同一时刻只能有一个 PDP_PRIMARY 处于 ACTIVE。
     * 同时校验 WORKFLOW_ENGINE 唯一存在。
     */
    public void assertSingleWriteSovereignty() {
        long primaryCount = registrations.values().stream()
                .filter(r -> r.role() == DataSourceRole.PDP_PRIMARY && r.status() == RegistrationStatus.ACTIVE)
                .count();
        if (primaryCount != 1) {
            throw new PdpException(ErrorCode.DATABASE_CAPABILITY_REJECTED,
                    "单写主权违反：PDP_PRIMARY ACTIVE 数量必须为 1，实际 " + primaryCount);
        }
        long workflowCount = registrations.values().stream()
                .filter(r -> r.role() == DataSourceRole.WORKFLOW_ENGINE && r.status() == RegistrationStatus.ACTIVE)
                .count();
        if (workflowCount != 1) {
            throw new PdpException(ErrorCode.DATABASE_CAPABILITY_REJECTED,
                    "WORKFLOW_ENGINE ACTIVE 数量必须为 1，实际 " + workflowCount);
        }
    }

    /** 清除所有注册（仅测试用）。 */
    public void clear() {
        providers.clear();
        registrations.clear();
    }
}

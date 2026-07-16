package com.pdp.mysql.config;

import com.pdp.mysql.routing.DataSourceRoutingGuard;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DynamicDataSourceConfig {

    @Bean
    OnlineDataSourcePolicy onlineDataSourcePolicy(
            @Value("${spring.datasource.dynamic.strict:true}") boolean strict,
            @Value("${spring.datasource.dynamic.primary:pdpPrimary}") String primary,
            @Value("${pdp.persistence.online-data-source-keys:pdpPrimary,pdpRead}") String keys) {
        Set<String> allowedKeys = new LinkedHashSet<>(Arrays.stream(keys.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList());
        OnlineDataSourcePolicy policy = new OnlineDataSourcePolicy(strict, primary, allowedKeys);
        policy.validate();
        return policy;
    }

    @Bean
    DataSourceRoutingGuard dataSourceRoutingGuard(OnlineDataSourcePolicy policy) {
        return new DataSourceRoutingGuard(policy.allowedKeys());
    }

    public record OnlineDataSourcePolicy(boolean strict, String primary, Set<String> allowedKeys) {

        public OnlineDataSourcePolicy {
            primary = primary == null ? "" : primary.trim();
            allowedKeys = Set.copyOf(allowedKeys);
        }

        void validate() {
            if (!strict) {
                throw new IllegalStateException("在线动态数据源必须启用 strict=true");
            }
            if (!DataSourceRoutingGuard.PDP_PRIMARY.equals(primary)) {
                throw new IllegalStateException("在线动态数据源主键必须是 pdpPrimary");
            }
            if (!allowedKeys.contains(DataSourceRoutingGuard.PDP_PRIMARY)
                    || !DataSourceRoutingGuard.ONLINE_KEYS.containsAll(allowedKeys)) {
                throw new IllegalStateException("在线业务只允许登记 pdpPrimary 和 pdpRead");
            }
        }
    }
}

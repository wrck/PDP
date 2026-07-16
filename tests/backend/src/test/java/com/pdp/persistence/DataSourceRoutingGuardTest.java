package com.pdp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.pdp.mysql.routing.DataSourceRoutingException;
import com.pdp.mysql.routing.DataSourceRoutingGuard;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DataSourceRoutingGuardTest {

    private final DataSourceRoutingGuard guard =
            new DataSourceRoutingGuard(Set.of("pdpPrimary", "pdpRead"));

    @AfterEach
    void clearThreadState() {
        DynamicDataSourceContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void routesPrimaryAndCleansContext() {
        try (var ignored = guard.primary()) {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("pdpPrimary");
        }

        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    @Test
    void rejectsUnknownRouteAndReadReplicaWrites() {
        assertThatThrownBy(() -> guard.open(
                        "migrationTarget", DataSourceRoutingGuard.AccessMode.READ))
                .isInstanceOf(DataSourceRoutingException.class)
                .hasMessageContaining("未知或未授权");

        assertThatThrownBy(() -> guard.open(
                        "pdpRead", DataSourceRoutingGuard.AccessMode.WRITE))
                .isInstanceOf(DataSourceRoutingException.class)
                .hasMessageContaining("禁止写入");
    }

    @Test
    void rejectsSwitchingInsideAnActiveTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (var ignored = guard.primary()) {
            assertThatThrownBy(guard::readReplica)
                    .isInstanceOf(DataSourceRoutingException.class)
                    .hasMessageContaining("事务内禁止切换");
        }
    }
}

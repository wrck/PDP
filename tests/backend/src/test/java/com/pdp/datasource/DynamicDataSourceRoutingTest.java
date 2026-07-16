package com.pdp.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.pdp.mysql.routing.DataSourceRoutingException;
import com.pdp.mysql.routing.DataSourceRoutingGuard;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DynamicDataSourceRoutingTest {

    @AfterEach
    void clearThreadState() {
        DynamicDataSourceContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void routesEventuallyConsistentReadsToHealthyReplica() {
        DataSourceRoutingGuard guard =
                new DataSourceRoutingGuard(Set.of("pdpPrimary", "pdpRead"));

        try (var ignored = guard.readPreferred(true)) {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("pdpRead");
        }

        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    @Test
    void degradesReadsToPrimaryWhenReplicaIsMissingOrUnhealthy() {
        DataSourceRoutingGuard primaryOnly =
                new DataSourceRoutingGuard(Set.of("pdpPrimary"));
        try (var ignored = primaryOnly.readPreferred(true)) {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("pdpPrimary");
        }

        DataSourceRoutingGuard withReplica =
                new DataSourceRoutingGuard(Set.of("pdpPrimary", "pdpRead"));
        try (var ignored = withReplica.readPreferred(false)) {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("pdpPrimary");
        }
    }

    @Test
    void rejectsMigrationRoutesAndTransactionRouteSwitches() {
        DataSourceRoutingGuard guard =
                new DataSourceRoutingGuard(Set.of("pdpPrimary", "pdpRead"));

        assertThatThrownBy(() -> guard.open(
                        "migrationSource", DataSourceRoutingGuard.AccessMode.READ))
                .isInstanceOf(DataSourceRoutingException.class)
                .hasMessageContaining("未知或未授权");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (var ignored = guard.primary()) {
            assertThatThrownBy(() -> guard.readPreferred(true))
                    .isInstanceOf(DataSourceRoutingException.class)
                    .hasMessageContaining("事务内禁止切换");
        }
    }

    @Test
    void rejectsWriteThroughReadReplica() {
        DataSourceRoutingGuard guard =
                new DataSourceRoutingGuard(Set.of("pdpPrimary", "pdpRead"));

        assertThatThrownBy(
                        () -> guard.open("pdpRead", DataSourceRoutingGuard.AccessMode.WRITE))
                .isInstanceOf(DataSourceRoutingException.class)
                .hasMessageContaining("禁止写入");
    }
}

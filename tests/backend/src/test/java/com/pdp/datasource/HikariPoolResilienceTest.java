package com.pdp.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class HikariPoolResilienceTest {

    @Test
    void failsFastWhenPoolIsExhaustedAndRecoversAfterConnectionReturn() throws Exception {
        try (HikariDataSource pool = pool("exhaustion", h2("exhaustion"))) {
            try (Connection held = pool.getConnection()) {
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                assertThatThrownBy(pool::getConnection)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("Connection is not available");
            }

            try (Connection recovered = pool.getConnection()) {
                assertThat(recovered.isValid(1)).isTrue();
            }
        }
    }

    @Test
    void exposesBorrowedConnectionsAndClearsLeakSignalWhenReturned() throws Exception {
        try (HikariDataSource pool = pool("leak-observation", h2("leak-observation"))) {
            Connection borrowed = pool.getConnection();
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            assertThat(pool.getHikariPoolMXBean().getIdleConnections()).isZero();

            borrowed.close();

            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(pool.getHikariPoolMXBean().getIdleConnections()).isEqualTo(1);
        }
    }

    @Test
    void rebuildsConnectionsAfterDatabaseFailureIsCleared() throws Exception {
        ToggleDataSource database = new ToggleDataSource(h2("recovery"));
        try (HikariDataSource pool = pool("recovery", database)) {
            try (Connection connection = pool.getConnection()) {
                assertThat(connection.isValid(1)).isTrue();
            }

            database.available.set(false);
            pool.getHikariPoolMXBean().softEvictConnections();
            assertThatThrownBy(pool::getConnection)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Connection is not available");

            database.available.set(true);
            try (Connection recovered = pool.getConnection()) {
                assertThat(recovered.isValid(1)).isTrue();
            }
        }
    }

    private static HikariDataSource pool(String name, DataSource dataSource) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("test-" + name);
        config.setDataSource(dataSource);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(250);
        config.setValidationTimeout(250);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    private static JdbcDataSource h2(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static final class ToggleDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicBoolean available = new AtomicBoolean(true);

        private ToggleDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            ensureAvailable();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            ensureAvailable();
            return delegate.getConnection(username, password);
        }

        private void ensureAvailable() throws SQLException {
            if (!available.get()) {
                throw new SQLException("模拟数据库不可用");
            }
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}

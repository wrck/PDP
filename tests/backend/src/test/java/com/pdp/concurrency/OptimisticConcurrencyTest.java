package com.pdp.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.EntityTag;
import com.pdp.shared.concurrency.OptimisticConcurrencyGuard;
import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.error.ErrorCode;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class OptimisticConcurrencyTest {

    @Test
    void rejectsStaleUpdateAsConflictAndAllowsExplicitRetry() throws Exception {
        String url = "jdbc:h2:mem:optimistic-concurrency;DB_CLOSE_DELAY=-1";
        try (Connection setup = DriverManager.getConnection(url, "sa", "")) {
            setup.createStatement().execute("""
                    CREATE TABLE concurrent_item (
                      id VARCHAR(40) PRIMARY KEY,
                      payload VARCHAR(100) NOT NULL,
                      revision BIGINT NOT NULL
                    )
                    """);
            setup.createStatement().execute(
                    "INSERT INTO concurrent_item(id,payload,revision) VALUES ('item-1','initial',0)");
        }

        try (Connection firstUser = DriverManager.getConnection(url, "sa", "");
                Connection secondUser = DriverManager.getConnection(url, "sa", "")) {
            firstUser.setAutoCommit(false);
            secondUser.setAutoCommit(false);
            firstUser.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            secondUser.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            Revision firstRead = readRevision(firstUser);
            Revision secondRead = readRevision(secondUser);
            assertThat(firstRead).isEqualTo(new Revision(0));
            assertThat(secondRead).isEqualTo(new Revision(0));

            assertThat(update(firstUser, "first-update", firstRead)).isEqualTo(1);
            firstUser.commit();

            assertThat(update(secondUser, "stale-second-update", secondRead)).isZero();
            secondUser.rollback();

            Revision current = readRevision(secondUser);
            assertThatThrownBy(
                            () -> OptimisticConcurrencyGuard.requireMatch(secondRead, current))
                    .isInstanceOf(ConcurrencyConflictException.class)
                    .satisfies(error -> {
                        ConcurrencyConflictException conflict =
                                (ConcurrencyConflictException) error;
                        assertThat(conflict.errorCode()).isEqualTo(ErrorCode.REVISION_CONFLICT);
                        assertThat(conflict.details())
                                .containsEntry("expectedRevision", 0L)
                                .containsEntry("actualRevision", 1L);
                    });
            assertThat(EntityTag.from(current).value()).isEqualTo("\"1\"");

            assertThat(update(secondUser, "retried-second-update", current)).isEqualTo(1);
            secondUser.commit();
            assertThat(readRevision(secondUser)).isEqualTo(new Revision(2));
            assertThat(readPayload(secondUser)).isEqualTo("retried-second-update");
        }
    }

    private static int update(Connection connection, String payload, Revision expected)
            throws Exception {
        try (var statement = connection.prepareStatement("""
                UPDATE concurrent_item
                   SET payload = ?, revision = revision + 1
                 WHERE id = 'item-1' AND revision = ?
                """)) {
            statement.setString(1, payload);
            statement.setLong(2, expected.value());
            return statement.executeUpdate();
        }
    }

    private static Revision readRevision(Connection connection) throws Exception {
        try (var resultSet = connection.createStatement()
                .executeQuery("SELECT revision FROM concurrent_item WHERE id = 'item-1'")) {
            assertThat(resultSet.next()).isTrue();
            return new Revision(resultSet.getLong(1));
        }
    }

    private static String readPayload(Connection connection) throws Exception {
        try (var resultSet = connection.createStatement()
                .executeQuery("SELECT payload FROM concurrent_item WHERE id = 'item-1'")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}

package com.pdp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.mysql.typehandler.UuidBinaryTypeHandler;
import com.pdp.shared.page.CursorValidationException;
import com.pdp.shared.page.KeysetCursor;
import com.pdp.shared.page.SignedKeysetCursorCodec;
import com.pdp.shared.page.SortDirection;
import com.pdp.shared.page.SortOrder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class CursorPaginationDatabaseContractTest {

    private static final UUID WORKSPACE_A =
            UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final UUID WORKSPACE_B =
            UUID.fromString("018f0000-0000-7000-8000-000000000002");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC);
    private static final SignedKeysetCursorCodec CODEC =
            new SignedKeysetCursorCodec(
                    "cursor-v1",
                    Map.of(
                            "cursor-v1",
                            "pdp-cursor-contract-secret-32-bytes".getBytes(
                                    StandardCharsets.UTF_8)),
                    Duration.ofHours(1),
                    CLOCK);

    @Container
    private static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4").withDatabaseName("pdp_cursor");

    @Test
    void paginatesWithStableKeysetDuringConcurrentInsertDeleteAndWorkspaceFiltering()
            throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            createTable(connection);
            insert(connection, WORKSPACE_A, id(1), "2026-07-17T07:59:00Z", "a-1");
            insert(connection, WORKSPACE_A, id(2), "2026-07-17T07:58:00Z", "a-2");
            insert(connection, WORKSPACE_A, id(3), "2026-07-17T07:58:00Z", "a-3");
            insert(connection, WORKSPACE_A, id(4), "2026-07-17T07:57:00Z", "a-4");
            insert(connection, WORKSPACE_B, id(5), "2026-07-17T07:56:00Z", "b-1");

            List<Row> first = page(connection, WORKSPACE_A, null, 2);
            assertThat(first).extracting(Row::payload).containsExactly("a-1", "a-2");

            Row last = first.getLast();
            KeysetCursor cursor =
                    new KeysetCursor(
                            WORKSPACE_A.toString(),
                            "permission-and-filter-v1",
                            List.of(
                                    new SortOrder(
                                            "updated_at", SortDirection.DESCENDING, false),
                                    new SortOrder("id", SortDirection.ASCENDING, true)),
                            List.of(last.updatedAt().toString(), last.id().toString()),
                            CLOCK.instant());
            String encoded = CODEC.encode(cursor);

            insert(connection, WORKSPACE_A, id(6), "2026-07-17T07:58:30Z", "new-before-cursor");
            try (var statement =
                    connection.prepareStatement("DELETE FROM cursor_item WHERE id = ?")) {
                statement.setBytes(1, UuidBinaryTypeHandler.toBytes(id(1)));
                statement.executeUpdate();
            }

            KeysetCursor decoded =
                    CODEC.decode(
                            encoded, WORKSPACE_A.toString(), "permission-and-filter-v1");
            List<Row> second = page(connection, WORKSPACE_A, decoded, 10);

            assertThat(second).extracting(Row::payload).containsExactly("a-3", "a-4");
            assertThat(second).extracting(Row::payload)
                    .doesNotContain("a-1", "a-2", "new-before-cursor", "b-1");
        }
    }

    @Test
    void rejectsCursorTamperingFilterChangesAndWorkspaceReuse() {
        KeysetCursor cursor =
                new KeysetCursor(
                        WORKSPACE_A.toString(),
                        "permission-and-filter-v1",
                        List.of(new SortOrder("id", SortDirection.ASCENDING, true)),
                        List.of(id(1).toString()),
                        CLOCK.instant());
        String encoded = CODEC.encode(cursor);
        String tampered =
                encoded.substring(0, encoded.length() - 1)
                        + (encoded.endsWith("A") ? "B" : "A");

        assertThatThrownBy(
                        () -> CODEC.decode(
                                tampered,
                                WORKSPACE_A.toString(),
                                "permission-and-filter-v1"))
                .isInstanceOf(CursorValidationException.class);
        assertThatThrownBy(
                        () -> CODEC.decode(
                                encoded, WORKSPACE_A.toString(), "permission-and-filter-v2"))
                .isInstanceOf(CursorValidationException.class)
                .hasMessageContaining("筛选");
        assertThatThrownBy(
                        () -> CODEC.decode(
                                encoded,
                                WORKSPACE_B.toString(),
                                "permission-and-filter-v1"))
                .isInstanceOf(CursorValidationException.class)
                .hasMessageContaining("工作空间");
    }

    private static void createTable(Connection connection) throws Exception {
        connection.createStatement().execute("""
                CREATE TABLE cursor_item (
                  workspace_id BINARY(16) NOT NULL,
                  id BINARY(16) NOT NULL,
                  updated_at DATETIME(6) NOT NULL,
                  payload VARCHAR(60) NOT NULL,
                  PRIMARY KEY(id),
                  INDEX idx_cursor_workspace_order(workspace_id, updated_at DESC, id ASC)
                )
                """);
    }

    private static void insert(
            Connection connection,
            UUID workspaceId,
            UUID id,
            String updatedAt,
            String payload)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO cursor_item(workspace_id,id,updated_at,payload) VALUES (?,?,?,?)")) {
            statement.setBytes(1, UuidBinaryTypeHandler.toBytes(workspaceId));
            statement.setBytes(2, UuidBinaryTypeHandler.toBytes(id));
            statement.setTimestamp(3, Timestamp.from(Instant.parse(updatedAt)));
            statement.setString(4, payload);
            statement.executeUpdate();
        }
    }

    private static List<Row> page(
            Connection connection, UUID workspaceId, KeysetCursor cursor, int limit)
            throws Exception {
        String sql = cursor == null
                ? """
                  SELECT id, updated_at, payload
                    FROM cursor_item
                   WHERE workspace_id = ?
                   ORDER BY updated_at DESC, id ASC
                   LIMIT ?
                  """
                : """
                  SELECT id, updated_at, payload
                    FROM cursor_item
                   WHERE workspace_id = ?
                     AND (updated_at < ? OR (updated_at = ? AND id > ?))
                   ORDER BY updated_at DESC, id ASC
                   LIMIT ?
                  """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBinaryTypeHandler.toBytes(workspaceId));
            if (cursor == null) {
                statement.setInt(2, limit);
            } else {
                Timestamp updatedAt = Timestamp.from(Instant.parse(cursor.values().get(0)));
                statement.setTimestamp(2, updatedAt);
                statement.setTimestamp(3, updatedAt);
                statement.setBytes(
                        4,
                        UuidBinaryTypeHandler.toBytes(
                                UUID.fromString(cursor.values().get(1))));
                statement.setInt(5, limit);
            }
            List<Row> rows = new ArrayList<>();
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new Row(
                            UuidBinaryTypeHandler.fromBytes(resultSet.getBytes("id")),
                            resultSet.getTimestamp("updated_at").toInstant(),
                            resultSet.getString("payload")));
                }
            }
            return rows;
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("018f0000-0000-7000-8000-%012d".formatted(suffix));
    }

    private record Row(UUID id, Instant updatedAt, String payload) {}
}

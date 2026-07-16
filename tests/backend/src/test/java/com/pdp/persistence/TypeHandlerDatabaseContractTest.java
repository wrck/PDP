package com.pdp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.mysql.typehandler.InstantUtcTypeHandler;
import com.pdp.mysql.typehandler.JsonDocumentTypeHandler;
import com.pdp.mysql.typehandler.StableEnumTypeHandler;
import com.pdp.mysql.typehandler.StringValueTypeHandler;
import com.pdp.mysql.typehandler.UuidBinaryTypeHandler;
import com.pdp.persistence.type.JsonDocument;
import com.pdp.persistence.type.StableEnum;
import com.pdp.persistence.type.StringValue;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class TypeHandlerDatabaseContractTest {

    @Container
    private static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4").withDatabaseName("pdp_typehandler");

    @Test
    void preservesUuidJsonEnumInstantAndValueObjectSemantics() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            connection.createStatement().execute("""
                    CREATE TABLE handler_roundtrip (
                      id BINARY(16) PRIMARY KEY,
                      payload JSON NOT NULL,
                      status VARCHAR(30) NOT NULL,
                      happened_at DATETIME(6) NOT NULL,
                      value_text VARCHAR(100) NOT NULL
                    )
                    """);

            UUID id = UUID.randomUUID();
            JsonDocument json = new JsonDocument("{\"enabled\":true,\"count\":2}");
            Instant instant = Instant.now().truncatedTo(ChronoUnit.MICROS);
            StableEnumTypeHandler<SampleStatus> enumHandler =
                    new StableEnumTypeHandler<>(SampleStatus.class);
            StringValueTypeHandler<SampleValue> valueHandler =
                    new StringValueTypeHandler<>(SampleValue.class);

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO handler_roundtrip(id,payload,status,happened_at,value_text) VALUES (?,?,?,?,?)")) {
                new UuidBinaryTypeHandler()
                        .setNonNullParameter(statement, 1, id, JdbcType.BINARY);
                new JsonDocumentTypeHandler()
                        .setNonNullParameter(statement, 2, json, JdbcType.OTHER);
                enumHandler.setNonNullParameter(statement, 3, SampleStatus.ACTIVE, JdbcType.VARCHAR);
                new InstantUtcTypeHandler()
                        .setNonNullParameter(statement, 4, instant, JdbcType.TIMESTAMP);
                valueHandler.setNonNullParameter(
                        statement, 5, new SampleValue("stable-value"), JdbcType.VARCHAR);
                statement.executeUpdate();
            }

            try (var resultSet = connection.createStatement()
                    .executeQuery("SELECT * FROM handler_roundtrip")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(new UuidBinaryTypeHandler().getNullableResult(resultSet, "id"))
                        .isEqualTo(id);
                assertThat(new JsonDocumentTypeHandler().getNullableResult(resultSet, "payload"))
                        .isEqualTo(json);
                assertThat(enumHandler.getNullableResult(resultSet, "status"))
                        .isEqualTo(SampleStatus.ACTIVE);
                assertThat(new InstantUtcTypeHandler()
                                .getNullableResult(resultSet, "happened_at"))
                        .isEqualTo(instant);
                assertThat(valueHandler.getNullableResult(resultSet, "value_text"))
                        .isEqualTo(new SampleValue("stable-value"));
            }
        }
    }

    private enum SampleStatus implements StableEnum {
        ACTIVE;

        @Override
        public String stableKey() {
            return "ACTIVE";
        }
    }

    public record SampleValue(String value) implements StringValue {}
}

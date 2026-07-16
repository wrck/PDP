package com.pdp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdp.mysql.typehandler.InstantUtcTypeHandler;
import com.pdp.mysql.typehandler.JsonDocumentTypeHandler;
import com.pdp.mysql.typehandler.StableEnumTypeHandler;
import com.pdp.mysql.typehandler.StringValueTypeHandler;
import com.pdp.mysql.typehandler.UuidBinaryTypeHandler;
import com.pdp.persistence.type.JsonDocument;
import com.pdp.persistence.type.StableEnum;
import com.pdp.persistence.type.StringValue;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypeHandlerSemanticsTest {

    @Test
    void preservesUuidNetworkByteOrder() {
        UUID value = UUID.fromString("018f7e2a-2f60-7d90-8b31-7c1aef01d600");

        assertThat(UuidBinaryTypeHandler.fromBytes(UuidBinaryTypeHandler.toBytes(value)))
                .isEqualTo(value);
    }

    @Test
    void restoresJsonEnumInstantAndStringValueWithoutChangingStableSemantics()
            throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Instant instant = Instant.parse("2026-07-17T01:02:03.456789Z");
        when(resultSet.getString("payload")).thenReturn("{\"state\":\"ACTIVE\"}");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getString("value_text")).thenReturn("stable-value");
        when(resultSet.getTimestamp(
                        org.mockito.ArgumentMatchers.eq("happened_at"),
                        org.mockito.ArgumentMatchers.any(java.util.Calendar.class)))
                .thenReturn(Timestamp.from(instant));

        assertThat(new JsonDocumentTypeHandler().getNullableResult(resultSet, "payload"))
                .isEqualTo(new JsonDocument("{\"state\":\"ACTIVE\"}"));
        assertThat(new StableEnumTypeHandler<>(SampleStatus.class)
                        .getNullableResult(resultSet, "status"))
                .isEqualTo(SampleStatus.ACTIVE);
        assertThat(new InstantUtcTypeHandler().getNullableResult(resultSet, "happened_at"))
                .isEqualTo(instant);
        assertThat(new StringValueTypeHandler<>(SampleValue.class)
                        .getNullableResult(resultSet, "value_text"))
                .isEqualTo(new SampleValue("stable-value"));
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

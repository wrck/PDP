package com.pdp.mysql.typehandler;

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class UuidBinaryTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setBytes(index, toBytes(parameter));
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromBytes(resultSet.getBytes(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromBytes(resultSet.getBytes(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromBytes(statement.getBytes(columnIndex));
    }

    public static byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    public static UUID fromBytes(byte[] value) {
        if (value == null) {
            return null;
        }
        if (value.length != 16) {
            throw new IllegalArgumentException("UUID BINARY 值必须为 16 字节");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}

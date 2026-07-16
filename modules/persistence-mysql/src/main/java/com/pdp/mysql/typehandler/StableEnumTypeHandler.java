package com.pdp.mysql.typehandler;

import com.pdp.persistence.type.StableEnum;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class StableEnumTypeHandler<E extends Enum<E> & StableEnum>
        extends BaseTypeHandler<E> {

    private final Map<String, E> values;

    public StableEnumTypeHandler(Class<E> enumType) {
        values = Arrays.stream(enumType.getEnumConstants())
                .collect(Collectors.toUnmodifiableMap(
                        StableEnum::stableKey, Function.identity()));
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, E parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, parameter.stableKey());
    }

    @Override
    public E getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromStableKey(resultSet.getString(columnName));
    }

    @Override
    public E getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromStableKey(resultSet.getString(columnIndex));
    }

    @Override
    public E getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromStableKey(statement.getString(columnIndex));
    }

    private E fromStableKey(String value) {
        if (value == null) {
            return null;
        }
        E result = values.get(value);
        if (result == null) {
            throw new IllegalArgumentException("未知枚举稳定键: " + value);
        }
        return result;
    }
}

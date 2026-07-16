package com.pdp.mysql.typehandler;

import com.pdp.persistence.type.StringValue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class StringValueTypeHandler<T extends StringValue> extends BaseTypeHandler<T> {

    private final Constructor<T> constructor;

    public StringValueTypeHandler(Class<T> valueType) {
        try {
            constructor = valueType.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("字符串值对象必须提供单字符串构造器", exception);
        }
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, T parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, parameter.value());
    }

    @Override
    public T getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromString(resultSet.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromString(resultSet.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromString(statement.getString(columnIndex));
    }

    private T fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return constructor.newInstance(value);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("无法恢复字符串值对象", exception);
        }
    }
}

package com.pdp.mysql.typehandler;

import com.pdp.persistence.type.JsonDocument;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class JsonDocumentTypeHandler extends BaseTypeHandler<JsonDocument> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, JsonDocument parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, parameter.value());
    }

    @Override
    public JsonDocument getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromString(resultSet.getString(columnName));
    }

    @Override
    public JsonDocument getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromString(resultSet.getString(columnIndex));
    }

    @Override
    public JsonDocument getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return fromString(statement.getString(columnIndex));
    }

    private JsonDocument fromString(String value) {
        return value == null ? null : new JsonDocument(value);
    }
}

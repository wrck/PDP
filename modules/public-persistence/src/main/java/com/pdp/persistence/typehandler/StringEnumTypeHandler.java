package com.pdp.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 枚举稳定键 ↔ varchar TypeHandler。
 *
 * <p>保存枚举 name() 稳定键，禁止 ordinal 持久化（枚举顺序变化不会破坏数据）。
 * 反序列化时按 name() 查找，未知值抛出 SQLException 而非静默返回 null。
 */
@MappedTypes(Enum.class)
public class StringEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> type;

    public StringEnumTypeHandler(Class<E> type) {
        if (type == null) {
            throw new IllegalArgumentException("枚举类型不能为 null");
        }
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private E parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        throw new SQLException("未知枚举稳定键 " + value + " for " + type.getName());
    }
}

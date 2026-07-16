package com.pdp.mysql;

import com.pdp.persistence.provider.DatabaseDialect;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MysqlDialectAdapter implements DatabaseDialect {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    @Override
    public String quoteIdentifier(String identifier) {
        identifier = Objects.requireNonNull(identifier, "identifier");
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("MySQL 标识符格式非法: " + identifier);
        }
        return "`" + identifier + "`";
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "1" : "0";
    }

    @Override
    public String jsonColumnType() {
        return "JSON";
    }

    @Override
    public String uuidColumnType() {
        return "BINARY(16)";
    }
}

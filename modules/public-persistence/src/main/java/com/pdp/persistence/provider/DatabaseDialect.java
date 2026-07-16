package com.pdp.persistence.provider;

/**
 * 数据库方言的最小公共边界。专有 SQL 仍由具体适配器生成。
 */
public interface DatabaseDialect {

    String quoteIdentifier(String identifier);

    String booleanLiteral(boolean value);

    String jsonColumnType();

    String uuidColumnType();
}

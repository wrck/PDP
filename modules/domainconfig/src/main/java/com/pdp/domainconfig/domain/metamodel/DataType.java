package com.pdp.domainconfig.domain.metamodel;

/**
 * 字段数据类型（domain-package.schema.json fieldDefinition.dataType）。
 *
 * <p>核心字段规范化列存储；扩展字段使用版本化逻辑 JSON 与受控索引投影。
 */
public enum DataType {
    TEXT,
    LONG_TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    ENUM,
    USER_REF,
    ORG_REF,
    OBJECT_REF,
    FILE_REF,
    GEO_POINT,
    JSON
}

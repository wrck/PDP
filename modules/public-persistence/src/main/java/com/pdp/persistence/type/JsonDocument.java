package com.pdp.persistence.type;

import java.util.Objects;

/**
 * 数据库无关的 JSON 文档载体。适配器必须保持 NULL、对象和数组语义不变。
 */
public record JsonDocument(String value) {

    public JsonDocument {
        value = Objects.requireNonNull(value, "value").trim();
        if (!(value.startsWith("{") && value.endsWith("}"))
                && !(value.startsWith("[") && value.endsWith("]"))) {
            throw new IllegalArgumentException("JSON 文档必须是对象或数组");
        }
    }
}

package com.pdp.persistence.provider;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 数据库产品稳定标识。它是开放值对象，P2 增加产品时无需修改公共类型。
 */
public record DatabaseProduct(String stableKey) {

    private static final Pattern STABLE_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");

    public static final DatabaseProduct MYSQL = new DatabaseProduct("MYSQL");

    public DatabaseProduct {
        stableKey = Objects.requireNonNull(stableKey, "stableKey").trim().toUpperCase(Locale.ROOT);
        if (!STABLE_KEY.matcher(stableKey).matches()) {
            throw new IllegalArgumentException("数据库产品稳定标识格式非法: " + stableKey);
        }
    }
}

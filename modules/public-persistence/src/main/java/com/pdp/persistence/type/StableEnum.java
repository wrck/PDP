package com.pdp.persistence.type;

/**
 * 枚举持久化稳定键契约，禁止保存 ordinal。
 */
public interface StableEnum {

    String stableKey();
}

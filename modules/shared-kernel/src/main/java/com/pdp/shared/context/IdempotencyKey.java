package com.pdp.shared.context;

/**
 * 幂等键值对象。
 *
 * <p>对应 OpenAPI 头 {@code Idempotency-Key}，长度 16-128 字符。
 * 高风险写操作必须携带幂等键，防止重复提交生成重复业务结果。
 */
public record IdempotencyKey(String value) {

    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey 不能为空");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "IdempotencyKey 长度必须在 " + MIN_LENGTH + "-" + MAX_LENGTH + " 之间");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

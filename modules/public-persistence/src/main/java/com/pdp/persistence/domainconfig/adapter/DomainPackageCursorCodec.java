package com.pdp.persistence.domainconfig.adapter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 领域包治理游标编解码器（简化实现）。
 *
 * <p>P1 简化：游标为最终排序键（UUIDv7 {@code id}）的 Base64URL 编码字符串，
 * 不携带签名、过滤摘要或范围摘要。客户端不得解析；过滤条件变化时由调用方重新首页查询。
 *
 * <p>当 {@code lastId} 为 {@code null}（首页）时返回 {@code null}。
 */
public final class DomainPackageCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private DomainPackageCursorCodec() {
    }

    /** 将最终排序键编码为不透明游标；{@code null} 返回 {@code null}。 */
    public static String encode(UUID lastId) {
        if (lastId == null) {
            return null;
        }
        return ENCODER.encodeToString(lastId.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 解码游标为最终排序键；{@code null}/空返回 {@code null}（首页）。 */
    public static UUID decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = DECODER.decode(cursor);
            return UUID.fromString(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("游标格式无效或已损坏", e);
        }
    }
}

package com.pdp.shared.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 签名游标编解码器。
 *
 * <p>游标使用 Base64URL 编码和 HMAC-SHA256 签名，格式为 {@code <payload>.<signature>}。
 * 客户端不得解析或修改游标；签名校验失败、版本不匹配、过期或 queryType/filterDigest/scopeDigest
 * 变化时游标失效。
 */
public final class CursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec signingKey;
    private final ObjectMapper objectMapper;

    public CursorCodec(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("游标签名密钥必须至少 32 字节");
        }
        this.signingKey = new SecretKeySpec(secret, HMAC_ALGORITHM);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** 将游标载荷编码为不透明字符串。 */
    public String encode(CursorPayload payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            String payloadPart = URL_ENCODER.encodeToString(json);
            String signature = computeSignature(payloadPart);
            return payloadPart + SEPARATOR + signature;
        } catch (Exception e) {
            throw new IllegalStateException("游标编码失败", e);
        }
    }

    /** 解码并验证游标。验证失败抛出 {@link IllegalArgumentException}。 */
    public CursorPayload decode(String cursor, String expectedQueryType,
                                String expectedFilterDigest, String expectedScopeDigest) {
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("游标不能为空");
        }
        int sep = cursor.lastIndexOf(SEPARATOR);
        if (sep <= 0 || sep >= cursor.length() - 1) {
            throw new IllegalArgumentException("游标格式无效");
        }
        String payloadPart = cursor.substring(0, sep);
        String signature = cursor.substring(sep + 1);

        if (!constantTimeEquals(signature, computeSignature(payloadPart))) {
            throw new IllegalArgumentException("游标签名校验失败");
        }

        try {
            byte[] json = URL_DECODER.decode(payloadPart);
            CursorPayload payload = objectMapper.readValue(json, CursorPayload.class);

            if (payload.cursorVersion() != CursorPayload.CURRENT_VERSION) {
                throw new IllegalArgumentException("游标版本不匹配");
            }
            if (!payload.queryType().equals(expectedQueryType)) {
                throw new IllegalArgumentException("游标查询类型不匹配，旧游标已失效");
            }
            if (expectedFilterDigest != null && !expectedFilterDigest.equals(payload.filterDigest())) {
                throw new IllegalArgumentException("过滤条件已变化，旧游标已失效");
            }
            if (expectedScopeDigest != null && !expectedScopeDigest.equals(payload.scopeDigest())) {
                throw new IllegalArgumentException("权限范围已变化，旧游标已失效");
            }
            if (payload.isExpired(Instant.now())) {
                throw new IllegalArgumentException("游标已过期");
            }
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("游标解析失败", e);
        }
    }

    private String computeSignature(String payloadPart) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] sig = mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("游标签名计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

package com.pdp.experience.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 带明确过期时间和必需请求头的对象存储签名地址。 */
public record SignedObjectUrl(
        URI url,
        Method method,
        Instant expiresAt,
        Map<String, String> requiredHeaders) {

    public enum Method {
        GET,
        PUT
    }

    public SignedObjectUrl {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(expiresAt, "expiresAt");
        requiredHeaders = Map.copyOf(requiredHeaders == null ? Map.of() : requiredHeaders);
        if (!url.isAbsolute()) {
            throw new IllegalArgumentException("签名地址必须是绝对 URI");
        }
    }
}

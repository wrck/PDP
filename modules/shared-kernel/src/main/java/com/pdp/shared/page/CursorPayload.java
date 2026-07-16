package com.pdp.shared.page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 游标载荷。
 *
 * <p>外部 API 使用不透明、签名的 keyset cursor。游标载荷至少包含：
 * <ul>
 *   <li>游标版本和查询类型；</li>
 *   <li>规范化排序字段值、NULL 标记和 UUIDv7 唯一兜底键；</li>
 *   <li>正向或反向方向；</li>
 *   <li>过滤条件摘要、工作空间/权限范围摘要；</li>
 *   <li>签发时间和可选过期时间。</li>
 * </ul>
 *
 * <p>过滤条件、排序方式或权限范围改变时旧游标必须失效（通过 queryType/filterDigest/scopeDigest 比对）。
 * 游标不包含数据库类型、表名或原始 SQL。
 */
public record CursorPayload(
        int cursorVersion,
        String queryType,
        List<String> lastSortValues,
        List<Boolean> nullMarkers,
        UUID tiebreakerId,
        SortDirection direction,
        String filterDigest,
        String scopeDigest,
        Instant issuedAt,
        Instant expiresAt) {

    public static final int CURRENT_VERSION = 1;

    public CursorPayload {
        if (queryType == null || queryType.isBlank()) {
            throw new IllegalArgumentException("queryType 不能为空");
        }
        if (tiebreakerId == null) {
            throw new IllegalArgumentException("tiebreakerId 不能为 null（最终排序键 id）");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction 不能为 null");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("issuedAt 不能为 null");
        }
        lastSortValues = lastSortValues == null ? List.of() : List.copyOf(lastSortValues);
        nullMarkers = nullMarkers == null ? List.of() : List.copyOf(nullMarkers);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** 构造首页之后的游标载荷。 */
    public static CursorPayload after(
            String queryType,
            List<String> lastSortValues,
            List<Boolean> nullMarkers,
            UUID tiebreakerId,
            SortDirection direction,
            String filterDigest,
            String scopeDigest,
            Instant issuedAt,
            Instant expiresAt) {
        return new CursorPayload(CURRENT_VERSION, queryType, lastSortValues, nullMarkers,
                tiebreakerId, direction, filterDigest, scopeDigest, issuedAt, expiresAt);
    }
}

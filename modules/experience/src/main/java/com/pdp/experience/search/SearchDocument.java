package com.pdp.experience.search;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 权限过滤搜索投影中的最小公共文档，不承载权威业务状态。 */
public record SearchDocument(
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String title,
        String summary,
        Set<String> terms,
        Map<String, String> filters,
        long revision,
        Instant indexedAt) {

    public SearchDocument {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(indexedAt, "indexedAt");
        objectType = requireText(objectType, "objectType");
        title = requireText(title, "title");
        summary = summary == null ? "" : summary;
        terms = Set.copyOf(terms == null ? Set.of() : terms);
        filters = Map.copyOf(filters == null ? Map.of() : filters);
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不得为负数");
        }
    }

    public SearchObjectRef objectRef() {
        return new SearchObjectRef(workspaceId, objectType, objectId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.strip();
    }
}

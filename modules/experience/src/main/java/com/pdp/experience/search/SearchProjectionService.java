package com.pdp.experience.search;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 投影写入、权限撤销清理和查询时实时复核的统一入口。 */
public final class SearchProjectionService {
    public static final Duration REVOCATION_REMOVAL_SLA = Duration.ofSeconds(30);

    private final SearchProjectionPort projection;
    private final SearchAuthorizationPort authorization;
    private final SearchAnalyzer analyzer;
    private final Clock clock;

    public SearchProjectionService(
            SearchProjectionPort projection,
            SearchAuthorizationPort authorization,
            SearchAnalyzer analyzer,
            Clock clock) {
        this.projection = Objects.requireNonNull(projection);
        this.authorization = Objects.requireNonNull(authorization);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.clock = Objects.requireNonNull(clock);
    }

    public SearchDocument project(
            UUID workspaceId,
            String objectType,
            UUID objectId,
            String title,
            String summary,
            Map<String, String> filters,
            long revision) {
        Set<String> terms = analyzer.analyze(title + " " + (summary == null ? "" : summary));
        SearchDocument document = new SearchDocument(
                workspaceId,
                objectType,
                objectId,
                title,
                summary,
                terms,
                filters,
                revision,
                clock.instant());
        projection.upsert(document);
        return document;
    }

    public Instant revoke(SearchObjectRef objectRef) {
        projection.remove(objectRef);
        return clock.instant().plus(REVOCATION_REMOVAL_SLA);
    }

    public SearchPage search(
            UUID workspaceId,
            UUID actorId,
            String query,
            Map<String, String> filters,
            int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit 必须在 1 到 200 之间");
        }
        List<SearchDocument> visible = projection
                .findCandidates(workspaceId, analyzer.analyze(query), filters, limit + 1)
                .stream()
                .filter(document -> authorization.canOpen(actorId, document.objectRef()))
                .limit(limit)
                .toList();
        Instant indexedAt = visible.stream()
                .map(SearchDocument::indexedAt)
                .min(Instant::compareTo)
                .orElse(clock.instant());
        return new SearchPage(visible, indexedAt);
    }

    public record SearchPage(List<SearchDocument> items, Instant indexedAt) {
        public SearchPage {
            items = List.copyOf(items);
            Objects.requireNonNull(indexedAt);
        }
    }
}

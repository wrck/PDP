package com.pdp.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.experience.search.SearchAnalyzer;
import com.pdp.experience.search.SearchAuthorizationPort;
import com.pdp.experience.search.SearchDocument;
import com.pdp.experience.search.SearchObjectRef;
import com.pdp.experience.search.SearchProjectionPort;
import com.pdp.experience.search.SearchProjectionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchProjectionConsistencyTest {

    @Test
    void projectsWithinThirtySecondSlaAndRechecksCurrentPermissionOnEveryQuery() {
        Instant now = Instant.parse("2026-07-17T03:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryProjection projection = new InMemoryProjection();
        Set<UUID> revoked = new java.util.HashSet<>();
        SearchAuthorizationPort authorization =
                (actorId, objectRef) -> !revoked.contains(objectRef.objectId());
        SearchProjectionService service =
                new SearchProjectionService(projection, authorization, new SearchAnalyzer(), clock);

        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        SearchDocument document = service.project(
                workspaceId,
                "project",
                objectId,
                "网络设备割接",
                "核心交换机迁移",
                Map.of("status", "ACTIVE"),
                3);

        assertThat(document.indexedAt()).isEqualTo(now);
        assertThat(service.search(workspaceId, actorId, "设备割接", Map.of(), 20).items())
                .extracting(SearchDocument::objectId)
                .containsExactly(objectId);

        revoked.add(objectId);
        assertThat(service.search(workspaceId, actorId, "设备割接", Map.of(), 20).items())
                .isEmpty();

        Instant deadline = service.revoke(document.objectRef());
        assertThat(deadline).isEqualTo(now.plusSeconds(30));
        assertThat(projection.documents).isEmpty();
    }

    private static final class InMemoryProjection implements SearchProjectionPort {
        private final List<SearchDocument> documents = new ArrayList<>();

        @Override
        public void upsert(SearchDocument document) {
            remove(document.objectRef());
            documents.add(document);
        }

        @Override
        public void remove(SearchObjectRef objectRef) {
            documents.removeIf(document -> document.objectRef().equals(objectRef));
        }

        @Override
        public List<SearchDocument> findCandidates(
                UUID workspaceId,
                Set<String> terms,
                Map<String, String> filters,
                int limit) {
            return documents.stream()
                    .filter(document -> document.workspaceId().equals(workspaceId))
                    .filter(document -> document.terms().containsAll(terms))
                    .filter(document -> document.filters().entrySet().containsAll(filters.entrySet()))
                    .limit(limit)
                    .toList();
        }
    }
}

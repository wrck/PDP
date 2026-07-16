package com.pdp.experience.search;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SearchProjectionPort {

    void upsert(SearchDocument document);

    void remove(SearchObjectRef objectRef);

    List<SearchDocument> findCandidates(
            UUID workspaceId,
            Set<String> terms,
            Map<String, String> filters,
            int limit);
}

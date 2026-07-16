package com.pdp.experience.search;

import java.util.UUID;

@FunctionalInterface
public interface SearchAuthorizationPort {
    boolean canOpen(UUID actorId, SearchObjectRef objectRef);
}

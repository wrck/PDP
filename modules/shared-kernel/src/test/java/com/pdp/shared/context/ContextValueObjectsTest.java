package com.pdp.shared.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextValueObjectsTest {

    @Test
    void idempotencyKeyValidatesLength() {
        assertThat(IdempotencyKey.of("0123456789abcdef")).isNotNull();
        assertThatThrownBy(() -> IdempotencyKey.of("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKey.of("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKey.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workspaceIdRejectsNull() {
        assertThatThrownBy(() -> new WorkspaceId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorContextDefaultsEmptyPermissions() {
        ActorRef actor = ActorRef.user(java.util.UUID.randomUUID(), "张三");
        WorkspaceId ws = WorkspaceId.next();
        OperatorContext ctx = new OperatorContext(actor, ws, null, null);
        assertThat(ctx.grantedPermissions()).isEmpty();
        assertThat(ctx.hasPermission("any")).isFalse();
    }

    @Test
    void operatorContextHasAnyPermission() {
        ActorRef actor = ActorRef.user(java.util.UUID.randomUUID(), "张三");
        WorkspaceId ws = WorkspaceId.next();
        OperatorContext ctx = new OperatorContext(actor, ws,
                java.util.Set.of("project.read", "project.write"), java.util.Set.of("PM"));
        assertThat(ctx.hasPermission("project.read")).isTrue();
        assertThat(ctx.hasAnyPermission("project.delete", "project.write")).isTrue();
        assertThat(ctx.hasAnyPermission("project.delete", "admin.all")).isFalse();
    }

    @Test
    void requestContextThreadLocalLifecycle() {
        assertThat(RequestContext.getIfPresent()).isEmpty();
        ActorRef actor = ActorRef.user(java.util.UUID.randomUUID(), "张三");
        WorkspaceId ws = WorkspaceId.next();
        OperatorContext op = new OperatorContext(actor, ws, java.util.Set.of(), java.util.Set.of());
        TraceContext trace = TraceContext.next();
        RequestContext.set(new RequestContext(op, trace, null));
        try {
            assertThat(RequestContext.get().workspaceId()).isEqualTo(ws);
            assertThat(RequestContext.get().idempotencyKey()).isEmpty();
        } finally {
            RequestContext.clear();
        }
        assertThat(RequestContext.getIfPresent()).isEmpty();
    }
}

package com.pdp.shared.context;

import java.util.Optional;

/**
 * 请求上下文持有者。
 *
 * <p>聚合 {@link OperatorContext}、{@link TraceContext} 与可选 {@link IdempotencyKey}，
 * 通过 {@code RequestContextFilter}（apps/api）在请求开始时设置、结束时清除。
 * 领域服务与应用服务通过此上下文获取工作空间边界、操作者身份和链路标识，
 * 禁止从控制器参数逐层透传。
 *
 * <p>使用 ThreadLocal 实现；异步任务需显式快照与恢复（见后台作业协调器）。
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private final OperatorContext operator;
    private final TraceContext trace;
    private final IdempotencyKey idempotencyKey;

    public RequestContext(OperatorContext operator, TraceContext trace, IdempotencyKey idempotencyKey) {
        this.operator = operator;
        this.trace = trace;
        this.idempotencyKey = idempotencyKey;
    }

    public OperatorContext operator() {
        return operator;
    }

    public TraceContext trace() {
        return trace;
    }

    public Optional<IdempotencyKey> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    public WorkspaceId workspaceId() {
        return operator.workspaceId();
    }

    public static void set(RequestContext context) {
        HOLDER.set(context);
    }

    public static RequestContext get() {
        RequestContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException("当前线程未绑定 RequestContext");
        }
        return ctx;
    }

    public static Optional<RequestContext> getIfPresent() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}

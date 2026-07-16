package com.pdp.mysql.routing;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在线数据源严格路由守卫。迁移源、迁移目标和工作流数据源不得通过此守卫访问。
 */
public final class DataSourceRoutingGuard {

    public static final String PDP_PRIMARY = "pdpPrimary";
    public static final String PDP_READ = "pdpRead";
    public static final Set<String> ONLINE_KEYS = Set.of(PDP_PRIMARY, PDP_READ);

    private final Set<String> configuredKeys;
    private final ThreadLocal<Deque<String>> localRoutes = ThreadLocal.withInitial(ArrayDeque::new);

    public DataSourceRoutingGuard(Set<String> configuredKeys) {
        this.configuredKeys = Set.copyOf(Objects.requireNonNull(configuredKeys, "configuredKeys"));
        if (!this.configuredKeys.contains(PDP_PRIMARY)
                || !ONLINE_KEYS.containsAll(this.configuredKeys)) {
            throw new IllegalArgumentException("在线路由只能配置 pdpPrimary 和可选 pdpRead");
        }
    }

    public RoutingScope primary() {
        return open(PDP_PRIMARY, AccessMode.WRITE);
    }

    public RoutingScope readReplica() {
        return open(PDP_READ, AccessMode.READ);
    }

    /**
     * 为允许最终一致性的查询选择只读副本；未配置或健康检查失败时受控降级到主库。
     *
     * @param readReplicaHealthy 当前只读副本健康检查结果
     */
    public RoutingScope readPreferred(boolean readReplicaHealthy) {
        String routeKey =
                readReplicaHealthy && configuredKeys.contains(PDP_READ) ? PDP_READ : PDP_PRIMARY;
        return open(routeKey, AccessMode.READ);
    }

    public RoutingScope open(String routeKey, AccessMode accessMode) {
        routeKey = requireKnownRoute(routeKey);
        Objects.requireNonNull(accessMode, "accessMode");
        if (accessMode == AccessMode.WRITE && PDP_READ.equals(routeKey)) {
            throw new DataSourceRoutingException("pdpRead 禁止写入");
        }

        Deque<String> stack = localRoutes.get();
        String guardedCurrent = stack.peek();
        String frameworkCurrent = DynamicDataSourceContextHolder.peek();
        String current = guardedCurrent != null ? guardedCurrent : frameworkCurrent;
        if (current != null && !configuredKeys.contains(current)) {
            throw new DataSourceRoutingException("当前线程包含未授权数据源路由: " + current);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && current != null
                && !current.equals(routeKey)) {
            throw new DataSourceRoutingException(
                    "事务内禁止切换数据源: " + current + "→" + routeKey);
        }

        DynamicDataSourceContextHolder.push(routeKey);
        stack.push(routeKey);
        return new RoutingScope(this, routeKey, Thread.currentThread());
    }

    private String requireKnownRoute(String routeKey) {
        routeKey = Objects.requireNonNull(routeKey, "routeKey").trim();
        if (!configuredKeys.contains(routeKey)) {
            throw new DataSourceRoutingException("未知或未授权的数据源路由: " + routeKey);
        }
        return routeKey;
    }

    private void close(String routeKey, Thread owner) {
        if (Thread.currentThread() != owner) {
            throw new DataSourceRoutingException("数据源路由作用域不能跨线程关闭");
        }
        Deque<String> stack = localRoutes.get();
        if (!routeKey.equals(stack.peek())) {
            throw new DataSourceRoutingException("数据源路由作用域必须按后进先出顺序关闭");
        }
        stack.pop();
        DynamicDataSourceContextHolder.poll();
        if (stack.isEmpty()) {
            localRoutes.remove();
        }
    }

    public enum AccessMode {
        READ,
        WRITE
    }

    public static final class RoutingScope implements AutoCloseable {

        private final DataSourceRoutingGuard guard;
        private final String routeKey;
        private final Thread owner;
        private boolean closed;

        private RoutingScope(DataSourceRoutingGuard guard, String routeKey, Thread owner) {
            this.guard = guard;
            this.routeKey = routeKey;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (!closed) {
                guard.close(routeKey, owner);
                closed = true;
            }
        }
    }
}

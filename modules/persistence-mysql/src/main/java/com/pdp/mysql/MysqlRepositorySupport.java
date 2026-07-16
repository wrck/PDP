package com.pdp.mysql;

import com.pdp.mysql.routing.DataSourceRoutingGuard;
import java.util.Objects;

/**
 * MySQL 仓储适配器公共骨架，领域仓储端口的实现只能在此边界内复用。
 */
public abstract class MysqlRepositorySupport {

    private final DataSourceRoutingGuard routingGuard;
    private final MysqlDialectAdapter dialect;

    protected MysqlRepositorySupport(
            DataSourceRoutingGuard routingGuard, MysqlDialectAdapter dialect) {
        this.routingGuard = Objects.requireNonNull(routingGuard, "routingGuard");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    protected DataSourceRoutingGuard routingGuard() {
        return routingGuard;
    }

    protected MysqlDialectAdapter dialect() {
        return dialect;
    }
}

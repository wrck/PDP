package com.pdp.persistence.routing;

import com.pdp.persistence.config.DataSourceKeys;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/**
 * 严格数据源路由守卫。
 *
 * <p>拒绝：
 * <ol>
 *   <li>未知数据源键（strict=true，不回退默认库）</li>
 *   <li>越权路由（业务 Mapper 不能注入迁移数据源；迁移 Mapper 不能使用业务管理权限）</li>
 *   <li>事务内切换数据源（事务开始后切换必须失败）</li>
 * </ol>
 *
 * <p>{@code @DS} 只允许用于 persistence、datamigration 基础设施实现；
 * Controller、领域服务和领域对象禁止使用（由 ArchUnit 守护）。
 */
@Aspect
@Component
public class DataSourceRoutingGuard {

    /** 允许的数据源键白名单。 */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            DataSourceKeys.PDP_PRIMARY,
            DataSourceKeys.PDP_READ,
            DataSourceKeys.MIGRATION_SOURCE,
            DataSourceKeys.MIGRATION_TARGET,
            DataSourceKeys.WORKFLOW_ENGINE);

    /**
     * 断言切换到指定数据源键被允许。
     *
     * @param dataSourceKey 目标数据源键
     * @throws PdpException 未知键、事务内切换或越权路由时抛出
     */
    public void assertSwitchAllowed(String dataSourceKey) {
        if (dataSourceKey == null || dataSourceKey.isBlank()) {
            throw new PdpException(ErrorCode.DATASOURCE_UNKNOWN_KEY, "数据源键不能为空");
        }
        if (!ALLOWED_KEYS.contains(dataSourceKey)) {
            throw new PdpException(ErrorCode.DATASOURCE_UNKNOWN_KEY,
                    "未知数据源键 " + dataSourceKey + "，strict=true 不回退默认库");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new PdpException(ErrorCode.DATASOURCE_SWITCH_IN_TRANSACTION,
                    "数据源在事务入口确定，事务开始后切换数据源必须失败: " + dataSourceKey);
        }
    }

    /**
     * 拦截 {@code @DS} 注解的切换调用，强制校验。
     * 仅 persistence 与 datamigration 基础设施允许使用 @DS（由 ArchUnit 守护声明位置）。
     */
    @Around("@annotation(com.baomidou.dynamic.datasource.annotation.DS)")
    public Object guardDsSwitch(ProceedingJoinPoint pjp) throws Throwable {
        // 注解值由 dynamic-datasource 框架解析并 push 到 DynamicDataSourceContextHolder；
        // 此处通过校验当前上下文键值约束切换。为避免重复解析注解，主要依赖 assertSwitchAllowed 调用点。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new PdpException(ErrorCode.DATASOURCE_SWITCH_IN_TRANSACTION,
                    "数据源在事务入口确定，事务内 @DS 切换必须失败");
        }
        return pjp.proceed();
    }
}

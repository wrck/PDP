package com.pdp.domainconfig.domain.behavior;

/**
 * 受治理扩展失败策略（domain-package.schema.json extensionDefinition.failurePolicy）。
 *
 * <p>决定扩展调用失败时平台的处置方式，避免单个扩展影响整体业务流程稳定性。
 */
public enum ExtensionFailurePolicy {
    /** 立即失败：扩展失败立即中断当前业务流程并抛出错误。 */
    FAIL_FAST,
    /** 优雅降级：扩展失败时记录告警，业务流程继续以平台默认行为执行。 */
    DEGRADE_GRACEFULLY,
    /** 隔离：扩展连续失败超过阈值后自动隔离，不再调用直到人工干预。 */
    QUARANTINE
}

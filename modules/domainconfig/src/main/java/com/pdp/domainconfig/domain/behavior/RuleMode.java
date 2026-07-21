package com.pdp.domainconfig.domain.behavior;

/**
 * 规则执行模式（domain-package.schema.json ruleDefinition.mode）。
 *
 * <p>FR-167 确定性状态机：规则 MUST 显式声明执行模式，决定触发后是同步阻塞还是异步入队。
 */
public enum RuleMode {
    /** 同步执行：阻塞当前操作直到规则完成。 */
    SYNCHRONOUS,
    /** 异步执行：通过 Outbox 事件入队后立即返回。 */
    ASYNCHRONOUS
}

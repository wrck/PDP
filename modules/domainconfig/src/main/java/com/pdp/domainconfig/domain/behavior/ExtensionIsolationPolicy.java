package com.pdp.domainconfig.domain.behavior;

/**
 * 受治理扩展隔离策略（domain-package.schema.json extensionDefinition.isolationPolicy）。
 *
 * <p>FR-019 领域包扩展 MUST 在平台提供的隔离边界内运行，禁止直接访问数据库、
 * 平台保留 API 或其他领域包内部状态。
 */
public enum ExtensionIsolationPolicy {
    /** 严格沙箱：扩展运行于平台提供的隔离运行时，无网络、无文件系统、无数据库访问。 */
    STRICT_SANDBOX,
    /** 服务账号：扩展以领域包专属服务账号身份调用平台公开 API。 */
    SERVICE_ACCOUNT,
    /** 事件桥：扩展仅通过事件桥接收和发出事件，与平台运行时完全解耦。 */
    EVENT_BRIDGE
}

package com.pdp.domainconfig.domain.behavior;

/**
 * 规则执行身份（domain-package.schema.json ruleDefinition.executionIdentity）。
 *
 * <p>FR-167 确定性状态机：规则 MUST 显式声明执行身份。
 * <ul>
 *   <li>{@link #CURRENT_USER}：以触发用户身份执行，受其权限限制；</li>
 *   <li>{@link #PACKAGE_SERVICE_ACCOUNT}：以领域包专属服务账号执行，权限由
 *       {@link ExtensionDefinition#permissions()} 等显式声明，不允许提升当前用户权限。</li>
 * </ul>
 */
public enum ExecutionIdentity {
    CURRENT_USER,
    PACKAGE_SERVICE_ACCOUNT
}

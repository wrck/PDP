package com.pdp.domainconfig.domain.behavior;

import java.util.List;

/**
 * 受治理扩展定义（domain-package.schema.json extensionDefinition）。
 *
 * <p>FR-019 领域包扩展 MUST 在平台提供的隔离边界内运行；本定义声明扩展的制品、入口、
 * 权限白名单、资源限制、隔离策略与失败策略。
 *
 * <p>{@link #permissions} MUST 为最小权限集，由 {@code DomainPackageValidationService}
 * （T121）发布前校验权限越界（SC-013）；{@link #signature} 为扩展制品的签名，
 * 运行时校验失败将拒绝加载。
 */
public record ExtensionDefinition(
        String stableKey,
        String artifact,
        String entrypoint,
        List<String> permissions,
        int timeoutMs,
        int memoryMb,
        String signature,
        ExtensionIsolationPolicy isolationPolicy,
        ExtensionFailurePolicy failurePolicy) {

    public ExtensionDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (artifact == null || artifact.isBlank()) {
            throw new IllegalArgumentException("artifact 不能为空");
        }
        if (entrypoint == null || entrypoint.isBlank()) {
            throw new IllegalArgumentException("entrypoint 不能为空");
        }
        if (timeoutMs < 100 || timeoutMs > 30000) {
            throw new IllegalArgumentException("timeoutMs 必须在 [100, 30000] 区间");
        }
        if (memoryMb != 0 && (memoryMb < 16 || memoryMb > 1024)) {
            throw new IllegalArgumentException("memoryMb 必须在 [16, 1024] 区间或为 0（不限制）");
        }
        if (isolationPolicy == null) {
            isolationPolicy = ExtensionIsolationPolicy.STRICT_SANDBOX;
        }
        if (failurePolicy == null) {
            failurePolicy = ExtensionFailurePolicy.FAIL_FAST;
        }
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}

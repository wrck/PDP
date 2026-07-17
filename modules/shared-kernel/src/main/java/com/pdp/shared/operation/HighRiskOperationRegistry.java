package com.pdp.shared.operation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 高风险操作类型注册表（FR-168、SC-039、spec.md 末段）。
 *
 * <p>预注册所有 P1 高风险操作类型，并支持注册未来认证数据库切换操作类型。
 * P1 期间 {@link HighRiskOperationType#DATABASE_SWITCH} 返回稳定禁用原因
 * {@link DisabledReason#databaseSwitchP1Disabled()}，P2 启用后通过
 * {@link #enable(HighRiskOperationType)} 移除禁用原因。
 *
 * <p><strong>注册契约</strong>：
 * <ul>
 *   <li>注册表在应用启动时由配置类预注册所有 {@link HighRiskOperationType} 枚举值；</li>
 *   <li>{@link HighRiskOperationType#DATABASE_SWITCH} 默认禁用，返回 P1 禁用原因；</li>
 *   <li>未来新增类型（如 P2 认证数据库切换的具体子类型）通过 {@link #register} 注册；</li>
 *   <li>禁用原因通过 {@link #enable} 移除，对应阶段启用操作；</li>
 *   <li>注册表线程安全（读写锁保护），支持运行时动态注册（如领域包插件加载）。</li>
 * </ul>
 *
 * <p>本注册表为纯领域组件，不依赖 Spring；持久化由调用方负责。
 */
public final class HighRiskOperationRegistry {

    /** 已注册操作类型及其禁用原因（null 表示启用）。 */
    private final Map<HighRiskOperationType, DisabledReason> registrations =
            new LinkedHashMap<>();

    /**
     * 创建注册表并预注册所有枚举定义的操作类型。
     *
     * <p>P1 启用类型（{@link HighRiskOperationType#executableInP1} 返回 true）注册为启用；
     * {@link HighRiskOperationType#DATABASE_SWITCH} 注册为禁用，附带 P1 禁用原因。
     */
    public HighRiskOperationRegistry() {
        for (HighRiskOperationType type : HighRiskOperationType.values()) {
            if (type.executableInP1()) {
                registrations.put(type, null);
            } else {
                registrations.put(type, DisabledReason.databaseSwitchP1Disabled());
            }
        }
    }

    /**
     * 注册新操作类型（如未来 P2 认证数据库切换的具体子类型）。
     *
     * <p>已注册类型重复调用为幂等：若禁用原因相同则无操作，不同则抛出异常防止误覆盖。
     *
     * @param type           操作类型
     * @param disabledReason 禁用原因（null 表示启用）
     * @throws IllegalStateException 类型已注册且禁用原因不同
     */
    public synchronized void register(HighRiskOperationType type, DisabledReason disabledReason) {
        Objects.requireNonNull(type, "type 不能为空");
        DisabledReason existing = registrations.get(type);
        if (existing != null) {
            if (!existing.equals(disabledReason)) {
                throw new IllegalStateException(
                        "操作类型 " + type.stableKey() + " 已注册，禁用原因不同，禁止覆盖");
            }
            return;
        }
        if (disabledReason != null && registrations.containsKey(type)) {
            throw new IllegalStateException(
                    "操作类型 " + type.stableKey() + " 已启用，禁止降级为禁用");
        }
        registrations.put(type, disabledReason);
    }

    /**
     * 启用操作类型（移除禁用原因）。
     *
     * <p>对应阶段升级（如 P1→P2 启用 DATABASE_SWITCH）。
     *
     * @param type 操作类型
     * @throws IllegalArgumentException 类型未注册
     */
    public synchronized void enable(HighRiskOperationType type) {
        Objects.requireNonNull(type, "type 不能为空");
        if (!registrations.containsKey(type)) {
            throw new IllegalArgumentException("操作类型 " + type.stableKey() + " 未注册");
        }
        registrations.put(type, null);
    }

    /**
     * 查询操作类型是否启用。
     *
     * @param type 操作类型
     * @return true 表示启用（无禁用原因）
     * @throws IllegalArgumentException 类型未注册
     */
    public synchronized boolean isEnabled(HighRiskOperationType type) {
        Objects.requireNonNull(type, "type 不能为空");
        DisabledReason reason = registrations.get(type);
        if (reason == null && !registrations.containsKey(type)) {
            throw new IllegalArgumentException("操作类型 " + type.stableKey() + " 未注册");
        }
        return reason == null;
    }

    /**
     * 查询操作类型的禁用原因。
     *
     * @param type 操作类型
     * @return 禁用原因（启用时返回 empty）
     * @throws IllegalArgumentException 类型未注册
     */
    public synchronized Optional<DisabledReason> disabledReason(HighRiskOperationType type) {
        Objects.requireNonNull(type, "type 不能为空");
        if (!registrations.containsKey(type)) {
            throw new IllegalArgumentException("操作类型 " + type.stableKey() + " 未注册");
        }
        return Optional.ofNullable(registrations.get(type));
    }

    /**
     * 列出所有已注册操作类型。
     *
     * @return 不可变集合
     */
    public synchronized Collection<HighRiskOperationType> registeredTypes() {
        return Collections.unmodifiableCollection(registrations.keySet());
    }

    /**
     * 列出所有禁用操作类型及其禁用原因。
     *
     * @return 不可变映射（操作类型 → 禁用原因）
     */
    public synchronized Map<HighRiskOperationType, DisabledReason> disabledTypes() {
        Map<HighRiskOperationType, DisabledReason> result = new LinkedHashMap<>();
        registrations.forEach((type, reason) -> {
            if (reason != null) {
                result.put(type, reason);
            }
        });
        return Collections.unmodifiableMap(result);
    }
}

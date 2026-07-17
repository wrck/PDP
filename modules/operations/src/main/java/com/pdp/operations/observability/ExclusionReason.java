package com.pdp.operations.observability;

/**
 * FR-165 请求排除原因分类（稳定键）。
 *
 * <p>对应 FR-165："客户端主动取消以及客户控制的网络或身份系统故障可排除，
 * PDP 应用、数据库、对象存储和内部依赖故障不得排除，割接保障窗口和未批准维护
 * 不得从核心可用性中排除。"
 *
 * <p><strong>可排除</strong>（不计入可用性分母）：
 * <ul>
 *   <li>{@link #CLIENT_CANCELLED}：客户端主动取消；</li>
 *   <li>{@link #CUSTOMER_NETWORK_FAILURE}：客户控制的网络故障；</li>
 *   <li>{@link #CUSTOMER_IDENTITY_FAILURE}：客户控制的身份系统故障。</li>
 * </ul>
 *
 * <p><strong>不可排除</strong>（计入可用性分母，失败时降低可用性）：
 * <ul>
 *   <li>{@link #PDP_APPLICATION_FAILURE}：PDP 应用故障；</li>
 *   <li>{@link #DATABASE_FAILURE}：数据库故障；</li>
 *   <li>{@link #OBJECT_STORAGE_FAILURE}：对象存储故障；</li>
 *   <li>{@link #INTERNAL_DEPENDENCY_FAILURE}：内部依赖故障；</li>
 *   <li>{@link #UNAPPROVED_MAINTENANCE}：未批准维护（核心可用性不排除）；</li>
 *   <li>{@link #CUTOVER_GUARANTEE_WINDOW}：割接保障窗口故障（核心可用性不排除）。</li>
 * </ul>
 *
 * <p><strong>无排除</strong>：{@link #NONE} 表示正常请求，不排除。
 */
public enum ExclusionReason {

    /** 无排除（正常请求，计入可用性）。 */
    NONE("NONE", true),

    // ===== 可排除（不计入可用性分母） =====

    /** 客户端主动取消（可排除）。 */
    CLIENT_CANCELLED("CLIENT_CANCELLED", true),

    /** 客户控制的网络故障（可排除）。 */
    CUSTOMER_NETWORK_FAILURE("CUSTOMER_NETWORK_FAILURE", true),

    /** 客户控制的身份系统故障（可排除）。 */
    CUSTOMER_IDENTITY_FAILURE("CUSTOMER_IDENTITY_FAILURE", true),

    // ===== 不可排除（计入可用性分母，失败时降低可用性） =====

    /** PDP 应用故障（不可排除）。 */
    PDP_APPLICATION_FAILURE("PDP_APPLICATION_FAILURE", false),

    /** 数据库故障（不可排除）。 */
    DATABASE_FAILURE("DATABASE_FAILURE", false),

    /** 对象存储故障（不可排除）。 */
    OBJECT_STORAGE_FAILURE("OBJECT_STORAGE_FAILURE", false),

    /** 内部依赖故障（不可排除）。 */
    INTERNAL_DEPENDENCY_FAILURE("INTERNAL_DEPENDENCY_FAILURE", false),

    /** 未批准维护（核心可用性不排除）。 */
    UNAPPROVED_MAINTENANCE("UNAPPROVED_MAINTENANCE", false),

    /** 割接保障窗口故障（核心可用性不排除）。 */
    CUTOVER_GUARANTEE_WINDOW("CUTOVER_GUARANTEE_WINDOW", false);

    private final String stableKey;
    private final boolean excludable;

    ExclusionReason(String stableKey, boolean excludable) {
        this.stableKey = stableKey;
        this.excludable = excludable;
    }

    public String stableKey() {
        return stableKey;
    }

    /** 是否可排除（不计入可用性分母，FR-165）。 */
    public boolean isExcludable() {
        return excludable;
    }

    /** 是否需要排除证据（SC-037：排除项证据完整率 100%）。 */
    public boolean requiresEvidence() {
        return this != NONE;
    }

    public static ExclusionReason fromStableKey(String stableKey) {
        for (ExclusionReason r : values()) {
            if (r.stableKey.equals(stableKey)) {
                return r;
            }
        }
        throw new IllegalArgumentException("未知排除原因稳定键: " + stableKey);
    }
}

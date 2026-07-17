package com.pdp.experience.storage;

/**
 * 文件扫描状态枚举（状态机，稳定键）。
 *
 * <p>对应数据模型 {@code FileVersion} 状态机：
 * {@code UPLOADING → SCANNING → AVAILABLE → QUARANTINED/ARCHIVED/DISPOSED}。
 *
 * <p>状态迁移规则：
 * <ul>
 *   <li>{@link #UPLOADING} → {@link #SCANNING}：上传完成，触发病毒扫描；</li>
 *   <li>{@link #SCANNING} → {@link #AVAILABLE}：扫描通过，文件可访问；</li>
 *   <li>{@link #SCANNING} → {@link #QUARANTINED}：扫描发现威胁，隔离；</li>
 *   <li>{@link #AVAILABLE} → {@link #ARCHIVED}：到达保留期归档；</li>
 *   <li>{@link #AVAILABLE}/{@link #QUARANTINED} → {@link #DISPOSED}：处置（受法律保留约束）。</li>
 * </ul>
 *
 * <p><strong>不变量</strong>（FR-071）：交付件、审批、签核和关键配置在保留期限前不得不可逆删除。
 * {@link #DISPOSED} 为终态，需通过处置审批和法律保留检查。
 */
public enum FileScanStatus {

    /** 上传中：文件正在上传到对象存储。 */
    UPLOADING("UPLOADING"),
    /** 扫描中：上传完成，正在执行病毒扫描。 */
    SCANNING("SCANNING"),
    /** 可用：扫描通过，文件可访问（仍需权限复核）。 */
    AVAILABLE("AVAILABLE"),
    /** 已隔离：扫描发现威胁或异常，禁止访问。 */
    QUARANTINED("QUARANTINED"),
    /** 已归档：到达保留期，冷存储。 */
    ARCHIVED("ARCHIVED"),
    /** 已处置：永久删除（受法律保留约束，FR-071）。 */
    DISPOSED("DISPOSED");

    private final String stableKey;

    FileScanStatus(String stableKey) {
        this.stableKey = stableKey;
    }

    public String stableKey() {
        return stableKey;
    }

    public static FileScanStatus fromStableKey(String stableKey) {
        for (FileScanStatus s : values()) {
            if (s.stableKey.equals(stableKey)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知文件扫描状态稳定键: " + stableKey);
    }

    /** 是否为终态。 */
    public boolean isTerminal() {
        return this == DISPOSED;
    }

    /** 是否可访问（用于下载权限复核前的预检查）。 */
    public boolean isAccessible() {
        return this == AVAILABLE;
    }

    /** 是否可迁移到扫描完成（AVAILABLE）。 */
    public boolean canBecomeAvailable() {
        return this == SCANNING;
    }

    /** 是否可隔离。 */
    public boolean canBeQuarantined() {
        return this == SCANNING || this == AVAILABLE;
    }

    /**
     * 校验状态迁移合法性。
     */
    public boolean canTransitionTo(FileScanStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case UPLOADING -> target == SCANNING;
            case SCANNING -> target == AVAILABLE || target == QUARANTINED;
            case AVAILABLE -> target == ARCHIVED || target == DISPOSED || target == QUARANTINED;
            case QUARANTINED -> target == DISPOSED || target == AVAILABLE; // 误报可释放
            case ARCHIVED -> target == DISPOSED || target == AVAILABLE; // 恢复访问
            case DISPOSED -> false; // 终态
        };
    }
}

package com.pdp.operations.job;

/**
 * 后台作业状态枚举（状态机，稳定键）。
 *
 * <p>对应 spec.md 状态机表"后台作业"行：排队中 → 运行中 → 已完成；可暂停、失败或取消。
 * 使用稳定字符串键持久化，禁止依赖枚举序号。
 *
 * <p>状态迁移规则（前置条件：幂等键、执行身份、范围、资源预算和检查点有效）：
 * <ul>
 *   <li>{@link #QUEUED} → {@link #RUNNING}：调度器拾取并校验资源预算通过后启动；</li>
 *   <li>{@link #RUNNING} → {@link #PAUSED}：手动暂停或资源预算超限自动暂停，保留检查点；</li>
 *   <li>{@link #PAUSED} → {@link #RUNNING}：手动恢复，从检查点继续；</li>
 *   <li>{@link #RUNNING} → {@link #COMPLETED}：处理全部条目且无致命失败；</li>
 *   <li>{@link #RUNNING} → {@link #FAILED}：致命错误或失败条目超阈值；保留检查点和失败明细，可安全重试；</li>
 *   <li>{@link #QUEUED}/{@link #RUNNING}/{@link #PAUSED} → {@link #CANCELLED}：手动取消；</li>
 *   <li>{@link #FAILED} → {@link #QUEUED}：人工重试，从检查点继续（新 revision）。</li>
 * </ul>
 *
 * <p>失败处理（spec.md）："保留检查点、失败明细和可安全重试/人工补偿入口"。
 * 终态：{@link #COMPLETED}、{@link #FAILED}、{@link #CANCELLED}。
 */
public enum BackgroundJobStatus {

    /** 排队中：已提交，等待调度器拾取。 */
    QUEUED("QUEUED"),
    /** 运行中：调度器已分派，正在执行。 */
    RUNNING("RUNNING"),
    /** 已暂停：手动或自动暂停，保留检查点，可恢复。 */
    PAUSED("PAUSED"),
    /** 已完成：全部条目处理成功。 */
    COMPLETED("COMPLETED"),
    /** 已失败：致命错误或失败条目超阈值，保留检查点和失败明细，可安全重试。 */
    FAILED("FAILED"),
    /** 已取消：手动取消，不可恢复（需重新提交）。 */
    CANCELLED("CANCELLED");

    private final String stableKey;

    BackgroundJobStatus(String stableKey) {
        this.stableKey = stableKey;
    }

    public String stableKey() {
        return stableKey;
    }

    public static BackgroundJobStatus fromStableKey(String stableKey) {
        for (BackgroundJobStatus s : values()) {
            if (s.stableKey.equals(stableKey)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知后台作业状态稳定键: " + stableKey);
    }

    /** 是否为终态（不可再迁移）。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /** 是否可暂停。 */
    public boolean isPausable() {
        return this == RUNNING;
    }

    /** 是否可恢复。 */
    public boolean isResumable() {
        return this == PAUSED;
    }

    /** 是否可取消。 */
    public boolean isCancellable() {
        return this == QUEUED || this == RUNNING || this == PAUSED;
    }

    /** 是否可重试（从检查点继续）。 */
    public boolean isRetryable() {
        return this == FAILED;
    }

    /**
     * 校验状态迁移合法性。
     *
     * @param target 目标状态
     * @return true 表示迁移合法
     */
    public boolean canTransitionTo(BackgroundJobStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == PAUSED || target == COMPLETED
                    || target == FAILED || target == CANCELLED;
            case PAUSED -> target == RUNNING || target == CANCELLED;
            case FAILED -> target == QUEUED; // 人工重试
            case COMPLETED, CANCELLED -> false; // 终态
        };
    }
}

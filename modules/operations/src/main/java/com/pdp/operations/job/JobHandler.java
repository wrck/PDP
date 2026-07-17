package com.pdp.operations.job;

/**
 * 后台作业处理器接口。
 *
 * <p>每种 {@link BackgroundJobType} 对应一个 {@link JobHandler} 实现，由 Spring 注册为 bean，
 * {@link BackgroundJobCoordinator} 在调度时按 {@link #supportedType()} 分派。
 *
 * <p><strong>实现契约</strong>（spec.md 状态机表"后台作业"行）：
 * <ol>
 *   <li>实现 MUST 幂等：相同检查点重复执行不产生重复业务结果（如重复导入、重复索引更新）；</li>
 *   <li>实现 MUST 周期性持久化检查点（通过 {@link JobContext#saveCheckpoint}），失败或暂停时可从检查点恢复；</li>
 *   <li>实现 MUST 收集失败明细（通过 {@link JobContext#recordFailure}），单个条目失败不应中断整体作业
 *       （除非致命错误）；</li>
 *   <li>实现 MUST 响应取消请求（通过 {@link JobContext#isCancelRequested}），及时返回
 *       {@link JobExecutionResult#cancelled}；</li>
 *   <li>实现 MUST 尊重资源预算，超时或资源超限时抛出 {@link JobExecutionException} 触发暂停或失败。</li>
 * </ol>
 *
 * <p>实现 SHOULD 为无状态（状态由 {@link JobContext} 和检查点携带），线程安全。
 */
public interface JobHandler {

    /**
     * 此处理器支持的作业类型。
     *
     * @return 作业类型
     */
    BackgroundJobType supportedType();

    /**
     * 执行作业。
     *
     * <p>由 {@link BackgroundJobCoordinator} 在作业状态转为 RUNNING 后调用。实现通过
     * {@link JobContext} 读取检查点、报告进度、收集失败和响应取消。
     *
     * @param context 作业执行上下文
     * @return 执行结果
     * @throws JobExecutionException 致命错误（触发作业 FAILED 状态）
     */
    JobExecutionResult execute(JobContext context);
}

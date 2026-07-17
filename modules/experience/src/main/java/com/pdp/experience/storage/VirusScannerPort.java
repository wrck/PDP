package com.pdp.experience.storage;

/**
 * 病毒扫描端口（六边形架构出站端口）。
 *
 * <p>对应 spec.md 安全基线和 FR-164：所有上传文件 MUST 经过病毒扫描才能迁移到 AVAILABLE 状态。
 * 实现负责对接 ClamAV 或其他扫描引擎，业务模块通过此端口触发扫描，不依赖具体 SDK。
 *
 * <p><strong>扫描时机</strong>：文件上传到对象存储后（状态 SCANNING）异步触发扫描；
 * 扫描完成后根据 {@link ScanResult} 迁移状态：
 * <ul>
 *   <li>{@link ScanResult.Verdict#CLEAN} → {@link FileScanStatus#AVAILABLE}；</li>
 *   <li>{@link ScanResult.Verdict#INFECTED}/{@link ScanResult.Verdict#SUSPICIOUS} →
 *       {@link FileScanStatus#QUARANTINED}；</li>
 *   <li>{@link ScanResult.Verdict#SCAN_ERROR} → 保持 SCANNING，由重试机制处理。</li>
 * </ul>
 *
 * <p><strong>幂等性</strong>：相同对象引用重复扫描 MUST 返回相同结论（除非病毒库更新）。
 * 实现可缓存扫描结论，但缓存失效或病毒库更新后 MUST 重新扫描。
 *
 * <p><strong>性能</strong>：扫描可能耗时较长（大文件可达数秒），建议异步执行并回调状态迁移。
 */
public interface VirusScannerPort {

    /**
     * 扫描对象存储中的文件。
     *
     * <p>实现从对象存储拉取文件流并扫描，不依赖业务模块传递内容。
     *
     * @param ref 对象引用（文件已上传到对象存储）
     * @return 扫描结果
     * @throws StorageException 扫描器不可用（{@link StorageException.Reason#SCAN_UNAVAILABLE}）
     */
    ScanResult scan(StorageObjectRef ref);

    /**
     * 扫描器标识（如 {@code clamav-1.4.0}）。
     *
     * <p>用于审计和兼容性追踪。病毒库更新后扫描器版本可能变化。
     *
     * @return 扫描器标识
     */
    String scannerName();

    /**
     * 扫描器是否健康（用于就绪检查和降级判断）。
     *
     * <p>扫描器不可用时，文件上传流程应降级为：保留 SCANNING 状态，等待扫描器恢复后批量扫描。
     * 不允许跳过扫描直接迁移到 AVAILABLE（违反安全基线）。
     *
     * @return true=扫描器可用
     */
    boolean isHealthy();
}

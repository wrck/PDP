package com.pdp.experience.storage;

import com.pdp.shared.context.WorkspaceId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件元数据持久化端口（六边形架构出站端口）。
 *
 * <p>对应数据模型 {@code FileVersion}：对象存储键、版本号、大小、媒体类型、内容哈希、
 * 病毒扫描状态、上传人和时间。本端口屏蔽 MyBatis/MySQL 专有类型，业务模块通过此端口操作文件元数据。
 *
 * <p>由 {@code public-persistence} 基础设施适配器实现。乐观锁基于 {@code revision} 字段，
 * 状态迁移 MUST 通过 {@link #updateScanStatus} 在期望版本匹配时更新。
 *
 * <p><strong>多租户隔离</strong>：所有查询 MUST 按 {@link WorkspaceId} 过滤，禁止跨工作空间访问。
 */
public interface FileMetadataRepository {

    /**
     * 保存文件元数据（新建）。
     *
     * @param metadata 文件元数据
     * @param status   初始扫描状态（通常为 {@link FileScanStatus#UPLOADING}）
     * @param revision 初始乐观锁版本（通常为 0）
     */
    void save(FileMetadata metadata, FileScanStatus status, int revision);

    /**
     * 按对象引用查询文件元数据。
     *
     * @param objectRef 对象存储引用
     * @return 文件元数据记录（含扫描状态和乐观锁版本），不存在返回 empty
     */
    Optional<FileMetadataRecord> findByObjectRef(StorageObjectRef objectRef);

    /**
     * 按业务对象查询所有文件版本（用于历史和审计）。
     *
     * @param workspaceId 工作空间
     * @param objectType   业务对象类型稳定键（如 {@code deliverable}）
     * @param objectId     业务对象 ID
     * @return 文件元数据记录列表（按版本号降序）
     */
    List<FileMetadataRecord> findByBusinessObject(WorkspaceId workspaceId,
                                                   String objectType,
                                                   UUID objectId);

    /**
     * 查询工作空间内指定扫描状态的文件（用于扫描器恢复和审计）。
     *
     * @param workspaceId 工作空间（null 表示所有工作空间，仅限运维场景）
     * @param status      扫描状态
     * @param limit       最大返回数
     * @return 文件元数据记录列表
     */
    List<FileMetadataRecord> findByScanStatus(WorkspaceId workspaceId,
                                              FileScanStatus status,
                                              int limit);

    /**
     * 更新文件扫描状态（乐观锁）。
     *
     * <p>状态迁移 MUST 通过此方法，校验当前状态可迁移到目标状态（{@link FileScanStatus#canTransitionTo}）。
     * 实现负责在 SQL 中校验 {@code expectedRevision}，不匹配时返回 false。
     *
     * @param objectRef      对象引用
     * @param from           期望的当前状态（防止并发覆盖）
     * @param to             目标状态
     * @param expectedRevision 期望的乐观锁版本
     * @return true=更新成功；false=版本冲突或状态不匹配
     */
    boolean updateScanStatus(StorageObjectRef objectRef,
                             FileScanStatus from,
                             FileScanStatus to,
                             int expectedRevision);

    /**
     * 更新文件元数据（版本号、大小、哈希等，乐观锁）。
     *
     * @param objectRef      对象引用
     * @param metadata       新元数据
     * @param expectedRevision 期望的乐观锁版本
     * @return true=更新成功；false=版本冲突
     */
    boolean updateMetadata(StorageObjectRef objectRef,
                           FileMetadata metadata,
                           int expectedRevision);

    /**
     * 统计工作空间内指定扫描状态的文件数（用于监控和 SLA）。
     *
     * @param workspaceId 工作空间
     * @param status      扫描状态
     * @return 文件数
     */
    long countByScanStatus(WorkspaceId workspaceId, FileScanStatus status);

    /**
     * 文件元数据记录（含扫描状态和乐观锁版本）。
     *
     * @param metadata   文件元数据
     * @param scanStatus 当前扫描状态
     * @param revision   当前乐观锁版本
     * @param uploadedBy 上传人
     * @param uploadedAt 上传时间
     * @param scannedAt  扫描完成时间（未扫描为 null）
     * @param scanResultJson 扫描结果 JSON（用于审计，可为空）
     */
    record FileMetadataRecord(
            FileMetadata metadata,
            FileScanStatus scanStatus,
            int revision,
            com.pdp.shared.context.ActorRef uploadedBy,
            java.time.Instant uploadedAt,
            java.time.Instant scannedAt,
            String scanResultJson) {
    }
}

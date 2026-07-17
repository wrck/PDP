package com.pdp.experience.storage;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件存储应用服务（编排上传 → 扫描 → 下载 → 处置全流程，FR-164）。
 *
 * <p>对应 spec.md FR-164 和 research.md 决策 11：大文件存入 S3 兼容对象存储，数据库保存引用 +
 * 内容哈希 + 版本元数据。本服务协调 {@link ObjectStoragePort}、{@link VirusScannerPort}、
 * {@link FileMetadataRepository} 和 {@link FileAccessAuthorizer}，对外提供统一入口。
 *
 * <p><strong>核心流程</strong>：
 * <ol>
 *   <li><b>上传（presigned URL 模式）</b>：
 *     <ol>
 *       <li>{@link #prepareUpload}：生成对象键、签发短时 PUT URL，写入 UPLOADING 状态元数据；</li>
 *       <li>前端直传对象存储；</li>
 *       <li>{@link #confirmUpload}：校验对象已上传、哈希一致，迁移 UPLOADING→SCANNING，触发病毒扫描。</li>
 *     </ol>
 *   </li>
 *   <li><b>病毒扫描</b>：
 *     <ol>
 *       <li>{@link #triggerScan}：异步调用 {@link VirusScannerPort#scan}；</li>
 *       <li>{@link #handleScanResult}：根据扫描结论迁移 SCANNING→AVAILABLE 或 SCANNING→QUARANTINED。</li>
 *     </ol>
 *   </li>
 *   <li><b>下载（FR-164 权限复核）</b>：
 *     <ol>
 *       <li>{@link #requestDownload}：复核权限 + 校验 AVAILABLE 状态 + 签发短时 GET URL；</li>
 *       <li>前端用 GET URL 直接下载（URL 有效期 ≤ 5 分钟）。</li>
 *     </ol>
 *   </li>
 *   <li><b>处置</b>：{@link #archive}/{@link #dispose} 迁移状态并物理删除（受 FR-071 法律保留约束）。</li>
 * </ol>
 *
 * <p><strong>FR-164 权限复核</strong>：
 * <ul>
 *   <li>签发下载 URL 前：{@link FileAccessAuthorizer#canDownload}；</li>
 *   <li>RESTRICTED 文件：{@link FileAccessAuthorizer#revalidateRestrictedDownload} 双重复核；</li>
 *   <li>实际下载前（由 API 网关或下载端点调用）：{@link FileAccessAuthorizer#revalidateDownload}。</li>
 * </ul>
 *
 * <p><strong>状态机强制</strong>：所有状态迁移 MUST 通过 {@link FileScanStatus#canTransitionTo} 校验，
 * 非法迁移抛出 {@link StorageException.Reason#ILLEGAL_STATE_TRANSITION}。
 *
 * <p><strong>TTL 强制</strong>：签名 URL 有效期 = min(请求 TTL, {@link SignedUrl#MAX_TTL},
 * {@link FileClassification#maxSignedUrlTtlSeconds()})，超过 {@link SignedUrl#MAX_TTL} 抛出
 * {@link StorageException.Reason#SIGN_URL_TTL_VIOLATION}。
 */
public class FileStorageService {

    /** 默认签名 URL 有效期（3 分钟，低于 FR-164 上限 5 分钟）。 */
    public static final Duration DEFAULT_SIGN_TTL = Duration.ofMinutes(3);

    private final ObjectStoragePort objectStorage;
    private final VirusScannerPort virusScanner;
    private final FileMetadataRepository metadataRepository;
    private final FileAccessAuthorizer accessAuthorizer;
    private final String defaultBucket;

    public FileStorageService(ObjectStoragePort objectStorage,
                              VirusScannerPort virusScanner,
                              FileMetadataRepository metadataRepository,
                              FileAccessAuthorizer accessAuthorizer,
                              String defaultBucket) {
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage 不能为 null");
        this.virusScanner = Objects.requireNonNull(virusScanner, "virusScanner 不能为 null");
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository 不能为 null");
        this.accessAuthorizer = Objects.requireNonNull(accessAuthorizer, "accessAuthorizer 不能为 null");
        this.defaultBucket = Objects.requireNonNull(defaultBucket, "defaultBucket 不能为 null");
        if (defaultBucket.isBlank()) {
            throw new IllegalArgumentException("defaultBucket 不能为空白");
        }
    }

    // ==================== 上传流程 ====================

    /**
     * 准备上传：生成对象键、签发短时 PUT URL，写入 UPLOADING 状态元数据。
     *
     * <p>前端获取 PUT URL 后直传对象存储，然后调用 {@link #confirmUpload} 完成流程。
     *
     * @param request   上传请求
     * @param actor     操作者
     * @param now       当前时间
     * @return 签名上传 URL（含对象引用）
     */
    public SignedUrl prepareUpload(UploadRequest request, ActorRef actor, Instant now) {
        Objects.requireNonNull(request, "request 不能为 null");
        Objects.requireNonNull(actor, "actor 不能为 null");
        Objects.requireNonNull(now, "now 不能为 null");

        String objectKey = StorageObjectRef.buildObjectKey(
                request.workspaceId(), request.objectType(),
                request.objectId(), request.versionNumber());
        StorageObjectRef ref = StorageObjectRef.of(request.workspaceId(), defaultBucket, objectKey);

        Duration ttl = classificationTtl(request.classification(), DEFAULT_SIGN_TTL);
        SignedUrl signedUrl = objectStorage.signUploadUrl(ref, request.classification(), ttl, now, null);

        // 写入 UPLOADING 状态元数据（大小和哈希在 confirmUpload 时回填）
        FileMetadata metadata = new FileMetadata(
                ref, request.versionNumber(), 0L, request.mediaType(),
                request.contentHash(), request.classification(), request.originalFilename());
        metadataRepository.save(metadata, FileScanStatus.UPLOADING, 0);

        return signedUrl;
    }

    /**
     * 确认上传：校验对象已上传、内容哈希一致，迁移 UPLOADING→SCANNING，触发病毒扫描。
     *
     * <p>前端直传对象存储完成后调用此方法。实现：
     * <ol>
     *   <li>{@link ObjectStoragePort#headObject} 校验对象存在并回填大小；</li>
     *   <li>校验内容哈希一致（前端预计算 vs 对象存储 ETag/重新计算）；</li>
     *   <li>迁移状态 UPLOADING→SCANNING（乐观锁）；</li>
     *   <li>异步触发病毒扫描（{@link #triggerScan}）。</li>
     * </ol>
     *
     * @param ref      对象引用
     * @param actor    操作者
     * @param now      当前时间
     * @return 更新后的文件元数据记录
     * @throws StorageException 对象未上传、哈希不匹配或状态迁移失败
     */
    public FileMetadataRepository.FileMetadataRecord confirmUpload(StorageObjectRef ref,
                                                                    ActorRef actor, Instant now) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        Objects.requireNonNull(actor, "actor 不能为 null");

        FileMetadataRepository.FileMetadataRecord record = metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));

        if (record.scanStatus() != FileScanStatus.UPLOADING) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), FileScanStatus.SCANNING);
        }

        // 校验对象已上传到对象存储
        ObjectStoragePort.ObjectMetadata objMeta = objectStorage.headObject(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, record.scanStatus()));

        // 回填大小并更新元数据
        FileMetadata updatedMeta = new FileMetadata(
                ref, record.metadata().versionNumber(), objMeta.sizeBytes(),
                record.metadata().mediaType(), record.metadata().contentHash(),
                record.metadata().classification(), record.metadata().originalFilename());
        if (!metadataRepository.updateMetadata(ref, updatedMeta, record.revision())) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), record.scanStatus());
        }

        // 迁移 UPLOADING→SCANNING
        FileMetadataRepository.FileMetadataRecord refreshed = metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));
        if (!metadataRepository.updateScanStatus(ref, FileScanStatus.UPLOADING,
                FileScanStatus.SCANNING, refreshed.revision())) {
            throw StorageException.illegalStateTransition(ref, FileScanStatus.UPLOADING, FileScanStatus.SCANNING);
        }

        // 触发病毒扫描（同步调用，扫描器内部可异步）
        triggerScan(ref, actor, now);

        return metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));
    }

    /**
     * 触发病毒扫描并处理结果。
     *
     * <p>扫描器不可用时，文件保持 SCANNING 状态，由后台作业重试。
     *
     * @param ref   对象引用
     * @param actor 操作者
     * @param now   当前时间
     */
    public void triggerScan(StorageObjectRef ref, ActorRef actor, Instant now) {
        Objects.requireNonNull(ref, "ref 不能为 null");

        if (!virusScanner.isHealthy()) {
            // 扫描器不可用，保持 SCANNING，等待后台重试
            return;
        }

        ScanResult result;
        try {
            result = virusScanner.scan(ref);
        } catch (Exception e) {
            throw StorageException.scanUnavailable(ref, "病毒扫描失败: " + e.getMessage(), e);
        }
        handleScanResult(ref, result, now);
    }

    /**
     * 处理扫描结果，迁移状态。
     *
     * @param ref    对象引用
     * @param result 扫描结果
     * @param now    当前时间
     * @throws StorageException 状态迁移失败或文件被隔离
     */
    public void handleScanResult(StorageObjectRef ref, ScanResult result, Instant now) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        Objects.requireNonNull(result, "result 不能为 null");

        FileMetadataRepository.FileMetadataRecord record = metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));

        if (record.scanStatus() != FileScanStatus.SCANNING) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), null);
        }

        FileScanStatus target;
        if (result.verdict() == ScanResult.Verdict.CLEAN) {
            target = FileScanStatus.AVAILABLE;
        } else if (result.shouldQuarantine()) {
            target = FileScanStatus.QUARANTINED;
        } else {
            // SCAN_ERROR：保持 SCANNING，等待重试
            return;
        }

        if (!record.scanStatus().canTransitionTo(target)) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), target);
        }
        if (!metadataRepository.updateScanStatus(ref, FileScanStatus.SCANNING, target, record.revision())) {
            throw StorageException.illegalStateTransition(ref, FileScanStatus.SCANNING, target);
        }
    }

    // ==================== 下载流程（FR-164） ====================

    /**
     * 请求下载：复核权限 + 校验 AVAILABLE 状态 + 签发短时 GET URL（FR-164）。
     *
     * <p>FR-164 要求：
     * <ul>
     *   <li>签发 URL 前复核权限（{@link FileAccessAuthorizer#canDownload}）；</li>
     *   <li>RESTRICTED 文件执行双重复核（{@link FileAccessAuthorizer#revalidateRestrictedDownload}）；</li>
     *   <li>URL 有效期 ≤ 5 分钟（{@link SignedUrl#MAX_TTL}）；</li>
     *   <li>实际下载前二次复核（由下载端点调用 {@link #revalidateBeforeDownload}）。</li>
     * </ul>
     *
     * @param ref          对象引用
     * @param actor        操作者
     * @param workspaceId  工作空间
     * @param objectType   业务对象类型稳定键
     * @param objectId     业务对象 ID
     * @param now          当前时间
     * @return 签名下载 URL
     * @throws StorageException 权限不足、文件状态非 AVAILABLE 或 TTL 违反
     */
    public SignedUrl requestDownload(StorageObjectRef ref, ActorRef actor,
                                     WorkspaceId workspaceId, String objectType, UUID objectId, Instant now) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        Objects.requireNonNull(actor, "actor 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(now, "now 不能为 null");

        FileMetadataRepository.FileMetadataRecord record = metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));

        // 1. 校验文件状态 AVAILABLE
        if (!record.scanStatus().isAccessible()) {
            if (record.scanStatus() == FileScanStatus.QUARANTINED) {
                throw StorageException.quarantineRequired(ref, extractThreatName(record));
            }
            throw StorageException.fileNotAvailable(ref, record.scanStatus());
        }

        // 2. 权限复核（FR-164）
        UUID fileId = extractFileId(ref);
        if (!accessAuthorizer.canDownload(actor, workspaceId, objectType, objectId, fileId)) {
            throw StorageException.permissionRevalidationFailed(ref,
                    "下载权限校验失败（canDownload）");
        }

        // 3. RESTRICTED 文件双重复核
        if (record.metadata().classification().requiresDualRevalidation()) {
            if (!accessAuthorizer.revalidateRestrictedDownload(actor, workspaceId, ref)) {
                throw StorageException.permissionRevalidationFailed(ref,
                        "RESTRICTED 文件双重复核失败（法律保留或权限）");
            }
        }

        // 4. 签发短时 GET URL（TTL 受分类约束）
        Duration ttl = classificationTtl(record.metadata().classification(), DEFAULT_SIGN_TTL);
        return objectStorage.signDownloadUrl(ref, record.metadata().classification(), ttl, now, null);
    }

    /**
     * 实际下载前权限复核（FR-164 二次复核）。
     *
     * <p>由下载端点（API 网关或专用下载控制器）在响应下载请求前调用。
     * 签发 URL 后到实际下载之间权限可能被撤销，此方法二次复核。
     *
     * @param ref         对象引用
     * @param actor       操作者
     * @param workspaceId 工作空间
     * @return true=权限有效，允许下载
     */
    public boolean revalidateBeforeDownload(StorageObjectRef ref, ActorRef actor,
                                            WorkspaceId workspaceId) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        Objects.requireNonNull(actor, "actor 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");

        FileMetadataRepository.FileMetadataRecord record = metadataRepository.findByObjectRef(ref)
                .orElse(null);
        if (record == null || !record.scanStatus().isAccessible()) {
            return false;
        }

        if (!accessAuthorizer.revalidateDownload(actor, workspaceId, ref)) {
            return false;
        }

        if (record.metadata().classification().requiresDualRevalidation()) {
            return accessAuthorizer.revalidateRestrictedDownload(actor, workspaceId, ref);
        }
        return true;
    }

    // ==================== 处置流程（FR-071） ====================

    /**
     * 归档文件（AVAILABLE→ARCHIVED）。
     *
     * <p>到达保留期的文件迁移到冷存储。归档后可恢复（{@link #restoreFromArchive}）。
     *
     * @param ref   对象引用
     * @param actor 操作者
     */
    public void archive(StorageObjectRef ref, ActorRef actor) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        transitionState(ref, FileScanStatus.AVAILABLE, FileScanStatus.ARCHIVED);
    }

    /**
     * 从归档恢复（ARCHIVED→AVAILABLE）。
     *
     * @param ref 对象引用
     */
    public void restoreFromArchive(StorageObjectRef ref) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        transitionState(ref, FileScanStatus.ARCHIVED, FileScanStatus.AVAILABLE);
    }

    /**
     * 释放隔离（QUARANTINED→AVAILABLE，误报处理）。
     *
     * <p>仅限安全管理员审批后调用，需记录审批证据。
     *
     * @param ref 对象引用
     */
    public void releaseFromQuarantine(StorageObjectRef ref) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        transitionState(ref, FileScanStatus.QUARANTINED, FileScanStatus.AVAILABLE);
    }

    /**
     * 处置文件（AVAILABLE/QUARANTINED/ARCHIVED→DISPOSED）+ 物理删除。
     *
     * <p>受 FR-071 法律保留约束，调用方 MUST 在调用前完成处置审批和法律保留检查。
     * DISPOSED 为终态，不可恢复。物理删除由 {@link ObjectStoragePort#delete} 执行。
     *
     * @param ref   对象引用
     * @param actor 操作者
     * @throws StorageException 状态迁移失败
     */
    public void dispose(StorageObjectRef ref, ActorRef actor) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        Objects.requireNonNull(actor, "actor 不能为 null");

        FileMetadataRepository.FileMetadataRecord record = metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));

        if (!record.scanStatus().canTransitionTo(FileScanStatus.DISPOSED)) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), FileScanStatus.DISPOSED);
        }

        if (!metadataRepository.updateScanStatus(ref, record.scanStatus(),
                FileScanStatus.DISPOSED, record.revision())) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), FileScanStatus.DISPOSED);
        }

        // 物理删除对象（FR-071：调用方已确保法律保留检查通过）
        try {
            objectStorage.delete(ref);
        } catch (Exception e) {
            throw StorageException.objectUnavailable(ref, "物理删除失败: " + e.getMessage(), e);
        }
    }

    // ==================== 查询 ====================

    /**
     * 查询文件元数据。
     *
     * @param ref 对象引用
     * @return 文件元数据记录，不存在返回 empty
     */
    public Optional<FileMetadataRepository.FileMetadataRecord> getFile(StorageObjectRef ref) {
        Objects.requireNonNull(ref, "ref 不能为 null");
        return metadataRepository.findByObjectRef(ref);
    }

    /**
     * 查询业务对象的所有文件版本（用于历史和审计）。
     *
     * @param workspaceId 工作空间
     * @param objectType  业务对象类型稳定键
     * @param objectId    业务对象 ID
     * @return 文件元数据记录列表（按版本号降序）
     */
    public java.util.List<FileMetadataRepository.FileMetadataRecord> listVersions(
            WorkspaceId workspaceId, String objectType, UUID objectId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        Objects.requireNonNull(objectId, "objectId 不能为 null");
        return metadataRepository.findByBusinessObject(workspaceId, objectType, objectId);
    }

    // ==================== 内部辅助 ====================

    /**
     * 计算分类约束的签名 URL 有效期。
     *
     * <p>取 min(请求 TTL, MAX_TTL, 分类最大 TTL)。
     */
    private Duration classificationTtl(FileClassification classification, Duration requestedTtl) {
        Duration maxTtl = SignedUrl.MAX_TTL;
        Duration classMax = Duration.ofSeconds(classification.maxSignedUrlTtlSeconds());
        Duration effective = requestedTtl;
        if (effective.compareTo(maxTtl) > 0) {
            effective = maxTtl;
        }
        if (effective.compareTo(classMax) > 0) {
            effective = classMax;
        }
        if (effective.isZero() || effective.isNegative()) {
            throw StorageException.signUrlTtlViolation(null, effective.getSeconds());
        }
        return effective;
    }

    private void transitionState(StorageObjectRef ref, FileScanStatus from, FileScanStatus to) {
        FileMetadataRepository.FileMetadataRecord record = metadataRepository.findByObjectRef(ref)
                .orElseThrow(() -> StorageException.fileNotAvailable(ref, null));
        if (record.scanStatus() != from) {
            throw StorageException.illegalStateTransition(ref, record.scanStatus(), to);
        }
        if (!from.canTransitionTo(to)) {
            throw StorageException.illegalStateTransition(ref, from, to);
        }
        if (!metadataRepository.updateScanStatus(ref, from, to, record.revision())) {
            throw StorageException.illegalStateTransition(ref, from, to);
        }
    }

    private UUID extractFileId(StorageObjectRef ref) {
        // 对象键格式：workspaceId/objectType/objectId/vN
        String[] parts = ref.objectKey().split("/");
        if (parts.length < 4) {
            return null;
        }
        try {
            return UUID.fromString(parts[parts.length - 2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extractThreatName(FileMetadataRepository.FileMetadataRecord record) {
        if (record.scanResultJson() == null || record.scanResultJson().isBlank()) {
            return null;
        }
        // 简化提取，实际由 JSON 解析
        int idx = record.scanResultJson().indexOf("\"threatName\"");
        if (idx < 0) {
            return null;
        }
        int start = record.scanResultJson().indexOf('"', idx + 12);
        if (start < 0) {
            return null;
        }
        int end = record.scanResultJson().indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return record.scanResultJson().substring(start + 1, end);
    }
}

package com.pdp.experience.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 对象存储操作端口（六边形架构出站端口）。
 *
 * <p>对应 research.md 决策 11：大文件存入 S3 兼容对象存储（MinIO），数据库仅保存引用 + 内容哈希
 * + 版本元数据。本端口屏蔽底层 S3/MinIO SDK，业务模块通过此端口操作对象存储，不依赖具体实现。
 *
 * <p><strong>FR-164 签名 URL 约束</strong>：
 * <ul>
 *   <li>{@link #signDownloadUrl} 和 {@link #signUploadUrl} 生成的 URL 有效期 MUST ≤ 5 分钟
 *       （{@link SignedUrl#MAX_TTL}）；</li>
 *   <li>实际有效期由 {@link FileClassification#maxSignedUrlTtlSeconds()} 进一步收紧；</li>
 *   <li>调用方在签发和实际下载之间 MUST 复核权限（由 {@code FileStorageService} 协调）。</li>
 * </ul>
 *
 * <p><strong>多租户隔离</strong>：{@link StorageObjectRef} 含 {@code workspaceId}，实现 MUST 保证
 * 跨工作空间的对象键不冲突（默认对象键前缀为 workspaceId，见 {@link StorageObjectRef#buildObjectKey}）。
 *
 * <p><strong>版本控制</strong>：启用版本控制时，{@link #upload} 返回的 ref 含 {@code versionId}，
 * {@link #download} 和 {@link #delete} 可针对特定版本操作。
 *
 * <p>实现由 {@code public-persistence} 或独立 infrastructure 模块提供（如 S3/MinIO 适配器）。
 */
public interface ObjectStoragePort {

    /**
     * 上传对象到对象存储。
     *
     * <p>上传成功后，文件状态由调用方迁移到 {@link FileScanStatus#SCANNING}。
     * 实现负责将内容流写入对象存储并返回包含 versionId（若启用版本控制）的引用。
     *
     * @param ref         目标对象引用（bucket + objectKey 已确定，versionId 可为空）
     * @param content     内容字节数组（小文件）或流式内容（大文件建议用 {@link #uploadStream}）
     * @param mediaType   媒体类型（如 {@code application/pdf}）
     * @param contentHash 内容哈希（SHA-256，实现 MUST 校验上传后内容哈希一致）
     * @return 包含 versionId 的对象引用（若启用版本控制）
     * @throws StorageException 对象存储不可用或哈希不匹配
     */
    StorageObjectRef upload(StorageObjectRef ref, byte[] content,
                            String mediaType, String contentHash);

    /**
     * 流式上传（大文件，避免内存爆炸）。
     *
     * @param ref             目标对象引用
     * @param contentStream   内容输入流（实现负责关闭）
     * @param sizeBytes       内容大小（字节，未知传 -1）
     * @param mediaType       媒体类型
     * @param contentHash     内容哈希（SHA-256，实现 MUST 校验）
     * @return 包含 versionId 的对象引用
     * @throws StorageException 对象存储不可用或哈希不匹配
     */
    StorageObjectRef uploadStream(StorageObjectRef ref,
                                  java.io.InputStream contentStream,
                                  long sizeBytes,
                                  String mediaType,
                                  String contentHash);

    /**
     * 下载对象内容（小文件，全量读入内存）。
     *
     * <p>调用方 MUST 在调用此方法前完成权限复核（FR-164）。
     *
     * @param ref 对象引用（versionId 可指定特定版本）
     * @return 内容字节数组
     * @throws StorageException 对象不存在或不可用
     */
    byte[] download(StorageObjectRef ref);

    /**
     * 流式下载（大文件，避免内存爆炸）。
     *
     * <p>调用方 MUST 在调用此方法前完成权限复核（FR-164）。
     * 调用方负责关闭返回的输入流。
     *
     * @param ref 对象引用
     * @return 内容输入流
     * @throws StorageException 对象不存在或不可用
     */
    java.io.InputStream downloadStream(StorageObjectRef ref);

    /**
     * 删除对象（软删除或硬删除由实现决定，受 FR-071 法律保留约束）。
     *
     * <p>终态文件（DISPOSED）调用此方法执行物理删除；非终态文件应先迁移到 DISPOSED 状态。
     *
     * @param ref 对象引用
     * @return true=删除成功；false=对象不存在
     * @throws StorageException 对象存储不可用
     */
    boolean delete(StorageObjectRef ref);

    /**
     * 签发短时下载 URL（FR-164：有效期 ≤ 5 分钟）。
     *
     * <p>实现 MUST：
     * <ol>
     *   <li>校验 {@code ttl} 不超过 {@link SignedUrl#MAX_TTL}（5 分钟）；</li>
     *   <li>校验 {@code ttl} 不超过 {@code classification.maxSignedUrlTtlSeconds()}；</li>
     *   <li>实际有效期 = min(ttl, MAX_TTL, classification.maxSignedUrlTtlSeconds())。</li>
     * </ol>
     *
     * <p><strong>注意</strong>：签发 URL 不代表权限已通过，实际下载前 MUST 复核权限（FR-164）。
     * 调用方应通过 {@code FileStorageService.requestDownload} 协调签发和复核。
     *
     * @param ref           对象引用
     * @param classification 文件分类级别（控制 TTL 上限）
     * @param ttl           期望有效期（MUST ≤ MAX_TTL）
     * @param issuedAt      签发时间
     * @param allowedOrigin 允许的来源（CORS，可为空）
     * @return 签名下载 URL
     * @throws StorageException TTL 违反 FR-164 或对象存储不可用
     */
    SignedUrl signDownloadUrl(StorageObjectRef ref, FileClassification classification,
                              Duration ttl, Instant issuedAt, String allowedOrigin);

    /**
     * 签发短时上传 URL（FR-164：有效期 ≤ 5 分钟）。
     *
     * <p>用于前端直传对象存储，避免文件内容经过应用服务器。上传完成后由前端回调
     * {@code FileStorageService} 完成状态迁移和病毒扫描触发。
     *
     * @param ref           目标对象引用
     * @param classification 文件分类级别
     * @param ttl           期望有效期（MUST ≤ MAX_TTL）
     * @param issuedAt      签发时间
     * @param allowedOrigin 允许的来源
     * @return 签名上传 URL
     * @throws StorageException TTL 违反 FR-164 或对象存储不可用
     */
    SignedUrl signUploadUrl(StorageObjectRef ref, FileClassification classification,
                            Duration ttl, Instant issuedAt, String allowedOrigin);

    /**
     * 查询对象元数据（大小、ETag、最后修改时间等，不含业务元数据）。
     *
     * @param ref 对象引用
     * @return 对象元数据，不存在返回 empty
     * @throws StorageException 对象存储不可用
     */
    Optional<ObjectMetadata> headObject(StorageObjectRef ref);

    /**
     * 校验对象存在性（不下载内容）。
     *
     * @param ref 对象引用
     * @return true=存在
     */
    boolean exists(StorageObjectRef ref);

    /**
     * 对象元数据（来自对象存储原生属性，不含业务分类）。
     *
     * @param sizeBytes      对象大小（字节）
     * @param etag           ETag（可用作完整性校验辅助）
     * @param lastModified   最后修改时间
     * @param contentType    内容类型
     * @param versionId      版本 ID（启用版本控制时）
     */
    record ObjectMetadata(long sizeBytes, String etag, Instant lastModified,
                         String contentType, String versionId) {
    }
}

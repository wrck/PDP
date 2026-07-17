package com.pdp.experience.storage;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;

import java.util.UUID;

/**
 * 文件访问授权端口（六边形架构出站端口，FR-164 下载前权限复核）。
 *
 * <p>对应 FR-164："附件签名地址有效期不得超过 5 分钟且实际下载前必须复核权限"。
 * 本端口由应用层实现（委托 {@code com.pdp.identity.port.AuthorizationPort}），存储域通过此端口
 * 复核下载权限，不直接依赖 identity 模块，保持模块边界清晰。
 *
 * <p><strong>复核时机</strong>（FR-164）：
 * <ol>
 *   <li>签发下载 URL 前：调用 {@link #canDownload} 校验；</li>
 *   <li>实际下载前：调用 {@link #revalidateDownload} 二次复核（防止 URL 签发后权限撤销）；</li>
 *   <li>{@link FileClassification#RESTRICTED} 文件：调用 {@link #revalidateRestrictedDownload}
 *       执行双重复核（法律保留检查 + 权限复核）。</li>
 * </ol>
 *
 * <p><strong>不泄露存在性</strong>：无权与不存在统一返回 false，调用方据此抛出
 * {@link StorageException.Reason#PERMISSION_REVALIDATION_FAILED} 或
 * {@link StorageException.Reason#FILE_NOT_AVAILABLE}，不区分原因。
 */
public interface FileAccessAuthorizer {

    /**
     * 校验用户是否可下载文件（签发 URL 前的初次校验）。
     *
     * @param actor      操作者
     * @param workspaceId 工作空间
     * @param objectType  业务对象类型稳定键（如 {@code deliverable}）
     * @param objectId    业务对象 ID
     * @param fileId      文件 ID（对象引用中的 objectId）
     * @return true=允许下载
     */
    boolean canDownload(ActorRef actor, WorkspaceId workspaceId,
                        String objectType, UUID objectId, UUID fileId);

    /**
     * 下载前权限复核（FR-164，实际下载前的二次校验）。
     *
     * <p>签发 URL 后到实际下载之间，用户权限可能被撤销。此方法在下载请求到达时二次复核，
     * 失败时调用方 MUST 拒绝下载并返回 403/404。
     *
     * @param actor      操作者
     * @param workspaceId 工作空间
     * @param objectRef   对象存储引用（含工作空间和对象键）
     * @return true=权限仍然有效，允许下载
     */
    boolean revalidateDownload(ActorRef actor, WorkspaceId workspaceId, StorageObjectRef objectRef);

    /**
     * RESTRICTED 文件下载双重复核（法律保留 + 权限）。
     *
     * <p>{@link FileClassification#RESTRICTED} 文件（含合同、客户、成本、签字数据）下载时
     * MUST 执行双重复核：法律保留检查（FR-071）+ 权限复核。失败时拒绝下载。
     *
     * @param actor      操作者
     * @param workspaceId 工作空间
     * @param objectRef   对象存储引用
     * @return true=法律保留和权限均通过
     */
    boolean revalidateRestrictedDownload(ActorRef actor, WorkspaceId workspaceId,
                                         StorageObjectRef objectRef);
}

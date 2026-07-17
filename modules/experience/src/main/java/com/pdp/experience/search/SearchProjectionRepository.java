package com.pdp.experience.search;

import com.pdp.shared.context.WorkspaceId;

import java.util.List;
import java.util.Optional;

/**
 * 搜索投影仓储端口（持久化层实现）。
 *
 * <p>由 persistence 基础设施实现（如 MySQL 适配器），持久化 {@link SearchDocument} 和
 * {@link SearchTermProjection}。业务模块和应用层通过 {@link SearchProjectionPort} 调用，
 * 仓储端口仅用于持久化。
 *
 * <p><strong>数据库无关契约</strong>（spec.md 第 8 节、SC-033、SC-035）：
 * <ul>
 *   <li>仓储端口 MUST 不暴露 MyBatis、MySQL 驱动或数据库专有类型；</li>
 *   <li>词项查询基于平台 {@link SearchTermProjection} 契约，不依赖 MySQL FULLTEXT 分词；</li>
 *   <li>MySQL FULLTEXT 或专用索引只加速候选检索，<strong>不</strong>改变权限、最终匹配集合和稳定排序；</li>
 *   <li>跨认证数据库实现 MUST 产生相同的词项、匹配集合和稳定业务排序（SC-033）。</li>
 * </ul>
 *
 * <p><strong>乐观锁契约</strong>：{@link #saveDocument} 使用 {@link SearchDocument#indexedRevision()}
 * 作为版本校验，并发更新冲突时抛出 {@link com.pdp.shared.concurrency.OptimisticLockException}。
 */
public interface SearchProjectionRepository {

    /**
     * 保存或更新搜索文档。
     *
     * <p>UPSERT 语义：存在则更新，不存在则插入。更新时校验 {@code indexedRevision} 一致，
     * 防止异步事件乱序导致旧版本覆盖新版本。
     *
     * @param document 搜索文档
     * @return 保存后的文档
     */
    SearchDocument saveDocument(SearchDocument document);

    /**
     * 批量保存词项投影（替换该对象引用下的所有旧词项）。
     *
     * <p>实现 MUST 在单一事务内完成"删除旧词项 + 插入新词项"，避免中间状态导致搜索结果不一致。
     *
     * @param objectRef  业务对象引用
     * @param projections 词项投影列表
     */
    void replaceTermProjections(ObjectRef objectRef, List<SearchTermProjection> projections);

    /**
     * 按对象引用查询当前搜索文档。
     *
     * @param objectRef 业务对象引用
     * @return 搜索文档，不存在返回 empty
     */
    Optional<SearchDocument> findDocumentByObjectRef(ObjectRef objectRef);

    /**
     * 按工作空间和对象类型查询文档（用于审计和重建校验）。
     *
     * @param workspaceId 工作空间
     * @param objectType  对象类型（null 表示所有类型）
     * @param status      文档状态（null 表示所有状态）
     * @param offset      偏移量
     * @param limit       最大返回数
     * @return 搜索文档列表
     */
    List<SearchDocument> findDocuments(WorkspaceId workspaceId,
                                       SearchObjectType objectType,
                                       SearchDocumentStatus status,
                                       int offset,
                                       int limit);

    /**
     * 统计工作空间内文档数。
     *
     * @param workspaceId 工作空间
     * @param objectType  对象类型（null 表示所有类型）
     * @param status      文档状态（null 表示所有状态）
     * @return 文档数
     */
    long countDocuments(WorkspaceId workspaceId, SearchObjectType objectType, SearchDocumentStatus status);

    /**
     * 按工作空间批量更新文档状态（权限撤销时效保证，SC-036）。
     *
     * <p>当权限撤销或对象失去搜索可见性时，调用此方法在 30 秒 SLA 内将相关文档转为新状态。
     * 实现 MUST 保证时效性。
     *
     * @param workspaceId 工作空间
     * @param objectType  对象类型（null 表示所有类型）
     * @param newStatus   新状态
     * @return 受影响的文档数
     */
    int updateStatusByWorkspace(WorkspaceId workspaceId,
                                SearchObjectType objectType,
                                SearchDocumentStatus newStatus);

    /**
     * 按对象引用更新文档状态。
     *
     * @param objectRef 业务对象引用
     * @param newStatus 新状态
     * @return 是否更新成功（对象不存在返回 false）
     */
    boolean updateStatusByObjectRef(ObjectRef objectRef, SearchDocumentStatus newStatus);

    /**
     * 按对象引用删除搜索文档和词项投影（物理删除，用于 REMOVED 状态的清理作业）。
     *
     * @param objectRef 业务对象引用
     * @return 是否删除成功
     */
    boolean deleteByObjectRef(ObjectRef objectRef);

    /**
     * 按分析器版本查询不兼容的文档数（用于触发全量重建）。
     *
     * @param compatibleVersion 当前兼容版本
     * @return 不兼容的文档数
     */
    long countIncompatibleDocuments(AnalyzerVersion compatibleVersion);

    /**
     * 候选检索：按工作空间、词项和字段查询候选对象引用。
     *
     * <p>实现可使用 MySQL FULLTEXT 或专用索引加速，但 MUST 通过平台 {@link SearchTermProjection}
     * 契约保证跨认证数据库结果集合一致（SC-033）。
     *
     * @param workspaceId      工作空间
     * @param terms            规范化词项集合（去停用词后）
     * @param fieldKeys        限定字段键集合（null 表示所有字段）
     * @param analyzerVersion  分析器版本（过滤不兼容文档）
     * @param offset           偏移量
     * @param limit            最大返回数
     * @return 候选对象引用列表（已去重，按相关度参考分降序）
     */
    List<ObjectRef> findCandidates(WorkspaceId workspaceId,
                                   java.util.Set<String> terms,
                                   java.util.Set<String> fieldKeys,
                                   AnalyzerVersion analyzerVersion,
                                   int offset,
                                   int limit);

    /**
     * 查询候选对象的词项投影（用于相关度计算和高亮）。
     *
     * @param objectRef      业务对象引用
     * @param analyzerVersion 分析器版本（过滤不兼容词项）
     * @return 词项投影列表
     */
    List<SearchTermProjection> findTermProjections(ObjectRef objectRef, AnalyzerVersion analyzerVersion);
}

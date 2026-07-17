package com.pdp.experience.search;

import com.pdp.shared.context.ActorRef;

import java.util.List;
import java.util.Optional;

/**
 * 搜索投影端口（应用层调用，FR/US14、SC-033、SC-036）。
 *
 * <p>统一搜索投影的索引（写入）和检索（读取）入口。业务模块通过事务事件日志（Outbox）异步调用
 * {@link #index} 更新投影；查询服务通过 {@link #search} 执行跨项目、跨对象搜索。
 *
 * <p><strong>核心契约（spec.md 第 8 节、SC-033、SC-036）</strong>：
 * <ol>
 *   <li><b>权限过滤</b>：投影层和结果集合层双重过滤权限；结果只包含有权对象，且打开结果时再次校验
 *       （US14）。权限撤销时投影 MUST 在 30 秒内转为 HIDDEN/REMOVED（SC-036）。</li>
 *   <li><b>稳定排序</b>：结果按业务时间 + UUIDv7 兜底排序，数据库原生相关度不得直接成为 API 稳定排序
 *       （SC-033）。{@link SearchResultItem#relevanceScore()} 仅用于候选检索阶段。</li>
 *   <li><b>异步投影</b>：响应 MUST 返回 {@code indexedAt}；需要强一致的精确查询回查主库
 *       （persistence-design.md 第 8 节）。</li>
 *   <li><b>分析器版本</b>：{@link SearchDocument#analyzerVersion()} 与查询版本不兼容时，文档不进入结果集合，
 *       并触发后台重建。</li>
 *   <li><b>幂等性</b>：相同 {@link SearchIndexingEvent#eventId()} 重复投递 MUST 不产生重复索引更新
 *       （US14：相同核心事件重复投递不产生重复通知/索引）。</li>
 * </ol>
 *
 * <p>端口实现由 experience 模块的应用服务提供；底层持久化通过 {@link SearchProjectionRepository} 适配。
 */
public interface SearchProjectionPort {

    /**
     * 处理搜索索引事件（异步投影写入）。
     *
     * <p>由后台作业协调器（T070）从 Outbox 消费并调用。事件类型决定操作：
     * <ul>
     *   <li>{@link SearchIndexingEvent.Type#UPSERT}：构建新 {@link SearchDocument} 和
     *       {@link SearchTermProjection}，覆盖旧版本；</li>
     *   <li>{@link SearchIndexingEvent.Type#HIDE}：将文档状态转为 {@link SearchDocumentStatus#HIDDEN}，
     *       保留索引；</li>
     *   <li>{@link SearchIndexingEvent.Type#REMOVE}：将文档状态转为 {@link SearchDocumentStatus#REMOVED}，
     *       索引可被清理作业回收；</li>
     *   <li>{@link SearchIndexingEvent.Type#REBUILD}：使用新分析器版本重建文档和词项投影，
     *       旧版本标记为待清理。</li>
     * </ul>
     *
     * <p>幂等性：相同 eventId 重复调用 MUST 不产生重复更新。
     *
     * @param event 索引事件
     * @return 处理后的搜索文档（REMOVE 事件返回 REMOVED 状态文档）
     */
    SearchDocument index(SearchIndexingEvent event);

    /**
     * 执行搜索查询。
     *
     * <p>查询流程：
     * <ol>
     *   <li>使用当前分析器版本规范化 {@link SearchQuery#fullText()}；</li>
     *   <li>候选检索：通过 {@link SearchTermProjection} 或数据库 FULLTEXT 加速命中候选对象；</li>
     *   <li>结构化过滤：叠加 {@link SearchQuery#structuredFilters()}；</li>
     *   <li>权限过滤：基于 {@link SearchQuery#operator()} 和工作空间范围过滤；</li>
     *   <li>分析器版本兼容性过滤：排除不兼容版本的文档；</li>
     *   <li>稳定排序：按业务时间 + UUIDv7 兜底排序（SC-033）；</li>
     *   <li>分页：基于平台签名 keyset cursor。</li>
     * </ol>
     *
     * @param query 搜索查询
     * @return 搜索结果（含索引时间和一致性元数据）
     */
    SearchResult search(SearchQuery query);

    /**
     * 权限撤销触发的搜索投影清理（SC-036 时效保证）。
     *
     * <p>当用户权限撤销或对象失去搜索可见性时，调用此方法在 30 秒 SLA 内将相关文档转为
     * HIDDEN/REMOVED。实现 MUST 保证时效性，超时时抛出
     * {@link SearchConsistencyException}（{@link SearchConsistencyException.Reason#
     * PERMISSION_REVOCATION_SLA_VIOLATED}）。
     *
     * @param objectRef    业务对象引用（null 表示按工作空间批量清理）
     * @param workspaceId  工作空间（用于按工作空间批量清理）
     * @param revokedBy    撤销触发者
     * @return 受影响的文档数量
     */
    int revokeVisibility(ObjectRef objectRef, com.pdp.shared.context.WorkspaceId workspaceId, ActorRef revokedBy);

    /**
     * 按对象引用查询当前搜索文档（用于偏差检测和审计）。
     *
     * @param objectRef 业务对象引用
     * @return 搜索文档，不存在返回 empty
     */
    Optional<SearchDocument> findByObjectRef(ObjectRef objectRef);

    /**
     * 触发分析器版本升级的全量重建。
     *
     * <p>分析器 MAJOR 版本升级或投影定义变更时调用。返回重建任务 ID，由后台作业协调器（T070）执行
     * 批量重建。重建完成前不得把新投影用于约束或流程判断（persistence-design.md 第 7 节）。
     *
     * @param targetVersion 目标分析器版本
     * @param triggeredBy   触发者
     * @return 重建任务 ID
     */
    String triggerRebuild(AnalyzerVersion targetVersion, ActorRef triggeredBy);

    /**
     * 统计工作空间内指定对象类型的搜索文档数（用于一致性校验和容量监控）。
     *
     * @param workspaceId 工作空间
     * @param objectType  对象类型（null 表示所有类型）
     * @param status      文档状态（null 表示所有状态）
     * @return 文档数
     */
    long countDocuments(com.pdp.shared.context.WorkspaceId workspaceId,
                        SearchObjectType objectType,
                        SearchDocumentStatus status);

    /**
     * 列出工作空间内指定对象类型的搜索文档（用于审计和重建校验）。
     *
     * @param workspaceId 工作空间
     * @param objectType  对象类型（null 表示所有类型）
     * @param status      文档状态（null 表示所有状态）
     * @param offset      偏移量
     * @param limit       最大返回数
     * @return 搜索文档列表
     */
    List<SearchDocument> listDocuments(com.pdp.shared.context.WorkspaceId workspaceId,
                                       SearchObjectType objectType,
                                       SearchDocumentStatus status,
                                       int offset,
                                       int limit);
}

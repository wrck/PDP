package com.pdp.experience.search;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 搜索文档（数据模型 5.4 SearchDocument）。
 *
 * <p>由平台统一分析器构建，对应 spec.md 第 8 节"数据库无关搜索语义"。MySQL FULLTEXT 或专用索引
 * 只加速候选查询，<strong>不</strong>改变权限、最终匹配集合和稳定排序。
 *
 * <p>核心契约：
 * <ul>
 *   <li>{@code indexedRevision} 记录索引时源对象 revision，用于检测异步投影与主库版本偏差；
 *       偏差时搜索结果 MUST 标记为可能陈旧（{@link SearchDocumentStatus#STALE}）或回查主库。</li>
 *   <li>{@code indexedAt} 为异步投影时间戳，搜索响应 MUST 返回此时间（FR/SC-018 渐进结果要求）。</li>
 *   <li>{@code status} 控制可见性；权限撤销时投影 MUST 在 30 秒内转为 {@link SearchDocumentStatus#HIDDEN}
 *       或 {@link SearchDocumentStatus#REMOVED}（SC-036）。</li>
 *   <li>{@code analyzerVersion} 与查询时分析器版本不兼容时，文档 MUST 不进入结果集合，并触发后台重建。</li>
 * </ul>
 *
 * @param objectRef        业务对象引用
 * @param analyzerVersion  分析器版本
 * @param title            文档标题（规范化前的人类可读标题，用于展示）
 * @param normalizedText   规范化全文
 * @param fieldWeights     字段权重快照（索引时的权重版本，用于回放与一致性校验）
 * @param indexedRevision  索引时源对象 revision 快照
 * @param indexedAt        索引时间
 * @param status           文档状态
 */
public record SearchDocument(
        ObjectRef objectRef,
        AnalyzerVersion analyzerVersion,
        String title,
        NormalizedText normalizedText,
        List<FieldWeight> fieldWeights,
        int indexedRevision,
        Instant indexedAt,
        SearchDocumentStatus status) {

    public SearchDocument {
        Objects.requireNonNull(objectRef, "objectRef 不能为 null");
        Objects.requireNonNull(analyzerVersion, "analyzerVersion 不能为 null");
        Objects.requireNonNull(title, "title 不能为 null");
        Objects.requireNonNull(normalizedText, "normalizedText 不能为 null");
        Objects.requireNonNull(indexedAt, "indexedAt 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        fieldWeights = fieldWeights == null ? List.of() : List.copyOf(fieldWeights);
        if (indexedRevision < 0) {
            throw new IllegalArgumentException("indexedRevision 不能为负");
        }
    }

    /**
     * 构建新搜索文档（VISIBLE 状态）。
     *
     * @param objectRef       业务对象引用
     * @param analyzerVersion 分析器版本
     * @param title           标题
     * @param normalizedText  规范化文本
     * @param fieldWeights    字段权重
     * @param indexedRevision 源对象 revision 快照
     * @param indexedAt       索引时间
     * @return 新文档
     */
    public static SearchDocument visible(
            ObjectRef objectRef,
            AnalyzerVersion analyzerVersion,
            String title,
            NormalizedText normalizedText,
            List<FieldWeight> fieldWeights,
            int indexedRevision,
            Instant indexedAt) {
        return new SearchDocument(objectRef, analyzerVersion, title, normalizedText,
                fieldWeights, indexedRevision, indexedAt, SearchDocumentStatus.VISIBLE);
    }

    /**
     * 转为指定状态（保留其他字段不变）。
     *
     * @param newStatus 新状态
     * @return 新文档
     */
    public SearchDocument withStatus(SearchDocumentStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus 不能为 null");
        return new SearchDocument(objectRef, analyzerVersion, title, normalizedText,
                fieldWeights, indexedRevision, indexedAt, newStatus);
    }

    /**
     * 检测异步投影与当前源对象 revision 是否偏差。
     *
     * @param currentRevision 当前源对象 revision
     * @return true 表示偏差，搜索结果可能陈旧
     */
    public boolean isStaleFor(int currentRevision) {
        return indexedRevision != currentRevision;
    }

    /**
     * 分析器版本是否兼容查询时分析器版本。
     *
     * @param queryAnalyzerVersion 查询时分析器版本
     * @return true 表示兼容，可进入结果集合
     */
    public boolean isCompatibleWith(AnalyzerVersion queryAnalyzerVersion) {
        return analyzerVersion.isCompatibleWith(queryAnalyzerVersion);
    }

    /** 是否对搜索可见（VISIBLE 状态）。 */
    public boolean isVisible() {
        return status == SearchDocumentStatus.VISIBLE;
    }
}

package com.pdp.experience.search;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 搜索结果条目。
 *
 * <p>对应搜索响应中的单条结果。包含业务对象引用、相关度参考分（仅用于候选排序，不进入 API 稳定排序）、
 * 命中字段高亮片段、索引时间和权限再次校验标记。
 *
 * <p><strong>稳定排序契约</strong>（spec.md 第 8 节、SC-033）：
 * <ul>
 *   <li>最终结果集合排序以业务时间 + UUIDv7 兜底，数据库原生相关度不得直接成为 API 稳定排序；</li>
 *   <li>{@code relevanceScore} 仅用于候选检索阶段的内部排序，对外 API MUST 通过
 *       {@link SearchResultItem#businessSortKey()} 暴露稳定排序键。</li>
 * </ul>
 *
 * <p><strong>权限再次校验</strong>（US14）：{@link #permissionRevalidated} 标记打开结果时是否已再次
 * 校验当前权限。投影层过滤后到结果打开可能存在权限撤销时间窗，结果打开 MUST 重新校验。
 *
 * @param objectRef              业务对象引用
 * @param title                  展示标题（来自 {@link SearchDocument#title()}）
 * @param relevanceScore         候选相关度参考分（非 API 稳定排序依据）
 * @param highlightedFragments   命中字段高亮片段列表（可为空）
 * @param indexedAt              索引时间（异步投影时间戳，搜索响应 MUST 返回）
 * @param businessSortKey        稳定业务排序键（业务时间 + UUIDv7 兜底，序列化形式）
 * @param permissionRevalidated  是否已再次校验权限（默认 false，由调用方在打开结果时设置）
 */
public record SearchResultItem(
        ObjectRef objectRef,
        String title,
        double relevanceScore,
        List<String> highlightedFragments,
        Instant indexedAt,
        String businessSortKey,
        boolean permissionRevalidated) {

    public SearchResultItem {
        Objects.requireNonNull(objectRef, "objectRef 不能为 null");
        Objects.requireNonNull(title, "title 不能为 null");
        Objects.requireNonNull(indexedAt, "indexedAt 不能为 null");
        Objects.requireNonNull(businessSortKey, "businessSortKey 不能为 null");
        if (businessSortKey.isBlank()) {
            throw new IllegalArgumentException("businessSortKey 不能为空白");
        }
        highlightedFragments = highlightedFragments == null ? List.of() : List.copyOf(highlightedFragments);
    }

    public static SearchResultItem of(
            ObjectRef objectRef,
            String title,
            double relevanceScore,
            List<String> highlightedFragments,
            Instant indexedAt,
            String businessSortKey) {
        return new SearchResultItem(objectRef, title, relevanceScore, highlightedFragments,
                indexedAt, businessSortKey, false);
    }

    /** 标记权限已再次校验。 */
    public SearchResultItem withPermissionRevalidated() {
        return new SearchResultItem(objectRef, title, relevanceScore, highlightedFragments,
                indexedAt, businessSortKey, true);
    }
}

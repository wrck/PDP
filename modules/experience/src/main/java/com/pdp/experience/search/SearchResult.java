package com.pdp.experience.search;

import com.pdp.shared.page.PageResult;

import java.time.Instant;
import java.util.Optional;

/**
 * 搜索结果（分页）。
 *
 * <p>基于平台签名 keyset cursor 分页（{@link PageResult}），并附带搜索一致性元数据：
 * <ul>
 *   <li>{@code indexedAt}：本页结果中最新的索引时间戳（响应 MUST 返回，FR/SC-018 渐进结果要求）；</li>
 *   <li>{@code possiblyStale}：是否存在源对象 revision 偏差的文档；为 true 时调用方 SHOULD 回查主库
 *       或提示用户结果可能不一致（persistence-design.md 第 8 节）；</li>
 *   <li>{@code analyzerVersion}：本次查询使用的分析器版本，用于前端展示和审计。</li>
 * </ul>
 *
 * <p><strong>稳定排序契约</strong>：结果按 {@link SearchResultItem#businessSortKey()} 排序，
 * 相关度相同按业务时间和 UUIDv7 兜底（SC-033）。
 */
public record SearchResult(
        PageResult<SearchResultItem> page,
        Instant indexedAt,
        boolean possiblyStale,
        AnalyzerVersion analyzerVersion) {

    public SearchResult {
        java.util.Objects.requireNonNull(page, "page 不能为 null");
        java.util.Objects.requireNonNull(indexedAt, "indexedAt 不能为 null");
        java.util.Objects.requireNonNull(analyzerVersion, "analyzerVersion 不能为 null");
    }

    public static SearchResult of(
            PageResult<SearchResultItem> page,
            Instant indexedAt,
            boolean possiblyStale,
            AnalyzerVersion analyzerVersion) {
        return new SearchResult(page, indexedAt, possiblyStale, analyzerVersion);
    }

    /** 本页是否为空。 */
    public boolean isEmpty() {
        return page.data().isEmpty();
    }

    /** 本页结果数量。 */
    public int size() {
        return page.data().size();
    }

    /** 下一页游标（无更多数据时为空）。 */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(page.nextCursor());
    }
}

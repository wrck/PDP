package com.pdp.shared.page;

import java.util.List;
import java.util.Optional;

/**
 * 游标分页结果。
 *
 * <p>包含当前页数据与下一页游标。默认不执行总数查询；
 * 仅在显式需要时通过 {@link #total()} 提供近似总数。
 *
 * @param data 当前页数据
 * @param nextCursor 下一页游标，无更多数据时为 null
 * @param hasMore 是否还有更多数据
 * @param total 可选总数，默认为空
 */
public record PageResult<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore,
        Long total) {

    public PageResult {
        data = data == null ? List.of() : List.copyOf(data);
    }

    public static <T> PageResult<T> of(List<T> data, String nextCursor, boolean hasMore) {
        return new PageResult<>(data, nextCursor, hasMore, null);
    }

    public static <T> PageResult<T> ofWithTotal(List<T> data, String nextCursor, boolean hasMore, Long total) {
        return new PageResult<>(data, nextCursor, hasMore, total);
    }

    public Optional<Long> total() {
        return Optional.ofNullable(total);
    }
}

package com.pdp.shared.page;

/**
 * 排序方向。
 *
 * <p>游标分页支持正向（下一页）与反向（上一页）遍历。
 */
public enum SortDirection {
    ASC,
    DESC;

    public SortDirection reverse() {
        return this == ASC ? DESC : ASC;
    }
}

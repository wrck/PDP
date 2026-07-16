package com.pdp.shared.page;

import java.util.List;

/**
 * 游标分页请求。
 *
 * <p>首页请求仅指定 {@code pageSize}；后续请求携带平台签发的 {@code cursor}。
 * MyBatis-Plus {@code Page/IPage} 不得作为外部分页契约。
 *
 * @param cursor 平台签发的不透明游标，首页为 null
 * @param pageSize 每页大小
 */
public record PageRequest(String cursor, int pageSize) {

    public PageRequest {
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("pageSize 必须在 1-500 之间");
        }
    }

    /** 首页请求。 */
    public static PageRequest firstPage(int pageSize) {
        return new PageRequest(null, pageSize);
    }

    /** 续页请求。 */
    public static PageRequest next(String cursor, int pageSize) {
        return new PageRequest(cursor, pageSize);
    }

    public boolean isFirstPage() {
        return cursor == null || cursor.isBlank();
    }
}

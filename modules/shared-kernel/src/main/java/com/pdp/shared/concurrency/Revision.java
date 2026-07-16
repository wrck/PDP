package com.pdp.shared.concurrency;

/**
 * 乐观锁版本值对象。
 *
 * <p>所有可修改业务对象包含 {@code revision} 整数字段，初始为 1。
 * 自定义更新 SQL 必须显式校验并递增 revision：
 * <pre>
 *   SET revision = revision + 1
 *   WHERE id = :id AND workspace_id = :workspaceId AND revision = :expectedRevision AND &lt;授权范围&gt;
 * </pre>
 * 影响行数为 1 表示成功；为 0 时区分无权/不存在（404）与版本冲突（409）。
 */
public record Revision(int value) {

    public Revision {
        if (value < 1) {
            throw new IllegalArgumentException("revision 必须 >= 1");
        }
    }

    public static Revision initial() {
        return new Revision(1);
    }

    public static Revision of(int value) {
        return new Revision(value);
    }

    /** 返回下一版本。 */
    public Revision next() {
        return new Revision(value + 1);
    }
}

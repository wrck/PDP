package com.pdp.shared.concurrency;

/**
 * 并发冲突描述值对象。
 *
 * <p>乐观锁更新影响行数为 0 时，区分无权/不存在与版本冲突。
 * 本记录携带期望版本与实际版本（若可安全暴露），供异常与响应使用。
 */
public record ConcurrencyConflict(
        String objectType,
        java.util.UUID objectId,
        Revision expectedRevision,
        Revision actualRevision,
        ConflictKind kind) {

    /** 冲突种类。 */
    public enum ConflictKind {
        /** 版本不匹配（对象存在但 revision 不同）。 */
        STALE_REVISION,
        /** 对象不存在或无权（无法区分，统一 404）。 */
        NOT_FOUND_OR_FORBIDDEN
    }

    public boolean isStaleRevision() {
        return kind == ConflictKind.STALE_REVISION;
    }
}

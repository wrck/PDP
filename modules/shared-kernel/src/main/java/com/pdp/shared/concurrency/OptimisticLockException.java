package com.pdp.shared.concurrency;

import com.pdp.shared.error.ConflictException;
import com.pdp.shared.error.ErrorCode;

/**
 * 乐观锁冲突异常。
 *
 * <p>自定义 Mapper SQL 显式递增 revision 后影响行数为 0 时抛出。
 * 区分无权/不存在（404）与版本冲突（409），禁止泄露无权对象存在性。
 */
public class OptimisticLockException extends ConflictException {

    private final ConcurrencyConflict conflict;
    private final int httpStatus;

    private OptimisticLockException(ErrorCode code, String message, String objectType,
                                    java.util.UUID objectId, Revision expected, Revision actual,
                                    ConcurrencyConflict.ConflictKind kind, int httpStatus) {
        super(code, message);
        this.conflict = new ConcurrencyConflict(objectType, objectId, expected, actual, kind);
        this.httpStatus = httpStatus;
        target(objectType, objectId);
        if (actual != null) {
            currentRevision((long) actual.value());
        }
    }

    /** 版本冲突：对象存在但 revision 不匹配（HTTP 409）。 */
    public static OptimisticLockException staleRevision(String objectType, java.util.UUID objectId,
                                                        Revision expected, Revision actual) {
        OptimisticLockException ex = new OptimisticLockException(
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "资源版本冲突，请重新获取后重试",
                objectType, objectId, expected, actual,
                ConcurrencyConflict.ConflictKind.STALE_REVISION, 409);
        ex.reason("期望 revision=" + expected.value() + "，实际 revision=" + actual.value());
        ex.nextStep("重新获取资源最新版本后重试更新");
        return ex;
    }

    /** 无权或不存在：无法区分，统一 404 语义（HTTP 404）。 */
    public static OptimisticLockException notFoundOrForbidden(String objectType, java.util.UUID objectId) {
        return new OptimisticLockException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "请求的资源不存在或无权访问",
                objectType, objectId, null, null,
                ConcurrencyConflict.ConflictKind.NOT_FOUND_OR_FORBIDDEN, 404);
    }

    public ConcurrencyConflict conflict() {
        return conflict;
    }

    @Override
    protected int httpStatus() {
        return httpStatus;
    }
}

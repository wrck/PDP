package com.pdp.experience.search;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

/**
 * 搜索投影一致性异常。
 *
 * <p>当搜索投影与主库版本偏差（{@link SearchDocument#isStaleFor(int)} 返回 true）、分析器版本不兼容
 * 或权限撤销时效未满足（SC-036：搜索 30 秒内移除）时抛出。
 *
 * <p>对应错误码 {@link ErrorCode#SERVICE_UNAVAILABLE}（搜索投影不可用，调用方应回查主库或返回降级响应）。
 * 异常携带稳定原因分类和下一步建议，符合 spec.md "所有非法迁移、并发冲突和补偿操作必须具有稳定原因分类、
 * 下一步建议和关联证据"。
 */
public class SearchConsistencyException extends PdpException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final ObjectRef objectRef;

    /**
     * 异常原因分类（稳定键）。
     */
    public enum Reason {
        /** 投影与主库 revision 偏差，结果可能不一致。 */
        STALE_PROJECTION("SEARCH.STALE_PROJECTION"),
        /** 分析器版本不兼容，需要后台重建。 */
        ANALYZER_VERSION_MISMATCH("SEARCH.ANALYZER_VERSION_MISMATCH"),
        /** 权限撤销时效未满足（搜索结果未在 SLA 内移除）。 */
        PERMISSION_REVOCATION_SLA_VIOLATED("SEARCH.PERMISSION_REVOCATION_SLA_VIOLATED"),
        /** 异步投影延迟超阈值，强一致查询应回查主库。 */
        PROJECTION_LAG_EXCEEDED("SEARCH.PROJECTION_LAG_EXCEEDED");

        private final String stableKey;

        Reason(String stableKey) {
            this.stableKey = stableKey;
        }

        public String stableKey() {
            return stableKey;
        }
    }

    public SearchConsistencyException(Reason reason, ObjectRef objectRef, String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
        this.reason = reason;
        this.objectRef = objectRef;
    }

    public SearchConsistencyException(Reason reason, ObjectRef objectRef, String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
        this.reason = reason;
        this.objectRef = objectRef;
    }

    public Reason reason() {
        return reason;
    }

    public ObjectRef objectRef() {
        return objectRef;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        return "[" + reason.stableKey() + "] " + base
                + (objectRef != null ? " (objectRef=" + objectRef + ")" : "");
    }
}

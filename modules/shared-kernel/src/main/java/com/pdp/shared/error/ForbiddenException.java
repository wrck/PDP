package com.pdp.shared.error;

/**
 * 禁止访问异常（HTTP 403）。
 *
 * <p>权限不足、工作空间边界越权等返回此异常。
 * 注意：无权与不存在统一返回 404 语义，仅在确需告知"存在但无权"时使用 403。
 */
public class ForbiddenException extends PdpException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode code, String message) {
        super(code, message);
    }

    @Override
    protected int httpStatus() {
        return 403;
    }
}

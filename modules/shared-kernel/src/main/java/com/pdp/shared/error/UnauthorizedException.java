package com.pdp.shared.error;

/**
 * 未认证异常（HTTP 401）。
 */
public class UnauthorizedException extends PdpException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    @Override
    protected int httpStatus() {
        return 401;
    }
}

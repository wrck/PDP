package com.pdp.shared.error;

/**
 * 业务规则违反异常（HTTP 422）。
 *
 * <p>请求格式正确但违反业务规则或不变量时返回。
 */
public class BusinessRuleException extends PdpException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATED, message);
    }

    public BusinessRuleException(ErrorCode code, String message) {
        super(code, message);
    }

    @Override
    protected int httpStatus() {
        return 422;
    }
}

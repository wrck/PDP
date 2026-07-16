package com.pdp.shared.error;

import java.util.Map;

/**
 * 校验失败异常（HTTP 400）。
 *
 * <p>请求参数、字段约束或业务前置校验失败。{@code errors} 携带字段级错误明细。
 */
public class ValidationException extends PdpException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
        this.fieldErrors = Map.of();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(ErrorCode.VALIDATION_FAILED, message);
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    @Override
    public Problem toProblem() {
        return Problem.builder()
                .code(errorCode())
                .title(getMessage())
                .status(httpStatus())
                .correlationId(correlationId())
                .errors(fieldErrors)
                .build();
    }

    @Override
    protected int httpStatus() {
        return 400;
    }
}

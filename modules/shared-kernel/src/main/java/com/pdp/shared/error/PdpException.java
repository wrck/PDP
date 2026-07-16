package com.pdp.shared.error;

import java.util.UUID;

/**
 * 平台异常基类。
 *
 * <p>所有领域与应用异常继承此类，携带 {@link ErrorCode}、可选 correlationId 与目标对象信息。
 * apps/api 全局异常处理器据此生成 {@code application/problem+json} 响应。
 *
 * <p>异常消息不得包含无权对象的存在性信息；无权与不存在统一返回 404 语义。
 */
public class PdpException extends RuntimeException {

    private final ErrorCode errorCode;
    private UUID correlationId;
    private String targetObjectType;
    private UUID targetObjectId;
    private String currentState;
    private Long currentRevision;
    private String reason;
    private String nextStep;

    public PdpException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PdpException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public UUID correlationId() { return correlationId; }
    public String targetObjectType() { return targetObjectType; }
    public UUID targetObjectId() { return targetObjectId; }
    public String currentState() { return currentState; }
    public Long currentRevision() { return currentRevision; }
    public String reason() { return reason; }
    public String nextStep() { return nextStep; }

    public PdpException correlationId(UUID correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public PdpException target(String objectType, UUID objectId) {
        this.targetObjectType = objectType;
        this.targetObjectId = objectId;
        return this;
    }

    public PdpException currentState(String state) {
        this.currentState = state;
        return this;
    }

    public PdpException currentRevision(Long revision) {
        this.currentRevision = revision;
        return this;
    }

    public PdpException reason(String reason) {
        this.reason = reason;
        return this;
    }

    public PdpException nextStep(String nextStep) {
        this.nextStep = nextStep;
        return this;
    }

    /** 转换为 {@link Problem}，供异常处理器使用。 */
    public Problem toProblem() {
        return Problem.builder()
                .code(errorCode)
                .title(getMessage())
                .status(httpStatus())
                .correlationId(correlationId)
                .targetObjectType(targetObjectType)
                .targetObjectId(targetObjectId)
                .currentState(currentState)
                .currentRevision(currentRevision)
                .reason(reason)
                .nextStep(nextStep)
                .build();
    }

    /** 子类覆写：返回对应 HTTP 状态码。 */
    protected int httpStatus() {
        return 500;
    }
}

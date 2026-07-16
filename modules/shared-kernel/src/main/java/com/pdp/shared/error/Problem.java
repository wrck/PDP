package com.pdp.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * 统一问题详情，对应 RFC 7807 {@code application/problem+json} 风格响应。
 *
 * <p>失败响应必须包含 {@code correlationId}、目标对象、当前状态、当前版本、稳定原因和下一步建议，
 * 但不得泄露无权字段（无权与不存在不得通过响应差异泄露）。
 *
 * <p>本类为可移植 POJO，不依赖 spring-web；apps/api 的全局异常处理器将其序列化为
 * {@code application/problem+json} 响应体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type", "title", "status", "detail", "instance",
        "code", "correlationId",
        "targetObjectType", "targetObjectId", "currentState", "currentRevision",
        "reason", "nextStep", "errors"
})
public record Problem(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String code,
        UUID correlationId,
        String targetObjectType,
        UUID targetObjectId,
        String currentState,
        Long currentRevision,
        String reason,
        String nextStep,
        Map<String, String> errors) {

    public Problem {
        errors = errors == null ? Map.of() : Map.copyOf(errors);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private URI type;
        private String title;
        private int status;
        private String detail;
        private URI instance;
        private String code;
        private UUID correlationId;
        private String targetObjectType;
        private UUID targetObjectId;
        private String currentState;
        private Long currentRevision;
        private String reason;
        private String nextStep;
        private Map<String, String> errors;

        public Builder type(URI type) { this.type = type; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(int status) { this.status = status; return this; }
        public Builder detail(String detail) { this.detail = detail; return this; }
        public Builder instance(URI instance) { this.instance = instance; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder code(ErrorCode code) { this.code = code.code(); return this; }
        public Builder correlationId(UUID correlationId) { this.correlationId = correlationId; return this; }
        public Builder targetObjectType(String t) { this.targetObjectType = t; return this; }
        public Builder targetObjectId(UUID id) { this.targetObjectId = id; return this; }
        public Builder currentState(String s) { this.currentState = s; return this; }
        public Builder currentRevision(Long r) { this.currentRevision = r; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder nextStep(String nextStep) { this.nextStep = nextStep; return this; }
        public Builder errors(Map<String, String> errors) { this.errors = errors; return this; }

        public Problem build() {
            return new Problem(type, title, status, detail, instance, code, correlationId,
                    targetObjectType, targetObjectId, currentState, currentRevision,
                    reason, nextStep, errors);
        }
    }
}

package com.pdp.workflow.model;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

/**
 * 平台工作流引擎异常（FR-174、ADR-0005）。
 *
 * <p>封装 Flowable 引擎异常并翻译为平台稳定错误码，业务模块 MUST NOT 依赖
 * {@code org.flowable.*} 异常类型。所有工作流端口方法在引擎层故障时抛出本异常。
 *
 * <p><strong>稳定原因分类</strong>（持久化与审计使用，禁止依赖枚举序号）：
 * <ul>
 *   <li>{@link Reason#DEFINITION_INVALID}：BPMN 校验失败、流程键冲突或内容哈希不匹配；</li>
 *   <li>{@link Reason#DEFINITION_NOT_FOUND}：流程定义不存在或已下线；</li>
 *   <li>{@link Reason#INSTANCE_NOT_FOUND}：流程实例不存在或已结束；</li>
 *   <li>{@link Reason#ILLEGAL_STATE_TRANSITION}：实例状态不满足操作前置条件；</li>
 *   <li>{@link Reason#TASK_NOT_FOUND}：人工任务不存在或已被办理；</li>
 *   <li>{@link Reason#TASK_NOT_ASSIGNABLE}：当前用户不是候选人或权限复核未通过；</li>
 *   <li>{@link Reason#MIGRATION_PLAN_INVALID}：迁移计划不兼容或目标版本不可用；</li>
 *   <li>{@link Reason#ENGINE_UNAVAILABLE}：Flowable 引擎不可用或数据库故障；</li>
 *   <li>{@link Reason#ORCHESTRATION_FAILED}：编排消息关联、定时器或异步作业失败；</li>
 *   <li>{@link Reason#DEADLOCK_DETECTED}：引擎检测到死锁（如乐观锁冲突）；</li>
 *   <li>{@link Reason#PERMISSION_REVOKED}：办理前权限复核未通过（权限已被撤销）。</li>
 * </ul>
 *
 * <p>对应 {@code application/problem+json} 的 {@code code} 字段由
 * {@link #errorCodeFor(Reason)} 映射，HTTP 状态码由 {@link #httpStatus()} 决定。
 */
public class WorkflowEngineException extends PdpException {

    /**
     * 工作流引擎异常稳定原因分类。
     *
     * <p>每个原因映射到 {@link ErrorCode} 与 HTTP 状态码，保证错误响应稳定可消费。
     */
    public enum Reason {
        DEFINITION_INVALID,
        DEFINITION_NOT_FOUND,
        INSTANCE_NOT_FOUND,
        ILLEGAL_STATE_TRANSITION,
        TASK_NOT_FOUND,
        TASK_NOT_ASSIGNABLE,
        MIGRATION_PLAN_INVALID,
        ENGINE_UNAVAILABLE,
        ORCHESTRATION_FAILED,
        DEADLOCK_DETECTED,
        PERMISSION_REVOKED;

        /** 稳定键（持久化与审计使用，禁止依赖枚举序号）。 */
        public String stableKey() {
            return name();
        }
    }

    private final Reason reason;

    public WorkflowEngineException(Reason reason, String message) {
        super(errorCodeFor(reason), message);
        this.reason = reason;
    }

    public WorkflowEngineException(Reason reason, String message, Throwable cause) {
        super(errorCodeFor(reason), message, cause);
        this.reason = reason;
    }

    public Reason workflowReason() {
        return reason;
    }

    /**
     * 将工作流原因映射为平台错误码。
     *
     * @param reason 工作流异常原因
     * @return 平台错误码
     */
    public static ErrorCode errorCodeFor(Reason reason) {
        return switch (reason) {
            case DEFINITION_INVALID, MIGRATION_PLAN_INVALID -> ErrorCode.VALIDATION_FAILED;
            case DEFINITION_NOT_FOUND, INSTANCE_NOT_FOUND, TASK_NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case ILLEGAL_STATE_TRANSITION -> ErrorCode.CONFLICT;
            case TASK_NOT_ASSIGNABLE, PERMISSION_REVOKED -> ErrorCode.FORBIDDEN;
            case ENGINE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            case ORCHESTRATION_FAILED, DEADLOCK_DETECTED -> ErrorCode.BUSINESS_RULE_VIOLATED;
        };
    }

    @Override
    protected int httpStatus() {
        return switch (reason) {
            case DEFINITION_INVALID, MIGRATION_PLAN_INVALID -> 422;
            case DEFINITION_NOT_FOUND, INSTANCE_NOT_FOUND, TASK_NOT_FOUND -> 404;
            case ILLEGAL_STATE_TRANSITION -> 409;
            case TASK_NOT_ASSIGNABLE, PERMISSION_REVOKED -> 403;
            case ENGINE_UNAVAILABLE -> 503;
            case ORCHESTRATION_FAILED, DEADLOCK_DETECTED -> 500;
        };
    }
}

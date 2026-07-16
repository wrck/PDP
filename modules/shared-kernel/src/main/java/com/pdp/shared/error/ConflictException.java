package com.pdp.shared.error;

import java.util.UUID;

/**
 * 状态冲突异常（HTTP 409）。
 *
 * <p>乐观锁版本冲突、非法状态迁移、并发覆盖等统一返回此异常。
 * 冲突响应必须包含当前状态与当前版本，便于客户端重新获取后重试。
 */
public class ConflictException extends PdpException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }

    public ConflictException(String objectType, UUID objectId, String currentState, Long currentRevision) {
        super(ErrorCode.CONFLICT, "资源状态或版本冲突");
        target(objectType, objectId);
        currentState(currentState);
        currentRevision(currentRevision);
    }

    @Override
    protected int httpStatus() {
        return 409;
    }
}

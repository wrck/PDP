package com.pdp.shared.error;

import java.util.UUID;

/**
 * 资源不存在异常（HTTP 404）。
 *
 * <p>无权访问与不存在统一返回此异常，避免通过响应差异泄露无权对象存在性。
 */
public class ResourceNotFoundException extends PdpException {

    public ResourceNotFoundException(String objectType, UUID objectId) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "请求的资源不存在或无权访问");
        target(objectType, objectId);
    }

    public ResourceNotFoundException(ErrorCode code, String message, String objectType, UUID objectId) {
        super(code, message);
        target(objectType, objectId);
    }

    @Override
    protected int httpStatus() {
        return 404;
    }
}

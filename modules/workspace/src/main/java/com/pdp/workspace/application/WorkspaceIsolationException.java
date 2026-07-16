package com.pdp.workspace.application;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

public final class WorkspaceIsolationException extends PdpException {
  public WorkspaceIsolationException() {
    super(ErrorCode.ACCESS_DENIED, "无权访问该工作空间资源");
  }
}

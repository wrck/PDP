package com.pdp.shared.concurrency;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;
import java.util.Map;

public final class ConcurrencyConflictException extends PdpException {
  public ConcurrencyConflictException(Revision expected, Revision actual) {
    super(
        ErrorCode.REVISION_CONFLICT,
        "对象版本已变化，请刷新后重试",
        null,
        Map.of("expectedRevision", expected.value(), "actualRevision", actual.value()));
  }

  /** 保留并发冲突旧契约名称，统一数据仍由 {@link #attributes()} 承载。 */
  public Map<String, Object> details() {
    return attributes();
  }
}

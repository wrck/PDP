package com.pdp.domainconfig.domain.packageversion;

/**
 * 校验项严重级别。
 *
 * <p>{@link #BLOCKER} 必须阻断发布；{@link #ERROR} 默认阻断发布但可由独立发布者人工放行；
 * {@link #WARNING} 与 {@link #INFO} 仅作提示。
 */
public enum ValidationItemSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKER
}

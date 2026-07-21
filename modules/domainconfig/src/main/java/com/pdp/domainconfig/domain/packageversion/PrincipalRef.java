package com.pdp.domainconfig.domain.packageversion;

import java.util.Objects;

/**
 * 主体引用值对象（designer / publisher / approvedBy 等）。
 *
 * <p>用于 FR-167 设计者/发布者职责分离校验：设计者（{@code designer}）与发布者
 * （{@code publisher}）必须为不同主体。
 */
public record PrincipalRef(
        PrincipalType principalType,
        String principalId,
        String displayLabel) {

    public PrincipalRef {
        if (principalType == null) {
            throw new IllegalArgumentException("principalType 不能为 null");
        }
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId 不能为空");
        }
        if (displayLabel != null && displayLabel.length() > 200) {
            throw new IllegalArgumentException("displayLabel 不能超过 200 字符");
        }
    }

    public PrincipalRef(PrincipalType principalType, String principalId) {
        this(principalType, principalId, null);
    }

    /** 判断两个主体是否同一（用于职责分离校验）。 */
    public boolean sameAs(PrincipalRef other) {
        if (other == null) {
            return false;
        }
        return principalType == other.principalType
                && Objects.equals(principalId, other.principalId);
    }
}

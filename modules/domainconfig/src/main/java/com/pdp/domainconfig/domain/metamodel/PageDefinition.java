package com.pdp.domainconfig.domain.metamodel;

import java.util.List;
import java.util.Map;

/**
 * 页面定义（domain-package.schema.json pageDefinition）。
 *
 * <p>页面绑定到具体对象类型，通过 {@code layout} 描述字段区块、标签页与组件布局。
 * {@code visibilityRuleKey} 引用规则定义以控制页面可见性。
 */
public record PageDefinition(
        String stableKey,
        String objectKey,
        Map<String, Object> layout,
        String visibilityRuleKey) {

    public PageDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (layout == null || layout.isEmpty()) {
            throw new IllegalArgumentException("layout 不能为空");
        }
        layout = Map.copyOf(layout);
    }
}

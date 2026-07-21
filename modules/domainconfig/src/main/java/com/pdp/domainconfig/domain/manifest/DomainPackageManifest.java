package com.pdp.domainconfig.domain.manifest;

import com.pdp.domainconfig.domain.behavior.DomainPackageWorkflowBinding;
import com.pdp.domainconfig.domain.behavior.ExtensionDefinition;
import com.pdp.domainconfig.domain.behavior.OverrideDefinition;
import com.pdp.domainconfig.domain.behavior.PermissionDefinition;
import com.pdp.domainconfig.domain.behavior.RuleDefinition;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.metamodel.PageDefinition;
import com.pdp.domainconfig.domain.metamodel.ViewDefinition;

import java.util.List;

/**
 * 领域包版本清单值对象（manifest JSON 解析后的内存表示）。
 *
 * <p>对应 {@code domain-package.schema.json} 顶层结构。由 {@code DomainPackageManifestParser}
 * 从 {@code DomainPackageVersion#manifestJson} 解析得到，作为校验服务的输入。
 *
 * <p>{@code extendsParentKey}/{@code extendsVersionRange}/{@code parentSnapshotId}
 * 来自 manifest 的 {@code extends} 对象；用于三层继承与版本兼容性校验。
 *
 * @param schemaVersion           清单 schema 版本（必须为 "1.0"）
 * @param stableKey               领域包稳定键
 * @param name                    领域包名称
 * @param layer                   领域包层级
 * @param semanticVersion         语义化版本
 * @param extendsParentKey        继承的父包稳定键；PLATFORM_STANDARD 为 null
 * @param extendsVersionRange     父包版本范围
 * @param parentSnapshotId        父包运行时快照 ID
 * @param coreFieldReuses         核心字段复用声明
 * @param objects                 对象定义列表
 * @param pages                   页面定义列表
 * @param views                   视图定义列表
 * @param rules                   规则定义列表
 * @param workflowBindings        工作流绑定列表
 * @param permissions             权限定义列表
 * @param extensions              扩展定义列表
 * @param overrides               覆盖定义列表
 */
public record DomainPackageManifest(
        String schemaVersion,
        String stableKey,
        String name,
        String layer,
        String semanticVersion,
        String extendsParentKey,
        String extendsVersionRange,
        String parentSnapshotId,
        List<com.pdp.domainconfig.domain.packageversion.DomainPackageCoreFieldReuse> coreFieldReuses,
        List<ObjectDefinition> objects,
        List<PageDefinition> pages,
        List<ViewDefinition> views,
        List<RuleDefinition> rules,
        List<DomainPackageWorkflowBinding> workflowBindings,
        List<PermissionDefinition> permissions,
        List<ExtensionDefinition> extensions,
        List<OverrideDefinition> overrides) {

    public DomainPackageManifest {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion 不能为空");
        }
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (semanticVersion == null || semanticVersion.isBlank()) {
            throw new IllegalArgumentException("semanticVersion 不能为空");
        }
        coreFieldReuses = coreFieldReuses == null ? List.of() : List.copyOf(coreFieldReuses);
        objects = objects == null ? List.of() : List.copyOf(objects);
        pages = pages == null ? List.of() : List.copyOf(pages);
        views = views == null ? List.of() : List.copyOf(views);
        rules = rules == null ? List.of() : List.copyOf(rules);
        workflowBindings = workflowBindings == null ? List.of() : List.copyOf(workflowBindings);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
    }
}

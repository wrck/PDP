package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包聚合根（FR-007、FR-008、FR-013、FR-167）。
 *
 * <p>领域包是可版本、组合、发布、迁移和停用的业务能力单元，承载对象、字段、关系、页面、
 * 状态机、规则、动作、权限与集成映射的声明式配置。采用三层继承：平台标准包 → 行业领域包 →
 * 工作空间客户包。
 *
 * <p><strong>职责分离</strong>（FR-167、US2 验收场景 3）：{@code designer}（设计者）与
 * {@code publisher}（独立发布者）必须为不同主体，由 {@link #ensureDesignerPublisherSeparation}
 * 在创建时强制校验。
 *
 * <p><strong>状态机</strong>（spec.md §197）：
 * <ul>
 *   <li>{@link DomainPackageStatus#DRAFT} → {@link DomainPackageStatus#ACTIVE}：
 *       首个版本发布后激活；</li>
 *   <li>{@link DomainPackageStatus#ACTIVE} → {@link DomainPackageStatus#DEPRECATED}：
 *       所有版本弃用后包弃用；</li>
 *   <li>{@link DomainPackageStatus#DEPRECATED} → {@link DomainPackageStatus#RETIRED}：
 *       所有版本退役后包退役。</li>
 * </ul>
 *
 * <p><strong>乐观锁</strong>：{@code revision} 字段用于 If-Match 头并发控制。
 */
public record DomainPackage(
        UUID id,
        UUID workspaceId,
        String stableKey,
        String name,
        String description,
        DomainPackageLayer layer,
        UUID parentPackageId,
        DomainPackageStatus status,
        PrincipalRef designer,
        PrincipalRef publisher,
        UUID currentPublishedVersionId,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public DomainPackage {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (layer == null) {
            throw new IllegalArgumentException("layer 不能为 null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        if (designer == null) {
            throw new IllegalArgumentException("designer 不能为 null");
        }
        if (publisher == null) {
            throw new IllegalArgumentException("publisher 不能为 null");
        }
        // FR-013 三层继承：PLATFORM_STANDARD 不允许有父包；INDUSTRY 必须继承 PLATFORM_STANDARD；
        // WORKSPACE_CUSTOMER 必须继承 INDUSTRY。父子校验在应用服务通过仓储加载父包后完成。
        if (layer == DomainPackageLayer.PLATFORM_STANDARD && parentPackageId != null) {
            throw new IllegalArgumentException("PLATFORM_STANDARD 层级不允许有父包");
        }
        if (layer != DomainPackageLayer.PLATFORM_STANDARD && parentPackageId == null) {
            throw new IllegalArgumentException("非 PLATFORM_STANDARD 层级必须指定父包");
        }
        ensureDesignerPublisherSeparation(designer, publisher);
    }

    /** FR-167、US2 验收场景 3：设计者与发布者必须为不同主体。 */
    public static void ensureDesignerPublisherSeparation(PrincipalRef designer, PrincipalRef publisher) {
        if (designer != null && publisher != null && designer.sameAs(publisher)) {
            throw new IllegalArgumentException("设计者与发布者必须为不同主体（FR-167、US2 验收场景 3）");
        }
    }

    public boolean isDraft() {
        return status == DomainPackageStatus.DRAFT;
    }

    public boolean isActive() {
        return status == DomainPackageStatus.ACTIVE;
    }

    public boolean isDeprecated() {
        return status == DomainPackageStatus.DEPRECATED;
    }

    public boolean isRetired() {
        return status == DomainPackageStatus.RETIRED;
    }

    /** DRAFT → ACTIVE 是否合法（首个版本发布后激活）。 */
    public boolean canActivate() {
        return status == DomainPackageStatus.DRAFT;
    }

    /** ACTIVE → DEPRECATED 是否合法。 */
    public boolean canDeprecate() {
        return status == DomainPackageStatus.ACTIVE;
    }

    /** DEPRECATED → RETIRED 是否合法。 */
    public boolean canRetire() {
        return status == DomainPackageStatus.DEPRECATED;
    }
}

package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalogEntry;
import com.pdp.domainconfig.domain.metamodel.CoreFieldSource;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 平台核心字段目录仓储端口（FR-132、SC-025）。
 *
 * <p>核心字段由平台维护统一规范，领域包扩展字段必须通过 {@link DomainPackageCoreFieldReuseRepository}
 * 声明与核心字段的关系。{@code stableKey + coreObjectType} 唯一。
 */
public interface CoreFieldCatalogRepository {

    Optional<CoreFieldCatalogEntry> findById(UUID id);

    /** 按稳定键与核心对象类型查找。 */
    Optional<CoreFieldCatalogEntry> findByStableKeyAndObjectType(String stableKey, String coreObjectType);

    /** 按核心对象类型分页查询。 */
    PageResult<CoreFieldCatalogEntry> findByObjectType(String coreObjectType, PageRequest pageRequest);

    /** 按来源分页查询（用于按 ISO_21500/PMI_LEXICON/BPMN_2_0_2/PDP_EXTENSION 筛选）。 */
    PageResult<CoreFieldCatalogEntry> findBySource(CoreFieldSource source, PageRequest pageRequest);

    /** 全量分页查询。 */
    PageResult<CoreFieldCatalogEntry> findAll(PageRequest pageRequest);

    /** 插入新核心字段；stableKey+coreObjectType 唯一性由 uniq_core_field_key_object 索引保证。 */
    void save(CoreFieldCatalogEntry entry);

    /**
     * 更新核心字段标签、语义、允许覆盖标记、别名与数据类型，并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean update(UUID id, String label, String semantics, boolean allowedOverride,
                   Set<String> aliases, com.pdp.domainconfig.domain.metamodel.DataType dataType,
                   int expectedRevision, Instant now);
}

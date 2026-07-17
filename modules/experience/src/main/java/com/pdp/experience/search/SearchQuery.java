package com.pdp.experience.search;

import com.pdp.shared.context.OperatorContext;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.page.PageRequest;

import java.util.Objects;
import java.util.Set;

/**
 * 搜索查询值对象。
 *
 * <p>封装跨项目、跨对象搜索请求（US14）。查询 MUST 始终携带操作者上下文，由 {@link SearchProjectionPort}
 * 在投影层和结果集合层双重过滤权限，确保结果只包含有权对象，且打开结果时再次校验（FR/US14）。
 *
 * <p>核心契约：
 * <ul>
 *   <li>{@code workspaceId} 限定搜索范围，跨空间搜索需显式声明 {@code additionalWorkspaces}
 *       且命中有效协作授权；</li>
 *   <li>{@code objectTypes} 为空表示跨对象类型搜索，非空表示限定类型；</li>
 *   <li>{@code structuredFilters} 提供结构化过滤（如项目 ID、状态、负责人），与全文检索叠加，
 *       最终匹配集合以平台投影契约为准，不依赖数据库原生过滤；</li>
 *   <li>{@code analyzerVersion} 由调用方传入当前分析器版本，端口校验文档版本兼容性。</li>
 * </ul>
 *
 * @param workspaceId         主搜索工作空间
 * @param additionalWorkspaces 跨空间搜索的额外工作空间（须命中协作授权），可为空
 * @param objectTypes         限定对象类型集合，空表示跨对象
 * @param fullText            全文检索关键词（已由分析器规范化的查询词，或原始词由端口分析）
 * @param structuredFilters   结构化过滤条件（稳定键 → 值集合），可为空
 * @param pageRequest         分页请求
 * @param operator            操作者上下文（权限过滤依据）
 * @param analyzerVersion     当前分析器版本
 */
public record SearchQuery(
        WorkspaceId workspaceId,
        Set<WorkspaceId> additionalWorkspaces,
        Set<SearchObjectType> objectTypes,
        String fullText,
        java.util.Map<String, Set<String>> structuredFilters,
        PageRequest pageRequest,
        OperatorContext operator,
        AnalyzerVersion analyzerVersion) {

    public SearchQuery {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(pageRequest, "pageRequest 不能为 null");
        Objects.requireNonNull(operator, "operator 不能为 null");
        Objects.requireNonNull(analyzerVersion, "analyzerVersion 不能为 null");
        additionalWorkspaces = additionalWorkspaces == null ? Set.of() : Set.copyOf(additionalWorkspaces);
        objectTypes = objectTypes == null ? Set.of() : Set.copyOf(objectTypes);
        structuredFilters = structuredFilters == null
                ? java.util.Map.of()
                : java.util.Map.copyOf(structuredFilters);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 是否为跨空间搜索。 */
    public boolean isCrossWorkspace() {
        return !additionalWorkspaces.isEmpty();
    }

    /** 是否为跨对象类型搜索。 */
    public boolean isCrossObjectType() {
        return objectTypes.isEmpty();
    }

    /** 全文检索是否为空。 */
    public boolean hasFullText() {
        return fullText != null && !fullText.isBlank();
    }

    /**
     * 查询构建器。
     */
    public static final class Builder {
        private WorkspaceId workspaceId;
        private Set<WorkspaceId> additionalWorkspaces = Set.of();
        private Set<SearchObjectType> objectTypes = Set.of();
        private String fullText;
        private java.util.Map<String, Set<String>> structuredFilters = java.util.Map.of();
        private PageRequest pageRequest;
        private OperatorContext operator;
        private AnalyzerVersion analyzerVersion = AnalyzerVersion.P1_INITIAL;

        public Builder workspace(WorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder additionalWorkspaces(Set<WorkspaceId> workspaces) {
            this.additionalWorkspaces = workspaces;
            return this;
        }

        public Builder objectTypes(SearchObjectType... types) {
            this.objectTypes = Set.of(types);
            return this;
        }

        public Builder fullText(String text) {
            this.fullText = text;
            return this;
        }

        public Builder structuredFilter(String key, Set<String> values) {
            this.structuredFilters = java.util.Map.of(key, values);
            return this;
        }

        public Builder page(PageRequest pageRequest) {
            this.pageRequest = pageRequest;
            return this;
        }

        public Builder operator(OperatorContext operator) {
            this.operator = operator;
            return this;
        }

        public Builder analyzer(AnalyzerVersion version) {
            this.analyzerVersion = version;
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(workspaceId, additionalWorkspaces, objectTypes,
                    fullText, structuredFilters, pageRequest, operator, analyzerVersion);
        }
    }
}

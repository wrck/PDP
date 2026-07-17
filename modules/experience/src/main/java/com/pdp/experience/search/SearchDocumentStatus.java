package com.pdp.experience.search;

/**
 * 搜索文档状态枚举（稳定键）。
 *
 * <p>对应数据模型 {@code status} 列。控制搜索文档在结果集合中的可见性，用于实现权限撤销时效
 * （SC-036：搜索 30 秒内移除）、归档隔离和软删除场景。
 *
 * <p>状态使用稳定字符串键持久化，禁止依赖枚举序号。
 */
public enum SearchDocumentStatus {

    /** 可见：进入搜索结果集合（仍需通过权限过滤）。 */
    VISIBLE("VISIBLE"),
    /** 隐藏：不进入结果集合，但保留索引（如临时降级、待重建）。 */
    HIDDEN("HIDDEN"),
    /** 陈旧：源对象 revision 偏差，搜索结果可能不一致，MUST 标记或回查主库。 */
    STALE("STALE"),
    /** 已移除：源对象已删除或永久失去搜索可见性，索引可被清理。 */
    REMOVED("REMOVED");

    private final String stableKey;

    SearchDocumentStatus(String stableKey) {
        this.stableKey = stableKey;
    }

    public String stableKey() {
        return stableKey;
    }

    public static SearchDocumentStatus fromStableKey(String stableKey) {
        for (SearchDocumentStatus s : values()) {
            if (s.stableKey.equals(stableKey)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知搜索文档状态稳定键: " + stableKey);
    }
}

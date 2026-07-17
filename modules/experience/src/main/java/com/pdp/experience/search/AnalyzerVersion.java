package com.pdp.experience.search;

import java.util.Objects;

/**
 * 分析器版本值对象（语义版本）。
 *
 * <p>对应数据模型 {@code analyzer_version} 列。同一版本和同一数据集 MUST 产生相同词项、
 * 匹配集合和稳定业务排序（SC-033）。分析器升级时递增版本，旧版本投影 MUST 触发后台重建，
 * 重建完成前不得用于约束或流程判断（persistence-design.md 第 7 节）。
 *
 * <p>版本字符串采用 {@code MAJOR.MINOR.PATCH} 语义版本格式：MAJOR 不兼容变更（分词/规范化规则变化）、
 * MINOR 词表扩展（停用词新增）、PATCH 修复（不影响输出）。
 *
 * @param major 主版本
 * @param minor 次版本
 * @param patch 修订号
 */
public record AnalyzerVersion(int major, int minor, int patch) implements Comparable<AnalyzerVersion> {

    /** P1 平台统一分析器初始版本。 */
    public static final AnalyzerVersion P1_INITIAL = new AnalyzerVersion(1, 0, 0);

    public AnalyzerVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("版本号不能为负");
        }
    }

    /**
     * 解析语义版本字符串。
     *
     * @param version 形如 {@code 1.0.0} 的版本字符串
     * @return 分析器版本
     * @throws IllegalArgumentException 格式不合法
     */
    public static AnalyzerVersion parse(String version) {
        Objects.requireNonNull(version, "version 不能为 null");
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("分析器版本必须为 MAJOR.MINOR.PATCH 格式: " + version);
        }
        try {
            return new AnalyzerVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("分析器版本号必须为整数: " + version, e);
        }
    }

    /** 持久化用稳定字符串。 */
    public String stableKey() {
        return major + "." + minor + "." + patch;
    }

    /**
     * 是否兼容另一个版本（词项集合与匹配语义不变）。
     * <p>MAJOR 一致视为兼容；MAJOR 不同 MUST 触发后台重建。
     */
    public boolean isCompatibleWith(AnalyzerVersion other) {
        Objects.requireNonNull(other, "other 不能为 null");
        return this.major == other.major;
    }

    @Override
    public int compareTo(AnalyzerVersion other) {
        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return stableKey();
    }
}

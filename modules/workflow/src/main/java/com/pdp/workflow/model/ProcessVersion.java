package com.pdp.workflow.model;

import java.util.Objects;

/**
 * 流程定义业务版本值对象（FR-174）。
 *
 * <p>采用语义化版本（Semantic Versioning，{@code MAJOR.MINOR.PATCH}），
 * 对应 OpenAPI 契约 {@code businessVersion} 字段。
 *
 * <p><strong>版本语义</strong>：
 * <ul>
 *   <li>{@code MAJOR}：流程结构或状态机不兼容变化（已启动实例 MUST 迁移）；</li>
 *   <li>{@code MINOR}：新增节点或网关，向后兼容；</li>
 *   <li>{@code PATCH}：修复与配置调整，不影响运行中实例。</li>
 * </ul>
 *
 * <p>{@code (processDefinitionKey, businessVersion)} 唯一标识一个流程定义版本。
 * 已启动实例固定为启动时版本，迁移走 {@code WorkflowAdministrationPort}。
 */
public record ProcessVersion(int major, int minor, int patch) {

    /** 语义化版本正则。 */
    public static final String PATTERN = "^[0-9]+\\.[0-9]+\\.[0-9]+$";

    public ProcessVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("版本号必须为非负整数");
        }
    }

    /**
     * 从语义化版本字符串解析。
     *
     * @param value 语义化版本字符串（如 {@code "1.2.3"}）
     * @return 版本对象
     * @throws IllegalArgumentException 格式不匹配
     */
    public static ProcessVersion of(String value) {
        Objects.requireNonNull(value, "版本字符串不能为 null");
        if (!value.matches(PATTERN)) {
            throw new IllegalArgumentException("版本必须匹配 " + PATTERN + "，实际为 " + value);
        }
        String[] parts = value.split("\\.");
        return new ProcessVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }

    public static ProcessVersion of(int major, int minor, int patch) {
        return new ProcessVersion(major, minor, patch);
    }

    /**
     * 是否比 {@code other} 更新（按语义化版本比较）。
     *
     * @param other 另一版本
     * @return true 表示当前版本更新
     */
    public boolean isNewerThan(ProcessVersion other) {
        Objects.requireNonNull(other, "other 不能为 null");
        if (major != other.major) return major > other.major;
        if (minor != other.minor) return minor > other.minor;
        return patch > other.patch;
    }

    /**
     * 是否为主版本不兼容（major 不同）。
     *
     * @param other 另一版本
     * @return true 表示主版本不兼容，已启动实例需迁移
     */
    public boolean isIncompatibleWith(ProcessVersion other) {
        Objects.requireNonNull(other, "other 不能为 null");
        return major != other.major;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}

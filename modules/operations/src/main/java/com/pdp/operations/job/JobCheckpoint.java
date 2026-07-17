package com.pdp.operations.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * 后台作业检查点值对象（断点恢复用）。
 *
 * <p>对应表 {@code background_job.checkpoint}（JSON）列。作业暂停、失败或重试时持久化当前进度，
 * 恢复时从检查点继续，避免重复处理已成功条目（spec.md："重试必须从持久化检查点恢复"）。
 *
 * <p>检查点 MUST 与作业类型语义匹配，由 {@link JobHandler} 实现负责序列化和反序列化。
 * 平台仅保证检查点的存储、恢复和版本化（{@code revision} 用于乐观锁）。
 *
 * @param processedItems    已处理条目数
 * @param totalItems        总条目数（未知为 -1）
 * @param lastProcessedKey  最后处理条目的稳定键（用于断点续传定位）
 * @param stateJson         作业特定状态（如迁移批次 ID、导出文件分片、投影版本等）
 * @param revision          检查点版本（每次更新递增，用于乐观锁）
 */
public record JobCheckpoint(
        int processedItems,
        int totalItems,
        String lastProcessedKey,
        JsonNode stateJson,
        int revision) {

    public JobCheckpoint {
        if (processedItems < 0) {
            throw new IllegalArgumentException("processedItems 不能为负: " + processedItems);
        }
        if (totalItems < -1) {
            throw new IllegalArgumentException("totalItems 不能小于 -1: " + totalItems);
        }
        if (totalItems >= 0 && processedItems > totalItems) {
            throw new IllegalArgumentException("processedItems 不能大于 totalItems");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负: " + revision);
        }
    }

    /** 初始检查点（作业启动前）。 */
    public static JobCheckpoint initial(int totalItems) {
        return new JobCheckpoint(0, totalItems, null,
                JsonNodeFactory.instance.objectNode(), 0);
    }

    /** 空检查点（无状态作业或恢复时无历史检查点）。 */
    public static JobCheckpoint empty() {
        return new JobCheckpoint(0, -1, null, JsonNodeFactory.instance.objectNode(), 0);
    }

    /**
     * 推进检查点。
     *
     * @param newProcessedItems 新的已处理条目数
     * @param newLastKey        新的最后处理键
     * @param newStateJson      新的状态 JSON
     * @return 新版本检查点（revision 递增）
     */
    public JobCheckpoint advance(int newProcessedItems, String newLastKey, JsonNode newStateJson) {
        if (newProcessedItems < processedItems) {
            throw new IllegalArgumentException(
                    "新 processedItems 不能小于当前值: " + newProcessedItems + " < " + processedItems);
        }
        return new JobCheckpoint(
                newProcessedItems,
                totalItems,
                newLastKey,
                Objects.requireNonNullElse(newStateJson, JsonNodeFactory.instance.objectNode()),
                revision + 1);
    }

    /** 进度百分比 [0, 100]，totalItems 未知返回 0。 */
    public int progressPercent() {
        if (totalItems <= 0) {
            return 0;
        }
        return Math.min(100, (int) ((long) processedItems * 100 / totalItems));
    }

    /** 是否已完成（processedItems >= totalItems 且 totalItems 已知）。 */
    public boolean isComplete() {
        return totalItems >= 0 && processedItems >= totalItems;
    }

    /** 合并状态 JSON 中的字段（用于增量更新）。 */
    public JobCheckpoint withStateField(String fieldName, String value) {
        ObjectNode node = stateJson.isObject()
                ? stateJson.deepCopy()
                : JsonNodeFactory.instance.objectNode();
        node.put(fieldName, value);
        return new JobCheckpoint(processedItems, totalItems, lastProcessedKey, node, revision + 1);
    }
}

package com.pdp.experience.search;

import com.pdp.shared.context.ActorRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 搜索索引事件（异步投影）。
 *
 * <p>对应 spec.md 第 8 节："投影异步时响应提供 {@code indexedAt}；需要强一致的精确查询回查主库"。
 * 业务对象变更通过事务事件日志（Outbox）异步触发搜索投影重建，本事件作为 Outbox 载荷，
 * 由后台作业协调器（T070）消费并调用 {@link SearchProjectionPort#index(SearchIndexingEvent)}。
 *
 * <p>事件类型：
 * <ul>
 *   <li>{@link Type#UPSERT}：新增或更新文档（携带最新源对象 revision）；</li>
 *   <li>{@link Type#HIDE}：临时隐藏（如对象进入不可搜索状态），保留索引；</li>
 *   <li>{@link Type#REMOVE}：永久移除（对象删除或失去搜索可见性），索引可清理；</li>
 *   <li>{@link Type#REBUILD}：分析器版本升级或投影定义变更触发的全量重建。</li>
 * </ul>
 *
 * <p><strong>幂等性</strong>：{@code eventId}（UUIDv7）作为 Outbox 幂等键，重复投递 MUST 不产生
 * 重复索引更新（US14：相同核心事件重复投递不产生重复通知/索引）。
 *
 * @param eventId           事件 ID（UUIDv7，幂等键）
 * @param type              事件类型
 * @param objectRef         业务对象引用
 * @param sourceRevision    源对象当前 revision（用于检测投影偏差）
 * @param title             标题（UPSERT/REBUILD 时必填）
 * @param rawText           原始全文（UPSERT/REBUILD 时必填，由分析器规范化）
 * @param triggeredBy       触发者
 * @param occurredAt        事件发生时间
 * @param targetAnalyzerVersion 目标分析器版本（REBUILD 时必填，其他类型可空表示沿用当前版本）
 */
public record SearchIndexingEvent(
        UUID eventId,
        Type type,
        ObjectRef objectRef,
        int sourceRevision,
        String title,
        String rawText,
        ActorRef triggeredBy,
        Instant occurredAt,
        AnalyzerVersion targetAnalyzerVersion) {

    public SearchIndexingEvent {
        Objects.requireNonNull(eventId, "eventId 不能为 null");
        Objects.requireNonNull(type, "type 不能为 null");
        Objects.requireNonNull(objectRef, "objectRef 不能为 null");
        Objects.requireNonNull(triggeredBy, "triggeredBy 不能为 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
        if (sourceRevision < 0) {
            throw new IllegalArgumentException("sourceRevision 不能为负");
        }
        if ((type == Type.UPSERT || type == Type.REBUILD) && (title == null || title.isBlank())) {
            throw new IllegalArgumentException(type + " 事件必须携带 title");
        }
        if (type == Type.REBUILD && targetAnalyzerVersion == null) {
            throw new IllegalArgumentException("REBUILD 事件必须携带 targetAnalyzerVersion");
        }
    }

    /**
     * 构造 UPSERT 事件。
     */
    public static SearchIndexingEvent upsert(
            ObjectRef objectRef,
            int sourceRevision,
            String title,
            String rawText,
            ActorRef triggeredBy,
            Instant occurredAt) {
        return new SearchIndexingEvent(
                com.pdp.shared.id.UuidV7Generator.next(), Type.UPSERT, objectRef,
                sourceRevision, title, rawText, triggeredBy, occurredAt, null);
    }

    /**
     * 构造 HIDE 事件。
     */
    public static SearchIndexingEvent hide(
            ObjectRef objectRef,
            int sourceRevision,
            ActorRef triggeredBy,
            Instant occurredAt) {
        return new SearchIndexingEvent(
                com.pdp.shared.id.UuidV7Generator.next(), Type.HIDE, objectRef,
                sourceRevision, null, null, triggeredBy, occurredAt, null);
    }

    /**
     * 构造 REMOVE 事件。
     */
    public static SearchIndexingEvent remove(
            ObjectRef objectRef,
            int sourceRevision,
            ActorRef triggeredBy,
            Instant occurredAt) {
        return new SearchIndexingEvent(
                com.pdp.shared.id.UuidV7Generator.next(), Type.REMOVE, objectRef,
                sourceRevision, null, null, triggeredBy, occurredAt, null);
    }

    /**
     * 构造 REBUILD 事件（分析器版本升级或投影定义变更）。
     */
    public static SearchIndexingEvent rebuild(
            ObjectRef objectRef,
            int sourceRevision,
            String title,
            String rawText,
            ActorRef triggeredBy,
            Instant occurredAt,
            AnalyzerVersion targetAnalyzerVersion) {
        return new SearchIndexingEvent(
                com.pdp.shared.id.UuidV7Generator.next(), Type.REBUILD, objectRef,
                sourceRevision, title, rawText, triggeredBy, occurredAt, targetAnalyzerVersion);
    }

    /**
     * 事件类型。
     */
    public enum Type {
        /** 新增或更新文档。 */
        UPSERT,
        /** 临时隐藏（保留索引）。 */
        HIDE,
        /** 永久移除（索引可清理）。 */
        REMOVE,
        /** 全量重建（分析器版本升级或投影定义变更）。 */
        REBUILD
    }
}

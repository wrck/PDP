package com.pdp.shared.operation;

import com.pdp.shared.id.UuidV7Generator;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 高风险操作影响预览（FR-168）。
 *
 * <p>对应 spec.md "高风险操作目录" 中"必需预览与确认"列：
 * <ul>
 *   <li>领域包发布：版本差异、受影响对象、冲突、不可逆变更和迁移批次</li>
 *   <li>项目/阶段回退：未完成事项、影响交付件、审批、子项目和统计口径</li>
 *   <li>基线替换：原值、新值、原因、期限、贡献变化和审批人</li>
 *   <li>交付件发布：内容哈希、版本、签核、对象状态和下游影响</li>
 *   <li>数据导出/处置：数据范围、敏感级别、数量、保留约束和不可逆点</li>
 *   <li>历史迁移/数据库切换：映射、数量、关系、增量位点、差异、冻结窗口和单写主权</li>
 * </ul>
 *
 * <p><strong>预览有效期</strong>：{@link #expiresAt} 之后预览失效，调用方 MUST 重新生成；
 * 期间发生并发版本变化（业务对象 revision 变化）也 MUST 重新生成（由 {@link #sourceRevision} 检测）。
 *
 * <p><strong>版本化</strong>：每次重新生成递增 {@link #version}，操作者确认时 MUST 引用具体版本号；
 * 执行时校验版本一致，防止"预览 A → 修改 → 执行 B"。
 *
 * @param previewId        预览 ID（UUIDv7）
 * @param operationType    操作类型
 * @param scope            操作范围（如工作空间 ID、项目 ID 或迁移批次键）
 * @param version          预览版本（从 1 递增）
 * @param summary          影响摘要（人类可读）
 * @param items            影响条目列表（结构化）
 * @param pointOfNoReturn  不可逆点描述（null 表示无不可逆点）
 * @param sourceRevision   源对象 revision 快照（用于并发变化检测）
 * @param generatedAt      生成时间
 * @param expiresAt        过期时间（过期后预览失效，需重新生成）
 */
public record ImpactPreview(
        UUID previewId,
        HighRiskOperationType operationType,
        String scope,
        int version,
        String summary,
        List<ImpactItem> items,
        PointOfNoReturn pointOfNoReturn,
        int sourceRevision,
        Instant generatedAt,
        Instant expiresAt) {

    public ImpactPreview {
        Objects.requireNonNull(previewId, "previewId 不能为空");
        Objects.requireNonNull(operationType, "operationType 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope 不能为空白");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version 必须为正");
        }
        Objects.requireNonNull(summary, "summary 不能为空");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary 不能为空白");
        }
        items = items == null ? List.of() : List.copyOf(items);
        Objects.requireNonNull(generatedAt, "generatedAt 不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(generatedAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 generatedAt");
        }
        if (sourceRevision < 0) {
            throw new IllegalArgumentException("sourceRevision 不能为负");
        }
    }

    /**
     * 创建新预览（version=1）。
     *
     * @param operationType   操作类型
     * @param scope           操作范围
     * @param summary         影响摘要
     * @param items           影响条目
     * @param pointOfNoReturn 不可逆点（可空）
     * @param sourceRevision  源对象 revision
     * @param ttlSeconds      有效期（秒）
     * @return 新预览
     */
    public static ImpactPreview create(
            HighRiskOperationType operationType,
            String scope,
            String summary,
            List<ImpactItem> items,
            PointOfNoReturn pointOfNoReturn,
            int sourceRevision,
            long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds 必须为正");
        }
        Instant now = Instant.now();
        return new ImpactPreview(
                UuidV7Generator.next(), operationType, scope, 1, summary,
                items, pointOfNoReturn, sourceRevision, now, now.plusSeconds(ttlSeconds));
    }

    /**
     * 重新生成预览（递增 version，保留 previewId 实现版本链追踪）。
     *
     * @param newSummary       新摘要
     * @param newItems         新影响条目
     * @param newPointOfNoReturn 新不可逆点
     * @param newSourceRevision 新源对象 revision
     * @param ttlSeconds       有效期（秒）
     * @return 新版本预览
     */
    public ImpactPreview regenerate(
            String newSummary,
            List<ImpactItem> newItems,
            PointOfNoReturn newPointOfNoReturn,
            int newSourceRevision,
            long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds 必须为正");
        }
        Instant now = Instant.now();
        return new ImpactPreview(
                previewId, operationType, scope, version + 1,
                newSummary, newItems, newPointOfNoReturn,
                newSourceRevision, now, now.plusSeconds(ttlSeconds));
    }

    /**
     * 预览是否已过期。
     *
     * @param now 当前时间
     * @return true 表示已过期
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 源对象 revision 是否已变化（并发修改检测）。
     *
     * @param currentRevision 当前源对象 revision
     * @return true 表示并发变化，预览已失效
     */
    public boolean isStaleFor(int currentRevision) {
        return sourceRevision != currentRevision;
    }

    /**
     * 是否包含不可逆变更。
     */
    public boolean hasIrreversibleImpact() {
        return pointOfNoReturn != null
                || items.stream().anyMatch(ImpactItem::irreversible);
    }

    /**
     * 受影响对象总数。
     */
    public int totalAffectedObjects() {
        return items.stream().mapToInt(ImpactItem::affectedObjectCount).sum();
    }
}

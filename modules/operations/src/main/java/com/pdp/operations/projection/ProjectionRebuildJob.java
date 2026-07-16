package com.pdp.operations.projection;

import com.pdp.operations.job.BackgroundJobCoordinator.BatchContext;
import com.pdp.operations.job.BackgroundJobCoordinator.BatchResult;
import com.pdp.operations.job.BackgroundJobCoordinator.FailureItem;
import com.pdp.operations.job.BackgroundJobCoordinator.JobProcessor;
import com.pdp.operations.job.BackgroundJobCoordinator.JobType;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 通过稳定源/目标端口重建非权威投影。
 *
 * <p>目标端口必须按对象键和版本实现幂等写入。任一条目失败时，本批次检查点不前移；恢复后允许安全
 * 重放已经成功写入的同批条目，避免产生无法定位的投影缺口。
 */
public final class ProjectionRebuildJob implements JobProcessor {
    public static final String PROJECTION_KEY_SCOPE = "projectionKey";

    private final ProjectionSource source;
    private final ProjectionTarget target;
    private final Clock clock;

    public ProjectionRebuildJob(ProjectionSource source, ProjectionTarget target, Clock clock) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECTION_REBUILD;
    }

    @Override
    public BatchResult process(BatchContext context) {
        String projectionKey = requireProjectionKey(context.scope());
        ProjectionPage page =
                Objects.requireNonNull(
                        source.read(
                                context.workspaceId(),
                                projectionKey,
                                context.checkpoint().cursor(),
                                context.batchSize()),
                        "投影源不得返回 null");
        if (page.items().size() > context.batchSize()) {
            throw new IllegalStateException("投影源返回条目数超过批次限制");
        }
        if (!page.complete() && page.items().isEmpty()) {
            throw new IllegalStateException("未完成的投影页不能为空");
        }
        if (!page.complete()
                && Objects.equals(page.nextCursor(), context.checkpoint().cursor())) {
            throw new IllegalStateException("未完成的投影页必须推进游标");
        }
        for (ProjectionRecord record : page.items()) {
            try {
                target.apply(context.workspaceId(), projectionKey, record);
            } catch (RuntimeException exception) {
                return BatchResult.failed(
                        context.checkpoint().cursor(),
                        new FailureItem(
                                record.itemKey(),
                                "PROJECTION_REBUILD_ITEM_FAILED",
                                "投影条目写入失败: " + exception.getClass().getSimpleName(),
                                true,
                                clock.instant()));
            }
        }
        if (page.complete()) {
            return BatchResult.completed(
                    page.nextCursor(),
                    page.items().size(),
                    page.items().size(),
                    List.of(),
                    null);
        }
        return BatchResult.advanced(
                page.nextCursor(), page.items().size(), page.items().size(), List.of());
    }

    private static String requireProjectionKey(Map<String, Object> scope) {
        Object value = scope.get(PROJECTION_KEY_SCOPE);
        if (!(value instanceof String projectionKey) || projectionKey.isBlank()) {
            throw new IllegalArgumentException("投影重建范围缺少 projectionKey");
        }
        return projectionKey.strip();
    }

    public enum Operation {
        UPSERT,
        REMOVE
    }

    public record ProjectionRecord(
            String itemKey, long revision, Operation operation, Map<String, Object> payload) {
        public ProjectionRecord {
            if (itemKey == null || itemKey.isBlank()) {
                throw new IllegalArgumentException("itemKey 不能为空");
            }
            itemKey = itemKey.strip();
            if (revision < 0) {
                throw new IllegalArgumentException("revision 不得为负数");
            }
            Objects.requireNonNull(operation, "operation");
            payload =
                    payload == null || payload.isEmpty()
                            ? Map.of()
                            : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    public record ProjectionPage(
            List<ProjectionRecord> items, String nextCursor, boolean complete) {
        public ProjectionPage {
            items = List.copyOf(items == null ? List.of() : items);
            if (!complete && (nextCursor == null || nextCursor.isBlank())) {
                throw new IllegalArgumentException("未完成的投影页必须提供 nextCursor");
            }
        }
    }

    @FunctionalInterface
    public interface ProjectionSource {
        ProjectionPage read(
                UUID workspaceId, String projectionKey, String checkpoint, int batchSize);
    }

    @FunctionalInterface
    public interface ProjectionTarget {
        void apply(UUID workspaceId, String projectionKey, ProjectionRecord record);
    }
}

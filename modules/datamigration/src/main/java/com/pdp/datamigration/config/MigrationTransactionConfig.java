package com.pdp.datamigration.config;

import com.pdp.shared.id.UuidV7Generator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;

/**
 * 迁移事务边界与批次上下文配置（T051）。
 *
 * <p>落实 persistence-design.md 第 9 节事务边界规则：
 * <ul>
 *   <li>每个批次在单一目标数据库事务内提交并保存检查点；跨源/目标不启用分布式事务。</li>
 *   <li>读取源批次、转换、写入目标和记录结果使用幂等批次键恢复。</li>
 *   <li>数据源路由上下文必须在 {@code finally} 中清理，线程池任务显式传递或重建上下文，
 *       禁止 ThreadLocal 泄漏。</li>
 * </ul>
 *
 * <p>事务管理器（{@code legacySourceTransactionManager}、{@code migrationTargetTransactionManager}）
 * 已由 {@link LegacySourceMybatisConfig} 和 {@link PdpTargetMybatisConfig} 提供；本配置专注于
 * 批次边界抽象与线程安全的上下文传播。
 *
 * <p><strong>XA 禁用</strong>：源库读和目标库写不在同一事务内，由 {@link MigrationBatchHolder}
 * 显式管理边界——读源（源事务）、转换（无事务）、写目标（目标事务，含检查点写入）、记录结果（目标事务）。
 * 任何阶段失败均通过批次键和检查点恢复，不依赖跨库原子性。
 */
@Configuration
@ConditionalOnProperty(name = "pdp.migration.enabled", havingValue = "true")
public class MigrationTransactionConfig {

    /**
     * 批次上下文持有者（线程局部），使用普通 {@link ThreadLocal}（非 {@code InheritableThreadLocal}），
     * 仅在显式调用 {@link MigrationBatchHolder#open(MigrationBatch)} 时设置，必须在 finally 中调用
     * {@link MigrationBatchHolder#close()} 清理，禁止线程池任务隐式继承——
     * 线程池任务必须通过 {@link MigrationBatchHolder#capture()} / {@link MigrationBatchHolder#restore(Object)}
     * 显式传递或重建上下文。
     */
    @Bean("migrationBatchHolder")
    public MigrationBatchHolder migrationBatchHolder() {
        return new MigrationBatchHolder();
    }

    /**
     * 批次执行器（占位实现，具体迁移任务在 Txxx 阶段补齐）。
     *
     * <p>当前仅暴露批次键生成与检查点记录 API；实际迁移读写逻辑由后续迁移任务实现。
     */
    @Bean("migrationBatchExecutor")
    public MigrationBatchExecutor migrationBatchExecutor() {
        return new MigrationBatchExecutor();
    }

    // ============================================================
    // 批次边界值对象
    // ============================================================

    /**
     * 迁移批次状态。
     *
     * <p>状态机：{@code QUEUED → RUNNING → COMPLETED}；失败后 {@code FAILED → RECOVERING → RUNNING}；
     * 不允许跳过 {@code RUNNING} 直接进入 {@code COMPLETED}。
     */
    public enum BatchStatus {
        QUEUED, RUNNING, COMPLETED, FAILED, RECOVERING;

        public boolean isTerminal() {
            return this == COMPLETED;
        }

        public boolean isRetryable() {
            return this == FAILED || this == RECOVERING;
        }
    }

    /**
     * 不可变迁移批次记录。
     *
     * <p>{@code batchKey} 为 UUIDv7，包含时间序，作为幂等键跨重启恢复；
     * {@code jobType} 标识迁移种类（如 PROJECT_FULL、DELIVERABLE_INCREMENTAL）；
     * {@code scope} 为迁移对象范围（如工作空间 ID 或源系统标识）。
     *
     * @param batchKey    幂等批次键（UUIDv7）
     * @param jobType     迁移类型
     * @param scope       迁移范围
     * @param batchSize   批次大小（行数）
     * @param startedAt   批次开始时间
     * @param status      批次状态
     * @param attempt     重试次数（首次为 1）
     */
    public record MigrationBatch(
            UUID batchKey,
            String jobType,
            String scope,
            int batchSize,
            Instant startedAt,
            BatchStatus status,
            int attempt) {

        public MigrationBatch {
            if (batchKey == null) {
                throw new IllegalArgumentException("batchKey 不能为空");
            }
            if (jobType == null || jobType.isBlank()) {
                throw new IllegalArgumentException("jobType 不能为空");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize 必须为正");
            }
            if (startedAt == null) {
                throw new IllegalArgumentException("startedAt 不能为空");
            }
            if (status == null) {
                throw new IllegalArgumentException("status 不能为空");
            }
            if (attempt <= 0) {
                throw new IllegalArgumentException("attempt 必须为正");
            }
        }

        /** 创建新批次（attempt=1, status=QUEUED）。 */
        public static MigrationBatch newBatch(String jobType, String scope, int batchSize) {
            return new MigrationBatch(
                    UuidV7Generator.next(), jobType, scope, batchSize,
                    Instant.now(), BatchStatus.QUEUED, 1);
        }

        /** 创建重试批次（attempt+1, status=RECOVERING, batchKey 保持不变实现幂等）。 */
        public MigrationBatch retry() {
            if (!status.isRetryable()) {
                throw new IllegalStateException("仅 FAILED 或 RECOVERING 状态可重试，当前: " + status);
            }
            return new MigrationBatch(
                    batchKey, jobType, scope, batchSize, Instant.now(),
                    BatchStatus.RECOVERING, attempt + 1);
        }

        public MigrationBatch running() {
            return new MigrationBatch(batchKey, jobType, scope, batchSize, startedAt,
                    BatchStatus.RUNNING, attempt);
        }

        public MigrationBatch completed() {
            return new MigrationBatch(batchKey, jobType, scope, batchSize, startedAt,
                    BatchStatus.COMPLETED, attempt);
        }

        public MigrationBatch failed() {
            return new MigrationBatch(batchKey, jobType, scope, batchSize, startedAt,
                    BatchStatus.FAILED, attempt);
        }
    }

    /**
     * 批次检查点（持久化到目标库，跨重启恢复用）。
     *
     * <p>每个检查点记录已处理行数、最后处理键（游标位置）和自定义状态 JSON。
     * 检查点写入与业务数据写入位于同一目标库事务，确保原子性。
     *
     * @param batchKey          关联批次键
     * @param processedItems    已处理行数
     * @param totalItems        总行数（未知为 null）
     * @param lastProcessedKey  最后处理键（源库游标位置）
     * @param stateJson         自定义状态 JSON（如分桶、统计）
     * @param recordedAt        检查点记录时间
     * @param revision          检查点 revision（乐观锁）
     */
    public record BatchCheckpoint(
            UUID batchKey,
            int processedItems,
            Integer totalItems,
            String lastProcessedKey,
            String stateJson,
            Instant recordedAt,
            int revision) {

        public BatchCheckpoint {
            if (batchKey == null) {
                throw new IllegalArgumentException("batchKey 不能为空");
            }
            if (processedItems < 0) {
                throw new IllegalArgumentException("processedItems 不能为负");
            }
            if (totalItems != null && totalItems < processedItems) {
                throw new IllegalArgumentException("totalItems 不得小于 processedItems");
            }
            if (revision <= 0) {
                throw new IllegalArgumentException("revision 必须为正");
            }
        }

        /** 初始检查点（processedItems=0, revision=1）。 */
        public static BatchCheckpoint initial(UUID batchKey, Integer totalItems) {
            return new BatchCheckpoint(batchKey, 0, totalItems, null, null, Instant.now(), 1);
        }

        /** 推进检查点（processedItems 增加，revision 递增）。 */
        public BatchCheckpoint advance(int delta, String lastProcessedKey, String stateJson) {
            if (delta < 0) {
                throw new IllegalArgumentException("delta 不能为负");
            }
            return new BatchCheckpoint(
                    batchKey, processedItems + delta, totalItems,
                    lastProcessedKey, stateJson, Instant.now(), revision + 1);
        }

        /** 完成度比例（0.0~1.0），totalItems 未知时返回 null。 */
        public Double progressRatio() {
            if (totalItems == null || totalItems == 0) {
                return null;
            }
            return Math.min(1.0, (double) processedItems / totalItems);
        }
    }

    // ============================================================
    // 批次上下文持有者
    // ============================================================

    /**
     * 线程局部批次上下文持有者。
     *
     * <p><strong>使用契约</strong>：
     * <pre>{@code
     * MigrationBatch batch = MigrationBatch.newBatch("PROJECT_FULL", "ws-123", 500);
     * try {
     *     holder.open(batch);
     *     // 执行迁移：读源、转换、写目标、记录检查点
     * } finally {
     *     holder.close();  // 必须清理，防止线程池复用泄漏
     * }
     * }</pre>
     *
     * <p><strong>线程池任务传递</strong>（禁止隐式继承）：
     * <pre>{@code
     * Object snapshot = holder.capture();          // 主线程捕获
     * executor.submit(() -> {
     *     try {
     *         holder.restore(snapshot);             // 工作线程显式恢复
     *         // 执行子任务
     *     } finally {
     *         holder.close();
     *     }
     * });
     * }</pre>
     *
     * <p>使用普通 {@link ThreadLocal}（非 {@code InheritableThreadLocal}），防止子线程隐式继承
     * 导致线程池任务意外读到主线程上下文；显式 {@link #restore(Object)} 是唯一跨线程传播路径。
     */
    public static final class MigrationBatchHolder {

        private final ThreadLocal<MigrationBatch> current = new ThreadLocal<>();

        /** 打开批次上下文（设置 ThreadLocal）。 */
        public void open(MigrationBatch batch) {
            if (batch == null) {
                throw new IllegalArgumentException("batch 不能为空");
            }
            if (current.get() != null) {
                throw new IllegalStateException("当前线程已持有批次上下文，必须先 close: "
                        + current.get().batchKey());
            }
            current.set(batch);
        }

        /** 获取当前批次（可能为空）。 */
        public MigrationBatch get() {
            return current.get();
        }

        /** 获取当前批次或抛出异常。 */
        public MigrationBatch require() {
            MigrationBatch batch = current.get();
            if (batch == null) {
                throw new IllegalStateException("当前线程未持有批次上下文");
            }
            return batch;
        }

        /** 替换当前批次（用于状态迁移，如 RUNNING → COMPLETED）。 */
        public void replace(MigrationBatch batch) {
            if (batch == null) {
                throw new IllegalArgumentException("batch 不能为空");
            }
            current.set(batch);
        }

        /**
         * 清理当前线程上下文。必须在 finally 中调用。
         * 若未持有上下文则无操作（幂等）。
         */
        public void close() {
            current.remove();
        }

        /**
         * 捕获当前线程上下文快照（用于线程池任务显式传递）。
         *
         * @return 上下文快照（不可变 MigrationBatch），未持有则返回 null
         */
        public MigrationBatch capture() {
            return current.get();
        }

        /**
         * 在当前线程恢复上下文快照（用于线程池任务显式重建）。
         *
         * <p>必须保证当前线程未持有上下文；调用方在 finally 中调用 {@link #close()} 清理。
         *
         * @param snapshot {@link #capture()} 返回的快照（必须为 MigrationBatch 或 null）
         */
        public void restore(Object snapshot) {
            if (snapshot != null && !(snapshot instanceof MigrationBatch)) {
                throw new IllegalArgumentException("snapshot 必须是 MigrationBatch");
            }
            if (current.get() != null) {
                throw new IllegalStateException("当前线程已持有批次上下文，必须先 close");
            }
            if (snapshot instanceof MigrationBatch batch) {
                current.set(batch);
            }
        }
    }

    /**
     * 批次执行器（占位 API，具体迁移逻辑在后续阶段实现）。
     *
     * <p>当前仅暴露批次/检查点构造工具，便于后续迁移任务复用。
     */
    public static final class MigrationBatchExecutor {

        /** 创建新批次。 */
        public MigrationBatch newBatch(String jobType, String scope, int batchSize) {
            return MigrationBatch.newBatch(jobType, scope, batchSize);
        }

        /** 重试批次（保留 batchKey 实现幂等）。 */
        public MigrationBatch retry(MigrationBatch failed) {
            return failed.retry();
        }

        /** 创建初始检查点。 */
        public BatchCheckpoint initialCheckpoint(MigrationBatch batch, Integer totalItems) {
            return BatchCheckpoint.initial(batch.batchKey(), totalItems);
        }

        /** 推进检查点。 */
        public BatchCheckpoint advanceCheckpoint(BatchCheckpoint current, int delta,
                                                  String lastProcessedKey, String stateJson) {
            return current.advance(delta, lastProcessedKey, stateJson);
        }
    }
}

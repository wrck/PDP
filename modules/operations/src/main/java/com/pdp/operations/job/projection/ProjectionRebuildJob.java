package com.pdp.operations.job.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdp.experience.search.AnalyzerVersion;
import com.pdp.experience.search.SearchDocument;
import com.pdp.experience.search.SearchDocumentStatus;
import com.pdp.experience.search.SearchIndexingEvent;
import com.pdp.experience.search.SearchObjectType;
import com.pdp.experience.search.SearchProjectionPort;
import com.pdp.operations.job.BackgroundJob;
import com.pdp.operations.job.BackgroundJobType;
import com.pdp.operations.job.JobCheckpoint;
import com.pdp.operations.job.JobContext;
import com.pdp.operations.job.JobExecutionResult;
import com.pdp.operations.job.JobExecutionException;
import com.pdp.operations.job.JobFailureItem;
import com.pdp.operations.job.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 搜索投影重建作业处理器（{@link BackgroundJobType#PROJECTION_REBUILD}）。
 *
 * <p>对应 spec.md 第 8 节"数据库无关搜索语义"和 persistence-design.md 第 7 节：
 * "投影定义变更必须触发后台重建，在完成前不得把新投影用于约束或流程判断"。
 *
 * <p>触发场景：
 * <ul>
 *   <li>分析器版本 MAJOR 升级（{@link AnalyzerVersion#isCompatibleWith} 返回 false）；</li>
 *   <li>字段权重配置变更；</li>
 *   <li>停用词词典扩展（MINOR 升级，可选重建）；</li>
 *   <li>投影定义变更（如新增可搜索字段）。</li>
 * </ul>
 *
 * <p>执行流程：
 * <ol>
 *   <li>从检查点恢复已处理位置（避免重复重建）；</li>
 *   <li>按对象类型分批拉取旧版本/全部 VISIBLE 文档（{@link SearchProjectionPort#listDocuments}）；</li>
 *   <li>对每个文档构造 {@link SearchIndexingEvent#rebuild} 事件，调用
 *       {@link SearchProjectionPort#index} 触发新版本投影构建；</li>
 *   <li>失败条目记录到 {@link JobContext#recordFailure}，继续处理后续条目；</li>
 *   <li>每批次通过 {@link JobContext#saveCheckpoint} 持久化进度；</li>
 *   <li>响应取消请求（{@link JobContext#isCancelRequested}）。</li>
 * </ol>
 *
 * <p><strong>幂等性</strong>：检查点记录最后处理的对象引用（对象类型 + 对象 ID），重复执行从断点继续，
 * 不产生重复重建。{@link SearchProjectionPort#index} 内部基于 eventId 幂等。
 *
 * <p><strong>重建输入</strong>：使用 {@link SearchDocument#normalizedText()} 作为重建输入。
 * 规范化文本已通过 NFC + 大小写折叠 + 空白压缩，再次规范化幂等（SC-033 一致性保证）。
 * 新分析器版本的停用词和分词规则将重新应用，产生新版本词项投影。
 *
 * <p><strong>资源预算</strong>：使用 {@link com.pdp.operations.job.JobResourceBudget#projectionRebuildBudget()}，
 * 2 个数据库连接、2 小时超时、5% 失败率阈值。
 */
@Component
public class ProjectionRebuildJob implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectionRebuildJob.class);
    private static final int BATCH_SIZE = 50;
    private static final String CK_LAST_OBJECT_TYPE = "lastObjectType";
    private static final String CK_LAST_OBJECT_ID = "lastObjectId";
    private static final String CK_TARGET_VERSION = "targetAnalyzerVersion";

    private final SearchProjectionPort searchProjectionPort;

    public ProjectionRebuildJob(SearchProjectionPort searchProjectionPort) {
        this.searchProjectionPort = Objects.requireNonNull(searchProjectionPort,
                "searchProjectionPort 不能为 null");
    }

    @Override
    public BackgroundJobType supportedType() {
        return BackgroundJobType.PROJECTION_REBUILD;
    }

    @Override
    public JobExecutionResult execute(JobContext context) {
        BackgroundJob job = context.job();
        LOG.info("开始搜索投影重建作业 {} (scope={})", job.id(), job.scope());

        AnalyzerVersion targetVersion = parseTargetVersion(job, context.checkpoint());

        // 首次执行：统计待重建文档总数
        JobCheckpoint checkpoint = context.checkpoint();
        if (checkpoint.totalItems() < 0) {
            long total = countAllVisibleDocuments();
            checkpoint = new JobCheckpoint(0, (int) Math.min(total, Integer.MAX_VALUE),
                    null, buildState(null, null, targetVersion), 0);
            context.saveCheckpoint(checkpoint);
            LOG.info("作业 {} 待重建文档总数: {}", job.id(), total);
        }

        int processed = checkpoint.processedItems();
        int totalItems = checkpoint.totalItems();
        List<JobFailureItem> failures = new ArrayList<>(context.failures());

        // 从检查点恢复游标
        String lastObjectTypeKey = parseStringState(checkpoint, CK_LAST_OBJECT_TYPE);
        UUID lastObjectId = parseUuidState(checkpoint, CK_LAST_OBJECT_ID);

        try {
            for (SearchObjectType type : SearchObjectType.values()) {
                // 跳过检查点之前的类型
                if (lastObjectTypeKey != null
                        && type.stableKey().compareTo(lastObjectTypeKey) < 0) {
                    continue;
                }

                int offset = 0;
                boolean skipUntilLastObject = lastObjectId != null
                        && (lastObjectTypeKey == null
                        || type.stableKey().equals(lastObjectTypeKey));

                while (true) {
                    // 响应取消
                    if (context.isCancelRequested()) {
                        LOG.info("作业 {} 收到取消请求，已处理 {}/{}", job.id(), processed, totalItems);
                        return JobExecutionResult.cancelled(
                                "用户取消，已处理 " + processed + "/" + totalItems,
                                buildCheckpoint(processed, totalItems, type, lastObjectId, targetVersion),
                                context.now());
                    }

                    // 资源预算检查
                    context.checkResourceBudget();

                    List<SearchDocument> batch = searchProjectionPort.listDocuments(
                            null, type, SearchDocumentStatus.VISIBLE, offset, BATCH_SIZE);
                    if (batch.isEmpty()) {
                        break;
                    }

                    for (SearchDocument doc : batch) {
                        // 跳过检查点之前的对象（同一类型内）
                        if (skipUntilLastObject && lastObjectId != null
                                && doc.objectRef().objectId().equals(lastObjectId)) {
                            skipUntilLastObject = false;
                            continue;
                        }
                        if (skipUntilLastObject) {
                            continue;
                        }

                        rebuildDocument(context, job, doc, targetVersion, failures);
                        processed++;
                        lastObjectId = doc.objectRef().objectId();
                    }

                    // 持久化检查点
                    checkpoint = buildCheckpoint(processed, totalItems, type, lastObjectId, targetVersion);
                    context.saveCheckpoint(checkpoint);

                    if (batch.size() < BATCH_SIZE) {
                        break;
                    }
                    offset += BATCH_SIZE;

                    // 失败率超阈值则中止
                    if (job.resourceBudget().isFailureRateExceeded(processed, failures.size())) {
                        LOG.warn("作业 {} 失败率超阈值 ({}/{})，中止", job.id(), failures.size(), processed);
                        return JobExecutionResult.failed(
                                "失败率超阈值: " + failures.size() + "/" + processed,
                                failures, checkpoint, context.now());
                    }
                }
                // 进入下一个类型，重置对象 ID 游标
                lastObjectId = null;
                lastObjectTypeKey = null;
            }

            // 完成
            String summary = String.format("搜索投影重建完成: 处理 %d 项, 失败 %d 项, 目标版本 %s",
                    processed, failures.size(), targetVersion.stableKey());
            if (failures.isEmpty()) {
                return JobExecutionResult.completed(summary, checkpoint, context.now());
            }
            return JobExecutionResult.completedWithFailures(summary, failures, checkpoint, context.now());

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("作业 {} 重建未预期异常", job.id(), e);
            throw JobExecutionException.fatalBusinessError(
                    "重建未预期异常: " + e.getMessage(),
                    "联系平台管理员排查日志，修复后从检查点重试");
        }
    }

    /**
     * 重建单个文档的搜索投影。
     *
     * <p>构造 {@link SearchIndexingEvent#rebuild} 事件，携带目标分析器版本和文档的规范化文本作为输入。
     * 规范化文本再次规范化幂等（NFC + 大小写折叠幂等），新分析器版本的停用词和分词规则将重新应用。
     */
    private void rebuildDocument(JobContext context, BackgroundJob job, SearchDocument doc,
                                  AnalyzerVersion targetVersion, List<JobFailureItem> failures) {
        try {
            SearchIndexingEvent event = SearchIndexingEvent.rebuild(
                    doc.objectRef(),
                    doc.indexedRevision(),
                    doc.title(),
                    doc.normalizedText().text(),
                    job.requestedBy(),
                    context.now(),
                    targetVersion);
            searchProjectionPort.index(event);
        } catch (Exception e) {
            String itemKey = doc.objectRef().objectType().stableKey()
                    + ":" + doc.objectRef().objectId();
            JobFailureItem failure = JobFailureItem.retryable(
                    itemKey,
                    "SEARCH.REBUILD_FAILED",
                    "重建文档失败: " + e.getMessage(),
                    "检查文档源数据或分析器版本兼容性后重试",
                    context.now());
            failures.add(failure);
            context.recordFailure(failure);
            LOG.debug("作业 {} 重建文档 {} 失败: {}", job.id(), itemKey, e.getMessage());
        }
    }

    private long countAllVisibleDocuments() {
        long total = 0;
        for (SearchObjectType type : SearchObjectType.values()) {
            total += searchProjectionPort.countDocuments(null, type, SearchDocumentStatus.VISIBLE);
        }
        return total;
    }

    private JobCheckpoint buildCheckpoint(int processed, int total,
                                          SearchObjectType lastType, UUID lastObjectId,
                                          AnalyzerVersion targetVersion) {
        return new JobCheckpoint(processed, total, null,
                buildState(lastType, lastObjectId, targetVersion), 0);
    }

    private JsonNode buildState(SearchObjectType lastType, UUID lastObjectId,
                                AnalyzerVersion targetVersion) {
        ObjectNode state = JsonNodeFactory.instance.objectNode();
        if (lastType != null) {
            state.put(CK_LAST_OBJECT_TYPE, lastType.stableKey());
        }
        if (lastObjectId != null) {
            state.put(CK_LAST_OBJECT_ID, lastObjectId.toString());
        }
        if (targetVersion != null) {
            state.put(CK_TARGET_VERSION, targetVersion.stableKey());
        }
        return state;
    }

    private AnalyzerVersion parseTargetVersion(BackgroundJob job, JobCheckpoint checkpoint) {
        // 优先从检查点恢复，其次从 job.scope 解析
        String fromState = parseStringState(checkpoint, CK_TARGET_VERSION);
        if (fromState != null) {
            return AnalyzerVersion.parse(fromState);
        }
        if (job.scope() != null && !job.scope().isBlank()) {
            return AnalyzerVersion.parse(job.scope());
        }
        throw JobExecutionException.fatalBusinessError(
                "PROJECTION_REBUILD 作业未指定目标分析器版本（scope 或 checkpoint 缺失）",
                "提交作业时通过 scope 字段传入目标 AnalyzerVersion.stableKey()");
    }

    private String parseStringState(JobCheckpoint checkpoint, String field) {
        JsonNode node = checkpoint.stateJson().get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private UUID parseUuidState(JobCheckpoint checkpoint, String field) {
        String value = parseStringState(checkpoint, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

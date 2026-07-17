package com.pdp.workflow.runtime;

import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.MessageCorrelation;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceSummary;
import com.pdp.workflow.model.WorkflowResultEvent;
import com.pdp.workflow.model.WorkflowStartRequest;

import java.util.Optional;

/**
 * 平台流程运行时端口（FR-174、ADR-0005 第 6 节）。
 *
 * <p>负责流程实例启动、推进、消息关联与结果桥接；
 * 对外 <strong>不暴露</strong> Flowable {@code RuntimeService} 类型。
 *
 * <p><strong>核心契约（FR-174 / ADR-0005）</strong>：
 * <ol>
 *   <li>版本固定：实例启动时 MUST 固定为当前定义版本，新版本部署不影响运行中实例；</li>
 *   <li>幂等启动：相同 {@code IdempotencyKey + BusinessObjectRef} 重复启动 MUST 返回已有实例，
 *       不创建重复实例；</li>
 *   <li>消息关联幂等：相同 {@code (messageName, businessObjectRef, idempotencyKey)}
 *       重复投递不重复推进流程；</li>
 *   <li>结果桥接：流程完成或异常终止时 MUST 通过 {@link WorkflowResultEvent} 桥接到业务模块，
 *       业务聚合决定最终业务状态变化；</li>
 *   <li>非权威存储：流程实例、活动与变量 MUST NOT 成为业务结论、权限或审计事实的唯一存储；</li>
 *   <li>禁止 XA：编排消费与流程启动不在同一事务，编排消息通过 Outbox 异步投递（ADR-0005 第 8 节）。</li>
 * </ol>
 *
 * <p>端口实现位于 {@code workflow} 模块的 {@code infrastructure/flowable/} 与
 * {@code infrastructure/event/} 子包，内部完成 Flowable {@code RuntimeService} 调用、
 * Outbox 事件消费、异常翻译与结果事件发布。
 */
public interface WorkflowRuntimePort {

    /**
     * 启动流程实例。
     *
     * <p>实例固定为 {@link WorkflowStartRequest#definitionId()} 指向的版本。
     * 相同 {@code IdempotencyKey + BusinessObjectRef} 重复启动返回已有实例。
     *
     * @param request 启动请求
     * @return 流程实例摘要
     * @throws WorkflowEngineException 定义不存在、不可启动（非 DEPLOYED）或引擎故障
     */
    WorkflowInstanceSummary start(WorkflowStartRequest request);

    /**
     * 查询流程实例诊断摘要。
     *
     * @param instanceId 实例 ID
     * @return 实例摘要，不存在或已归档时返回 empty
     */
    Optional<WorkflowInstanceSummary> findById(WorkflowInstanceId instanceId);

    /**
     * 按业务对象查询关联的流程实例。
     *
     * <p>用于业务模块回查编排状态（如审批详情页展示流程进度）。
     *
     * @param businessObjectRef 业务对象引用
     * @return 关联实例摘要，不存在时返回 empty
     */
    Optional<WorkflowInstanceSummary> findByBusinessObject(BusinessObjectRef businessObjectRef);

    /**
     * 向运行中流程实例投递消息事件。
     *
     * <p>消息关联 MUST 幂等：相同 {@code (messageName, businessObjectRef, idempotencyKey)}
     * 重复投递不重复推进流程。实例终态时消息被忽略并返回当前实例摘要。
     *
     * @param correlation 消息关联请求
     * @return 关联后的实例摘要
     * @throws WorkflowEngineException 实例不存在或引擎故障
     */
    WorkflowInstanceSummary correlateMessage(MessageCorrelation correlation);

    /**
     * 信号流程继续执行（用于等待态推进）。
     *
     * <p>仅适用于显式等待态（如 receive task、user task 完成后）。
     * 非等待态调用无效，返回当前实例摘要。
     *
     * @param instanceId      实例 ID
     * @param idempotencyKey  幂等键
     * @return 推进后的实例摘要
     * @throws WorkflowEngineException 实例不存在或引擎故障
     */
    WorkflowInstanceSummary signal(WorkflowInstanceId instanceId,
                                   com.pdp.shared.context.IdempotencyKey idempotencyKey);

    /**
     * 查询流程实例结果事件（终态时桥接到业务模块的结果）。
     *
     * @param instanceId 实例 ID
     * @return 结果事件，未终态时返回 empty
     */
    Optional<WorkflowResultEvent> findResultEvent(WorkflowInstanceId instanceId);

    /**
     * 引擎是否健康（用于就绪检查与降级判断）。
     *
     * <p>当引擎不可用时，编排消费者应将消息保留在 Outbox 等待重试，
     * 而非直接失败（FR-174 编排失败支持幂等恢复）。
     *
     * @return true 表示引擎可用
     */
    boolean isHealthy();
}

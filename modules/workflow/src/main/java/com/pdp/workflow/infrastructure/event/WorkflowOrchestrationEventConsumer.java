package com.pdp.workflow.infrastructure.event;

import com.pdp.workflow.application.WorkflowRuntimeService;
import com.pdp.workflow.model.MessageCorrelation;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowInstanceSummary;
import com.pdp.workflow.model.WorkflowStartRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 工作流编排请求事件消费者（FR-174、ADR-0005 第 8 节）。
 *
 * <p>消费 Spring Modulith 发布的 {@code pdp.workflow.orchestration.requested} 事件
 * （载荷为 {@link WorkflowOrchestrationRequest}），路由到
 * {@link WorkflowRuntimeService} 执行启动、消息关联或信号推进。
 *
 * <p><strong>消费语义</strong>：
 * <ul>
 *   <li>至少一次投递：Spring Modulith JDBC 事件发布保证至少一次，消费者通过 eventId 幂等
 *       （Spring Modulith 内部去重），运行时服务通过 idempotencyKey 二次幂等；</li>
 *   <li>独立事务：编排消费与流程启动不在同一事务（ADR-0005 禁止 XA）。
 *       {@code workflowEngine} 事务管理器处理 Flowable 操作，
 *       {@code pdpPrimary} 事务管理器处理 PDP 持久化；</li>
 *   <li>失败处理：不可重试故障（定义不存在、状态非法）发布
 *       {@code pdp.workflow.orchestration.failed} 事件通知人工补偿；
 *       可重试故障（引擎不可用）由 Spring Modulith 自动重试。</li>
 * </ul>
 *
 * <p><strong>重试安全</strong>：
 * 重试不生成重复审批动作或业务结果——运行时服务的幂等预查保证相同
 * {@code (workspace, businessObject, idempotencyKey)} 不重复启动或推进。
 */
@org.springframework.stereotype.Component
public class WorkflowOrchestrationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrationEventConsumer.class);

    /** 不可重试的引擎异常原因：发布失败事件而非自动重试。 */
    private static final Set<WorkflowEngineException.Reason> NON_RETRYABLE_REASONS = Set.of(
            WorkflowEngineException.Reason.DEFINITION_NOT_FOUND,
            WorkflowEngineException.Reason.DEFINITION_INVALID,
            WorkflowEngineException.Reason.ILLEGAL_STATE_TRANSITION,
            WorkflowEngineException.Reason.MIGRATION_PLAN_INVALID);

    private final WorkflowRuntimeService runtimeService;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowOrchestrationEventConsumer(
            WorkflowRuntimeService runtimeService,
            ApplicationEventPublisher eventPublisher) {
        this.runtimeService = runtimeService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 消费编排请求事件。
     *
     * <p>根据操作类型路由到运行时服务，失败时按原因分类处理。
     * 本方法不抛异常——不可重试故障通过失败事件通知，可重试故障由 Spring Modulith 重试。
     *
     * <p><strong>事务</strong>：本方法标记 {@code @Transactional(transactionManager = "workflowEngineTransactionManager")}，
     * 确保 Flowable 操作在同一 workflowEngine 事务中完成。PDP 实例引用持久化由运行时服务
     * 内部委托端口实现，使用 pdpPrimary 事务管理器（独立事务，无 XA）。
     *
     * @param request 编排请求事件载荷
     */
    @EventListener
    @Transactional(transactionManager = "workflowEngineTransactionManager")
    public void onOrchestrationRequest(WorkflowOrchestrationRequest request) {
        log.debug("消费工作流编排请求: eventId={}, operation={}",
                request.eventId(), request.operation());

        try {
            routeToRuntimeService(request);
        } catch (WorkflowEngineException e) {
            handleEngineException(request, e);
        } catch (Exception e) {
            log.warn("工作流编排请求处理异常（可重试）: eventId={}, operation={}, error={}",
                    request.eventId(), request.operation(), e.getMessage(), e);
            // 重新抛出，Spring Modulith 自动重试
            throw e;
        }
    }

    /**
     * 根据操作类型路由到运行时服务。
     *
     * @param request 编排请求
     * @throws WorkflowEngineException 引擎层故障
     */
    private void routeToRuntimeService(WorkflowOrchestrationRequest request) {
        switch (request.operation()) {
            case START -> handleStart(request);
            case CORRELATE -> handleCorrelate(request);
            case SIGNAL -> handleSignal(request);
        }
    }

    /**
     * 处理 START 操作：构造启动请求并委托运行时服务。
     */
    private void handleStart(WorkflowOrchestrationRequest request) {
        WorkflowStartRequest startRequest = new WorkflowStartRequest(
                request.definitionId(),
                request.businessObjectRef(),
                request.variables(),
                request.idempotencyKey(),
                request.actor());
        WorkflowInstanceSummary summary = runtimeService.start(startRequest);
        log.info("流程实例启动成功: eventId={}, instanceId={}, definitionId={}",
                request.eventId(), summary.id(), summary.definitionId());
    }

    /**
     * 处理 CORRELATE 操作：构造消息关联请求并委托运行时服务。
     */
    private void handleCorrelate(WorkflowOrchestrationRequest request) {
        MessageCorrelation correlation = new MessageCorrelation(
                request.messageName(),
                request.businessObjectRef(),
                request.variables(),
                request.idempotencyKey());
        WorkflowInstanceSummary summary = runtimeService.correlateMessage(
                request.workspaceId(), correlation);
        log.info("流程消息关联成功: eventId={}, instanceId={}, message={}",
                request.eventId(), summary.id(), request.messageName());
    }

    /**
     * 处理 SIGNAL 操作：委托运行时服务信号推进。
     */
    private void handleSignal(WorkflowOrchestrationRequest request) {
        WorkflowInstanceSummary summary = runtimeService.signal(
                request.workspaceId(), request.instanceId(), request.idempotencyKey());
        log.info("流程信号推进成功: eventId={}, instanceId={}, state={}",
                request.eventId(), summary.id(), summary.state());
    }

    /**
     * 处理引擎异常：不可重试原因发布失败事件，可重试原因重新抛出。
     *
     * @param request   原始编排请求
     * @param exception 引擎异常
     */
    private void handleEngineException(WorkflowOrchestrationRequest request, WorkflowEngineException exception) {
        if (NON_RETRYABLE_REASONS.contains(exception.workflowReason())) {
            log.warn("工作流编排请求不可重试失败: eventId={}, operation={}, reason={}, message={}",
                    request.eventId(), request.operation(),
                    exception.workflowReason(), exception.getMessage());
            // 发布失败事件，通知审批、运维和人工补偿消费者
            WorkflowOrchestrationFailure failure = WorkflowOrchestrationFailure.of(
                    request.eventId(),
                    request.operation().stableKey(),
                    request.instanceId(),
                    exception);
            eventPublisher.publishEvent(failure);
        } else {
            // 可重试故障（ENGINE_UNAVAILABLE、ORCHESTRATION_FAILED、DEADLOCK_DETECTED）：
            // 重新抛出，Spring Modulith 自动重试
            log.warn("工作流编排请求可重试失败: eventId={}, operation={}, reason={}",
                    request.eventId(), request.operation(), exception.workflowReason());
            throw exception;
        }
    }
}

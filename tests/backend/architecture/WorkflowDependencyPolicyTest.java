package com.pdp.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Nested;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Flowable 依赖边界架构测试 —— 强制执行 ADR 0005（平台工作流基础能力与 Flowable 边界）。
 *
 * <p>父 POM 已导入 {@code flowable-bom}，且仅 {@code workflow} 模块依赖
 * {@code flowable-spring-boot-starter-process}。本测试在类层面固化：Flowable API 只能出现在
 * {@code com.pdp.workflow..}，业务模块不得依赖 Flowable API、实体、表名或异常类型；
 * REST/IDM/CMMN/DMN 与 JPA/Hibernate 集成在 workflow 模块内也被禁止（仅保留 Process Engine）。
 *
 * <p>对应任务 T031、ADR 0005 第 2/3/5/9 条与宪章原则 III/IV。本测试为骨架测试，在后端模块补齐
 * 代码后编译并强制执行；当前用于文档化与固化策略。
 */
@AnalyzeClasses(
    packages = "com.pdp",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class WorkflowDependencyPolicyTest {

    /**
     * Flowable API 隔离策略 —— 仅 {@code com.pdp.workflow..} 可依赖 {@code org.flowable..}。
     *
     * <p>ADR 0005 第 3/4 条：{@code workflow} 是平台公共基础模块，对外暴露 4 类稳定端口
     * （{@code WorkflowDefinitionPort} / {@code WorkflowRuntimePort} / {@code WorkflowTaskPort} /
     * {@code WorkflowAdministrationPort}）。业务模块（approval、project、deliverable、governance
     * 及领域包运行时）只能通过端口复用能力，MUST NOT 依赖 Flowable API、实体、表名或异常类型。
     * CI 通过 Spring Modulith / ArchUnit 校验依赖方向与循环引用。
     */
    @Nested
    class FlowableIsolationPolicy {

        /**
         * 除 {@code com.pdp.workflow..} 外，任何 {@code com.pdp..} 类不得依赖 {@code org.flowable..}。
         */
        @ArchTest
        static final ArchRule onlyWorkflowModuleMayDependOnFlowable =
            noClasses().that().resideInAPackage("com.pdp..")
                .and().resideOutsideOfPackage("com.pdp.workflow..")
                .should().dependOnClassesThat().resideInAPackage("org.flowable..");
    }

    /**
     * Flowable 子引擎禁用策略 —— REST/IDM/CMMN/DMN 在任何模块（含 workflow）均被禁止。
     *
     * <p>ADR 0005 第 2/9 条：不引入 Flowable REST starter、IDM、CMMN、DMN。PDP 身份、授权、审批待办
     * 和业务查询继续使用自身模型，不依赖引擎内置身份数据。启用这些能力属于破坏 ADR 边界的重大变更，
     * 必须提交新 ADR 并补充兼容、迁移、任务重排与验收影响。
     */
    @Nested
    class FlowableSubEngineBannedPolicy {

        @ArchTest
        static final ArchRule noRestIdmCmmnDmnDependencies =
            noClasses().that().resideInAPackage("com.pdp..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.flowable.rest..",
                    "org.flowable.idm..",
                    "org.flowable.cmmn..",
                    "org.flowable.dmn..");
    }

    /**
     * Flowable 与 JPA/Hibernate 集成禁用策略。
     *
     * <p>ADR 0005 第 2/5/8 条：不引入 Flowable JPA/Hibernate 集成；Flowable 表与业务表使用独立
     * schema/账号/HikariCP 连接池和事务管理器，对应 {@code workflowEngine} 数据源，且与业务事务
     * 不使用 XA。业务事实先在 {@code pdpPrimary} 本地事务提交并登记 outbox，再由幂等编排消费者
     * 启动或推进流程。本规则禁止任何 {@code com.pdp..} 类依赖 Flowable JPA 包，并禁止 workflow
     * 模块内出现 Hibernate 依赖。
     */
    @Nested
    class FlowableJpaHibernateBannedPolicy {

        @ArchTest
        static final ArchRule noFlowableJpaDependencies =
            noClasses().that().resideInAPackage("com.pdp..")
                .should().dependOnClassesThat().resideInAPackage("org.flowable..jpa..");

        @ArchTest
        static final ArchRule noHibernateInWorkflowModule =
            noClasses().that().resideInAPackage("com.pdp.workflow..")
                .should().dependOnClassesThat().resideInAPackage("org.hibernate..");
    }
}

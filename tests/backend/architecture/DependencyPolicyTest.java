package com.pdp.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Nested;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 依赖治理架构测试 —— 强制执行宪章原则 V（契约优先、兼容演进与数据库独立）与 ADR 0001/0002 锁定的
 * 持久化框架边界。
 *
 * <p>父 POM 已在 dependencyManagement 层面锁定 MyBatis-Plus 版本并显式排除 Hibernate/JPA。本测试在
 * 编译期/类层面再次强制：领域层与应用层不得引入第二套持久化框架或泄露框架类型，框架类型只能位于
 * 基础设施适配器边界（{@code com.pdp.persistence..}）。
 *
 * <p>对应任务 T030、宪章原则 V 与“当前交付与工程门禁”中“领域层和应用层不得依赖具体持久化框架”
 * 条款。本测试为骨架测试，在后端模块补齐代码后编译并强制执行；当前用于文档化与固化策略。
 */
@AnalyzeClasses(
    packages = "com.pdp",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class DependencyPolicyTest {

    /**
     * 构造“全限定名匹配给定任一名称”的谓词，用于精确禁止对指定框架类型的依赖。
     *
     * <p>使用全限定名字符串而非 {@code Class} 引用，避免在测试类路径缺失 MyBatis-Plus 等框架时
     * 无法编译；规则在后端代码补齐后即生效。
     *
     * @param names 被禁框架类型的全限定名集合
     * @return 匹配任一全限定名的 {@link DescribedPredicate}
     */
    private static DescribedPredicate<JavaClass> haveFullyQualifiedNameAnyOf(String... names) {
        Set<String> nameSet = new HashSet<>(Arrays.asList(names));
        return DescribedPredicate.describe(
            "have fully qualified name any of " + Arrays.toString(names),
            javaClass -> nameSet.contains(javaClass.getName()));
    }

    /**
     * Hibernate / JPA 禁入策略。
     *
     * <p>宪章原则 V 与“当前交付与工程门禁”要求禁止在 P1 同时引入 Hibernate/JPA 作为第二套业务
     * 持久化框架，避免双持久化上下文、事务边界和实体状态语义分裂（plan.md“关键设计规则”）。
     * 父 POM 已在依赖层排除；本规则在类层面兜底，禁止任何 {@code com.pdp..} 类直接依赖
     * {@code org.hibernate..}、{@code javax.persistence..} 或 {@code jakarta.persistence..}。
     */
    @Nested
    class HibernateJpaBannedPolicy {

        @ArchTest
        static final ArchRule noHibernateDependencies =
            noClasses().that().resideInAPackage("com.pdp..")
                .should().dependOnClassesThat().resideInAPackage("org.hibernate..");

        @ArchTest
        static final ArchRule noJpaApiDependencies =
            noClasses().that().resideInAPackage("com.pdp..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.persistence..",
                    "jakarta.persistence..");
    }

    /**
     * MyBatis-Plus 框架类型隔离策略。
     *
     * <p>MyBatis-Plus 仅在基础设施层提供单表 CRUD、分页、乐观锁与安全拦截能力；业务模块不得直接
     * 暴露或依赖 {@code BaseMapper}、{@code IService}、{@code QueryWrapper} 等框架类型
     * （plan.md“P1 MySQL 持久化与数据库独立边界”）。框架类型只能位于
     * {@code com.pdp.persistence..} 基础设施适配器边界。
     */
    @Nested
    class MyBatisPlusIsolationPolicy {

        @ArchTest
        static final ArchRule frameworkTypesOnlyInInfrastructure =
            noClasses().that().resideInAPackage("com.pdp..")
                .and().resideOutsideOfPackage("com.pdp.persistence..")
                .should().dependOnClassesThat(haveFullyQualifiedNameAnyOf(
                    "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                    "com.baomidou.mybatisplus.extension.service.IService",
                    "com.baomidou.mybatisplus.core.conditions.query.QueryWrapper"));
    }

    /**
     * MyBatis (ibatis) 类型隔离策略。
     *
     * <p>{@code org.apache.ibatis..} 类型只能出现在持久化适配器与数据迁移基础设施中；领域层与应用层
     * 不得直接依赖 MyBatis 会话、映射或注解类型，保持数据库无关边界（宪章原则 V）。数据迁移模块
     * 使用独立 {@code SqlSessionFactory} 与 Mapper 扫描包，亦属基础设施，故一并豁免。
     */
    @Nested
    class MyBatisIsolationPolicy {

        @ArchTest
        static final ArchRule ibatisTypesOnlyInInfrastructure =
            noClasses().that().resideInAPackage("com.pdp..")
                .and().resideOutsideOfPackage("com.pdp.persistence..")
                .and().resideOutsideOfPackage("com.pdp.datamigration..")
                .should().dependOnClassesThat().resideInAPackage("org.apache.ibatis..");
    }
}

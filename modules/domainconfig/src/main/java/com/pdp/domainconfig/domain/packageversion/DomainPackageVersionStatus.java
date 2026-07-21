package com.pdp.domainconfig.domain.packageversion;

/**
 * 领域包版本状态（spec.md §197）。
 *
 * <p>状态机：
 * <ul>
 *   <li>{@link #DRAFT} → {@link #VALIDATING}（触发结构化校验）</li>
 *   <li>{@link #VALIDATING} → {@link #DRAFT}（校验失败回到草稿）或 {@link #REVIEW_PENDING}（校验通过后提交审核）</li>
 *   <li>{@link #DRAFT}/{@link #REJECTED} → {@link #REVIEW_PENDING}（提交审核）</li>
 *   <li>{@link #REVIEW_PENDING} → {@link #PUBLISHED}（独立发布者审核通过并发布）
 *       或 {@link #REJECTED}（审核拒绝，可回到草稿修改）</li>
 *   <li>{@link #PUBLISHED} → {@link #FROZEN}（冻结，阻止运行实例升级）</li>
 *   <li>{@link #PUBLISHED} → {@link #DEPRECATED}（弃用）</li>
 *   <li>{@link #DEPRECATED} → {@link #RETIRED}（运行实例全部迁移完毕后退役）</li>
 * </ul>
 *
 * <p>FR-167 确定性状态机：每个迁移定义前置条件、权限、并发语义、结果和稳定失败原因。
 */
public enum DomainPackageVersionStatus {
    DRAFT,
    VALIDATING,
    REVIEW_PENDING,
    PUBLISHED,
    REJECTED,
    DEPRECATED,
    RETIRED,
    FROZEN
}

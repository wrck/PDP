package com.pdp.operations.observability;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 服务等级档案（FR-169 SLO 定义）。
 *
 * <p>对应 spec.md FR-169："每项 P1 关键能力 MUST 具有服务等级档案，至少定义服务类别、适用交互时限、
 * SLI/SLO、容量假设、失败模式、告警条件、责任人、运行手册和证据位置。"和 SC-041："P1 关键能力
 * 服务等级档案覆盖率 100%，每项均能关联 SLI/SLO、容量、失败模式、告警、责任人、运行手册和
 * 最近一次验证证据。"
 *
 * <p>每项 P1 关键能力（项目/阶段推进、任务更新、审批办理、交付件提交等）MUST 关联一个 SLO 档案，
 * 档案持久化到服务目录表，版本化管理。
 *
 * @param id                  档案 ID（稳定标识，如 {@code slo.project.advance}）
 * @param capability          能力名称（如 {@code 项目/阶段推进}）
 * @param serviceCategory     服务类别
 * @param interactionTimeLimit 适用交互时限（P95 目标）
 * @param sliDefinition       SLI 定义（如 {@code 成功完成且未超时的合格请求 ÷ 合格请求总数}）
 * @param sloTarget           SLO 目标（如 {@code 99.95%}）
 * @param capacityAssumption  容量假设（如 {@code 1000 并发活跃用户，日均 10 万请求}）
 * @param failureModes        失败模式列表（如数据库故障、对象存储故障、依赖超时）
 * @param alertConditions     告警条件（如 {@code 可用性 < 99.9% 持续 5 分钟}）
 * @param responsibleTeam     责任团队
 * @param runbookLocation     运行手册位置（URL 或路径）
 * @param evidenceLocation    证据位置（如 {@code specs/002-pdp-product/evidence/slo/}）
 * @param version             档案版本
 */
public record ServiceLevelObjective(
        String id,
        String capability,
        ServiceCategory serviceCategory,
        Duration interactionTimeLimit,
        String sliDefinition,
        String sloTarget,
        String capacityAssumption,
        List<String> failureModes,
        List<String> alertConditions,
        String responsibleTeam,
        String runbookLocation,
        String evidenceLocation,
        String version) {

    public ServiceLevelObjective {
        Objects.requireNonNull(id, "id 不能为 null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空白");
        }
        Objects.requireNonNull(capability, "capability 不能为 null");
        if (capability.isBlank()) {
            throw new IllegalArgumentException("capability 不能为空白");
        }
        Objects.requireNonNull(serviceCategory, "serviceCategory 不能为 null");
        Objects.requireNonNull(interactionTimeLimit, "interactionTimeLimit 不能为 null");
        Objects.requireNonNull(sliDefinition, "sliDefinition 不能为 null");
        if (sliDefinition.isBlank()) {
            throw new IllegalArgumentException("sliDefinition 不能为空白");
        }
        Objects.requireNonNull(sloTarget, "sloTarget 不能为 null");
        if (sloTarget.isBlank()) {
            throw new IllegalArgumentException("sloTarget 不能为空白");
        }
        Objects.requireNonNull(responsibleTeam, "responsibleTeam 不能为 null");
        if (responsibleTeam.isBlank()) {
            throw new IllegalArgumentException("responsibleTeam 不能为空白");
        }
        failureModes = failureModes != null ? List.copyOf(failureModes) : List.of();
        alertConditions = alertConditions != null ? List.copyOf(alertConditions) : List.of();
    }

    /** 默认 SLI 定义（FR-165）。 */
    public static final String DEFAULT_SLI_DEFINITION =
            "成功完成且未超过适用交互时限的合格请求数 ÷ 合格请求总数（FR-165）";

    /**
     * 构造核心交付命令的默认 SLO 档案。
     *
     * @param capability       能力名称
     * @param responsibleTeam  责任团队
     * @param runbookLocation  运行手册
     * @param evidenceLocation 证据位置
     */
    public static ServiceLevelObjective forCoreCommand(String capability, String responsibleTeam,
                                                       String runbookLocation, String evidenceLocation) {
        return new ServiceLevelObjective(
                "slo." + capability.toLowerCase().replace("/", ".").replace(" ", "_"),
                capability,
                ServiceCategory.CORE_DELIVERY_COMMAND,
                ServiceCategory.CORE_DELIVERY_COMMAND.interactionTimeLimit(),
                DEFAULT_SLI_DEFINITION,
                "99.95%",
                "1000 并发活跃用户，日均 10 万核心命令请求",
                List.of("数据库故障", "对象存储故障", "内部依赖超时", "并发冲突"),
                List.of("可用性 < 99.95% 持续 5 分钟", "P95 延迟 > 2 秒持续 5 分钟",
                        "失败率 > 0.1% 持续 5 分钟"),
                responsibleTeam, runbookLocation, evidenceLocation, "1.0.0");
    }

    /** 是否满足 SC-041 完整性（所有必填字段非空）。 */
    public boolean isComplete() {
        return !capacityAssumption.isBlank()
                && !failureModes.isEmpty()
                && !alertConditions.isEmpty()
                && !runbookLocation.isBlank()
                && !evidenceLocation.isBlank()
                && !version.isBlank();
    }
}

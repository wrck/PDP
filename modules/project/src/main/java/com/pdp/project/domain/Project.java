package com.pdp.project.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 工作空间隔离的项目根聚合；生命周期动作由后续应用服务协调。 */
public record Project(UUID id, UUID workspaceId, UUID parentProjectId, String projectNo, String name,
                      String objective, String scope, UUID managerId, Priority priority, Health health,
                      LifecycleState lifecycleState, String domainStageKey, Status status,
                      Revision revision, Instant createdAt, Instant updatedAt) {
  public enum Priority { LOW, NORMAL, HIGH, CRITICAL }
  public enum Health { GREEN, AMBER, RED }
  public enum LifecycleState { PRE_PLANNING, PLANNING, EXECUTING, ACCEPTING, SERVICING, CLOSED, CANCELLED }
  public enum Status { ACTIVE, ARCHIVED }
  public Project {
    Objects.requireNonNull(id, "项目 id 不能为空"); Objects.requireNonNull(workspaceId, "项目工作空间不能为空");
    Objects.requireNonNull(managerId, "项目经理不能为空"); Objects.requireNonNull(priority, "项目优先级不能为空");
    Objects.requireNonNull(health, "项目健康度不能为空"); Objects.requireNonNull(lifecycleState, "项目生命周期不能为空");
    Objects.requireNonNull(status, "项目状态不能为空"); Objects.requireNonNull(revision, "项目 revision 不能为空");
    Objects.requireNonNull(createdAt, "项目创建时间不能为空"); Objects.requireNonNull(updatedAt, "项目更新时间不能为空");
    projectNo = required(projectNo, "项目编号", 100); name = required(name, "项目名称", 200);
    if (parentProjectId != null && parentProjectId.equals(id)) throw new IllegalArgumentException("项目不能作为自身父项目");
    if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("项目更新时间不能早于创建时间");
  }
  private static String required(String value, String label, int max) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    value = value.trim(); if (value.length() > max) throw new IllegalArgumentException(label + "超长"); return value;
  }
}

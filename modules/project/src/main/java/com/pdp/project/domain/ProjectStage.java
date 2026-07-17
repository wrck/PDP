package com.pdp.project.domain;

import com.pdp.shared.concurrency.Revision;
import java.util.Objects;
import java.util.UUID;

/** 项目阶段必须映射到唯一的顶层项目生命周期状态。 */
public record ProjectStage(UUID id, UUID projectId, String stableKey, String name,
                           Project.LifecycleState topLifecycleState, State state, UUID ownerId,
                           int sequenceNo, Revision revision) {
  public enum State { NOT_STARTED, READY, IN_PROGRESS, BLOCKED, PAUSED, COMPLETED, CANCELLED }
  public ProjectStage {
    Objects.requireNonNull(id, "阶段 id 不能为空"); Objects.requireNonNull(projectId, "阶段项目不能为空");
    Objects.requireNonNull(topLifecycleState, "阶段顶层生命周期不能为空"); Objects.requireNonNull(state, "阶段状态不能为空");
    Objects.requireNonNull(revision, "阶段 revision 不能为空");
    stableKey = required(stableKey, "阶段稳定键", 100); name = required(name, "阶段名称", 200);
    if (sequenceNo < 0) throw new IllegalArgumentException("阶段顺序不能为负数");
  }
  private static String required(String value, String label, int max) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); value = value.trim(); if (value.length() > max) throw new IllegalArgumentException(label + "超长"); return value; }
}

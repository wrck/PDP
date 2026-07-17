package com.pdp.template.port;

import com.pdp.shared.context.IdempotencyKey;
import com.pdp.template.domain.GeneratedProjectObjectRef;
import com.pdp.template.domain.ProjectInstantiationPlan;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * template 模块调用的公开原子物化边界。实现可协调 project/planning/deliverable/approval，
 * 但不得让 template 领域层依赖这些模块的表、框架或数据库专有类型。
 */
public interface ProjectInstantiationTargetPort {
  MaterializationResult materialize(MaterializationCommand command);

  record MaterializationCommand(
      ProjectInstantiationPlan plan, UUID actorId, IdempotencyKey idempotencyKey) {
    public MaterializationCommand {
      Objects.requireNonNull(plan, "实例化计划不能为空");
      Objects.requireNonNull(actorId, "实例化操作人不能为空");
      Objects.requireNonNull(idempotencyKey, "实例化幂等键不能为空");
      plan.requireCreatable();
    }
  }

  record MaterializationResult(
      UUID projectId,
      UUID domainPackageSnapshotId,
      List<GeneratedProjectObjectRef> generatedObjects) {
    public MaterializationResult {
      Objects.requireNonNull(projectId, "已创建项目 id 不能为空");
      Objects.requireNonNull(domainPackageSnapshotId, "领域包快照 id 不能为空");
      generatedObjects = List.copyOf(generatedObjects);
    }
  }
}

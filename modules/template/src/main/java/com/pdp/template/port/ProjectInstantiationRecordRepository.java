package com.pdp.template.port;

import com.pdp.shared.context.IdempotencyKey;
import com.pdp.template.domain.ProjectInstantiationRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 完成实例化记录仓储；saveIfAbsent 必须由数据库唯一约束提供原子语义。 */
public interface ProjectInstantiationRecordRepository {
  Optional<ProjectInstantiationRecord> findByIdempotencyKey(
      UUID workspaceId, IdempotencyKey idempotencyKey);

  Optional<ProjectInstantiationRecord> findByProjectId(UUID workspaceId, UUID projectId);

  SaveOutcome saveIfAbsent(ProjectInstantiationRecord record);

  record SaveOutcome(ProjectInstantiationRecord storedRecord, boolean inserted) {
    public SaveOutcome {
      Objects.requireNonNull(storedRecord, "实例化存储结果不能为空");
    }
  }
}

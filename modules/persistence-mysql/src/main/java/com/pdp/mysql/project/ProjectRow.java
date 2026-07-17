package com.pdp.mysql.project;
import com.pdp.project.domain.Project;
import com.pdp.shared.concurrency.Revision;
import java.time.Instant; import java.util.UUID;
public record ProjectRow(UUID id, UUID workspaceId, UUID parentProjectId, String projectNo, String name, String objective, String scope, UUID managerId, Project.Priority priority, Project.Health health, Project.LifecycleState lifecycleState, String domainStageKey, Project.Status status, long revision, Instant createdAt, Instant updatedAt) {
  static ProjectRow from(Project v) { return new ProjectRow(v.id(),v.workspaceId(),v.parentProjectId(),v.projectNo(),v.name(),v.objective(),v.scope(),v.managerId(),v.priority(),v.health(),v.lifecycleState(),v.domainStageKey(),v.status(),v.revision().value(),v.createdAt(),v.updatedAt()); }
  Project domain() { return new Project(id,workspaceId,parentProjectId,projectNo,name,objective,scope,managerId,priority,health,lifecycleState,domainStageKey,status,new Revision(revision),createdAt,updatedAt); }
}

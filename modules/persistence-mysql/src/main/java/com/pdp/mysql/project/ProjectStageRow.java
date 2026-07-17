package com.pdp.mysql.project;
import com.pdp.project.domain.ProjectStage; import com.pdp.shared.concurrency.Revision; import java.util.UUID;
public record ProjectStageRow(UUID id, UUID projectId, String stableKey, String name, com.pdp.project.domain.Project.LifecycleState topLifecycleState, ProjectStage.State state, UUID ownerId, int sequenceNo, long revision) {
 static ProjectStageRow from(ProjectStage v) { return new ProjectStageRow(v.id(),v.projectId(),v.stableKey(),v.name(),v.topLifecycleState(),v.state(),v.ownerId(),v.sequenceNo(),v.revision().value()); }
 ProjectStage domain() { return new ProjectStage(id,projectId,stableKey,name,topLifecycleState,state,ownerId,sequenceNo,new Revision(revision)); }
}

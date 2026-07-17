package com.pdp.mysql.project;
import com.pdp.project.domain.ProjectMember; import com.pdp.shared.concurrency.Revision; import java.time.Instant; import java.util.UUID;
public record ProjectMemberRow(UUID id, UUID projectId, UUID actorId, String projectRoleKey, Instant validFrom, Instant validUntil, String dataScope, ProjectMember.Source source, long revision) {
 static ProjectMemberRow from(ProjectMember v) { return new ProjectMemberRow(v.id(),v.projectId(),v.actorId(),v.projectRoleKey(),v.validFrom(),v.validUntil(),v.dataScope(),v.source(),v.revision().value()); }
 ProjectMember domain() { return new ProjectMember(id,projectId,actorId,projectRoleKey,validFrom,validUntil,dataScope,source,new Revision(revision)); }
}

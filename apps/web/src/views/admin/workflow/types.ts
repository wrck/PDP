export interface WorkflowFinding {
  severity: 'ERROR' | 'WARNING'
  code: string
  message: string
}

export interface WorkflowValidation {
  valid: boolean
  contentHash: string
  findings: WorkflowFinding[]
}

export interface WorkflowDefinitionSummary {
  id: string
  processDefinitionKey: string
  businessVersion: string
  contentHash: string
  domainPackageVersionId?: string | null
  status: string
  deployedAt: string
}

export interface WorkflowIncident {
  id: string
  code: string
  message: string
  status: 'OPEN' | 'RETRYING' | 'RESOLVED' | 'DEAD_LETTER'
  attempts: number
  occurredAt: string
  resolvedAt?: string | null
}

export interface WorkflowInstanceSummary {
  id: string
  definitionId: string
  businessObjectRef: Record<string, unknown>
  state: string
  currentActivityKeys: string[]
  incidentCount: number
  revision: number
  incidents?: WorkflowIncident[]
}

export interface WorkflowAdministrationApi {
  validate(command: Record<string, unknown>): Promise<WorkflowValidation>
  deploy(
    command: Record<string, unknown>,
    idempotencyKey: string,
  ): Promise<WorkflowDefinitionSummary>
  getInstance(workflowInstanceId: string): Promise<WorkflowInstanceSummary>
  applyAction(
    workflowInstanceId: string,
    command: Record<string, unknown>,
    idempotencyKey: string,
  ): Promise<{ jobId: string; status: string }>
}

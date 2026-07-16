import { apiClient, type ApiClient } from '../../../api/client'
import type {
  WorkflowAdministrationApi,
  WorkflowDefinitionSummary,
  WorkflowInstanceSummary,
  WorkflowValidation,
} from './types'

export function createWorkflowAdministrationApi(
  client: ApiClient = apiClient,
): WorkflowAdministrationApi {
  return {
    validate(command) {
      return client.request<WorkflowValidation>('/workflow-definitions/validate', {
        method: 'POST',
        body: command,
      })
    },

    deploy(command, idempotencyKey) {
      return client.request<WorkflowDefinitionSummary>('/workflow-definitions/deploy', {
        method: 'POST',
        idempotencyKey,
        body: command,
      })
    },

    getInstance(workflowInstanceId) {
      return client.request<WorkflowInstanceSummary>(
        `/workflow-instances/${encodeURIComponent(workflowInstanceId)}`,
      )
    },

    applyAction(workflowInstanceId, command, idempotencyKey) {
      return client.request<{ jobId: string; status: string }>(
        `/workflow-instances/${encodeURIComponent(workflowInstanceId)}/actions`,
        {
          method: 'POST',
          idempotencyKey,
          body: command,
        },
      )
    },
  }
}

export const workflowAdministrationApi = createWorkflowAdministrationApi()

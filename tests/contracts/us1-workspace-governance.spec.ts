import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'

import YAML from 'yaml'

const openapiPath = path.resolve(
  import.meta.dirname,
  '../../specs/002-pdp-product/contracts/openapi.yaml',
)

async function loadOpenApi() {
  return YAML.parse(await readFile(openapiPath, 'utf8'))
}

test('US1 工作空间治理公开操作保持稳定 operationId 和成功状态码', async () => {
  const openapi = await loadOpenApi()
  const operations = [
    ['/workspaces', 'get', 'listWorkspaces', '200'],
    ['/workspaces', 'post', 'createWorkspace', '201'],
    ['/workspaces/{workspaceId}', 'get', 'getWorkspace', '200'],
    ['/workspaces/{workspaceId}/actions', 'post', 'applyWorkspaceAction', '200'],
    ['/workspaces/{workspaceId}/organizations', 'post', 'createOrganizationUnit', '201'],
    ['/workspaces/{workspaceId}/members', 'post', 'addWorkspaceMember', '201'],
    ['/workspaces/{workspaceId}/roles', 'post', 'createWorkspaceRole', '201'],
    ['/workspaces/{workspaceId}/data-scopes', 'post', 'createDataScope', '201'],
    [
      '/workspaces/{workspaceId}/collaboration-grants',
      'post',
      'createCollaborationGrant',
      '201',
    ],
    [
      '/workspaces/{workspaceId}/collaboration-grants/{grantId}/revoke',
      'post',
      'revokeCollaborationGrant',
      '204',
    ],
  ]

  for (const [resourcePath, method, operationId, successStatus] of operations) {
    const operation = openapi.paths?.[resourcePath]?.[method]
    assert.ok(operation, `缺少操作 ${method.toUpperCase()} ${resourcePath}`)
    assert.equal(operation.operationId, operationId)
    assert.ok(operation.responses?.[successStatus], `${operationId} 缺少 ${successStatus}`)
  }
})

test('US1 状态机、并发与协作授权契约必须显式约束', async () => {
  const openapi = await loadOpenApi()
  assert.deepEqual(
    openapi.components.schemas.Workspace.properties.status.enum,
    ['DRAFT', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'],
  )
  assert.deepEqual(
    openapi.components.schemas.WorkspaceActionCommand.properties.action.enum,
    ['ACTIVATE', 'SUSPEND', 'ARCHIVE'],
  )
  assert.deepEqual(
    openapi.components.schemas.CollaborationGrant.allOf[1].properties.status.enum,
    ['DRAFT', 'ACTIVE', 'EXPIRED', 'REVOKED'],
  )
  const action =
    openapi.paths['/workspaces/{workspaceId}/actions'].post
  assert.ok(
    action.parameters.some(
      (parameter: { $ref?: string }) =>
        parameter.$ref === '#/components/parameters/IfMatch',
    ),
  )
  assert.ok(action.responses['409'])
})

import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'

import YAML from 'yaml'

const contractRoot = path.resolve(
  import.meta.dirname,
  '../../specs/002-pdp-product/contracts',
)

async function loadContracts() {
  const [openapiText, eventsText] = await Promise.all([
    readFile(path.join(contractRoot, 'openapi.yaml'), 'utf8'),
    readFile(path.join(contractRoot, 'events.md'), 'utf8'),
  ])

  return {
    openapi: YAML.parse(openapiText),
    eventsText,
  }
}

test('平台工作流公开操作保持稳定 operationId 与成功状态码', async () => {
  const { openapi } = await loadContracts()

  const operations = [
    ['/workflow-definitions/validate', 'post', 'validateWorkflowDefinition', '200'],
    ['/workflow-definitions/deploy', 'post', 'deployWorkflowDefinition', '201'],
    ['/workflow-instances/{workflowInstanceId}', 'get', 'getWorkflowInstance', '200'],
    [
      '/workflow-instances/{workflowInstanceId}/actions',
      'post',
      'applyWorkflowAdministrationAction',
      '202',
    ],
  ]

  for (const [resourcePath, method, operationId, successStatus] of operations) {
    const operation = openapi.paths?.[resourcePath]?.[method]
    assert.ok(operation, `缺少平台工作流操作：${method.toUpperCase()} ${resourcePath}`)
    assert.equal(operation.operationId, operationId)
    assert.ok(operation.responses?.[successStatus], `${operationId} 缺少 ${successStatus} 响应`)
  }
})

test('平台工作流失败语义与异步失败事件不得缺失', async () => {
  const { openapi, eventsText } = await loadContracts()

  assert.ok(openapi.paths['/workflow-definitions/deploy'].post.responses['409'])
  assert.ok(
    openapi.paths['/workflow-instances/{workflowInstanceId}'].get.responses['404'],
  )
  assert.ok(
    openapi.paths['/workflow-instances/{workflowInstanceId}/actions'].post.responses[
      '409'
    ],
  )
  assert.match(eventsText, /pdp\.workflow\.orchestration\.requested/)
  assert.match(eventsText, /pdp\.workflow\.orchestration\.failed/)
})

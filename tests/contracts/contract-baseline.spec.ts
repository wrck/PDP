import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'

import YAML from 'yaml'

const root = path.resolve(import.meta.dirname, '../..')
const contractRoot = path.join(root, 'specs/002-pdp-product/contracts')

test('OpenAPI、覆盖清单和前端 API 基线路径保持一致', async () => {
  const [openapiText, coverageText, clientText] = await Promise.all([
    readFile(path.join(contractRoot, 'openapi.yaml'), 'utf8'),
    readFile(path.join(contractRoot, 'coverage.md'), 'utf8'),
    readFile(path.join(root, 'apps/web/src/api/client.ts'), 'utf8'),
  ])
  const openapi = YAML.parse(openapiText)

  assert.deepEqual(openapi.servers, [{ url: '/api/v1' }])
  assert.match(clientText, /API_BASE_PATH = '\/api\/v1'/)
  assert.equal(openapi['x-pdp-contract-coverage'], './coverage.md')
  assert.match(coverageText, /覆盖率必须为 100%/)

  const operationIds: string[] = []
  for (const pathItem of Object.values(openapi.paths ?? {})) {
    for (const operation of Object.values(pathItem as Record<string, unknown>)) {
      if (
        operation &&
        typeof operation === 'object' &&
        'operationId' in operation
      ) {
        operationIds.push(String(operation.operationId))
      }
    }
  }

  assert.equal(new Set(operationIds).size, operationIds.length)
  assert.ok(operationIds.length >= 50)
})

test('JSON Schema 与事件标识保持唯一且具备稳定版本基线', async () => {
  const [domainSchemaText, migrationSchemaText, eventsText] = await Promise.all([
    readFile(path.join(contractRoot, 'domain-package.schema.json'), 'utf8'),
    readFile(path.join(contractRoot, 'migration-report.schema.json'), 'utf8'),
    readFile(path.join(contractRoot, 'events.md'), 'utf8'),
  ])
  const domainSchema = JSON.parse(domainSchemaText)
  const migrationSchema = JSON.parse(migrationSchemaText)

  for (const schema of [domainSchema, migrationSchema]) {
    assert.equal(typeof schema.$id, 'string')
    assert.match(schema.$schema, /json-schema\.org/)
  }

  const eventIds = [...eventsText.matchAll(/`(pdp\.[a-z0-9.]+)`/g)].map(
    (match) => match[1]!,
  )
  assert.ok(eventIds.length > 0)
  assert.equal(new Set(eventIds).size, eventIds.length)
})

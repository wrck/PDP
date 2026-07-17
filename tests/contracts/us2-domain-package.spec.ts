import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'

import { Ajv2020 } from 'ajv/dist/2020.js'
import YAML from 'yaml'

const contracts = path.resolve(
  import.meta.dirname,
  '../../specs/002-pdp-product/contracts',
)

test('US2 领域包生命周期和迁移操作必须形成稳定契约', async () => {
  const openapi = YAML.parse(
    await readFile(path.join(contracts, 'openapi.yaml'), 'utf8'),
  )
  const operations = [
    ['/domain-packages', 'post', 'createDomainPackage', '201'],
    [
      '/domain-packages/{packageId}/versions',
      'post',
      'createDomainPackageVersion',
      '201',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/validate',
      'post',
      'validateDomainPackageVersion',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/submit-review',
      'post',
      'submitDomainPackageVersionReview',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/review',
      'post',
      'reviewDomainPackageVersion',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/publish',
      'post',
      'publishDomainPackageVersion',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/retire',
      'post',
      'retireDomainPackageVersion',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/rollback',
      'post',
      'rollbackDomainPackageVersion',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/migration-preview',
      'post',
      'previewDomainPackageMigration',
      '200',
    ],
    [
      '/domain-packages/{packageId}/versions/{versionId}/migrations',
      'post',
      'startDomainPackageMigration',
      '202',
    ],
  ]

  for (const [resourcePath, method, operationId, successStatus] of operations) {
    const operation = openapi.paths?.[resourcePath]?.[method]
    assert.ok(operation, `缺少操作 ${method.toUpperCase()} ${resourcePath}`)
    assert.equal(operation.operationId, operationId)
    assert.ok(operation.responses?.[successStatus], `${operationId} 缺少 ${successStatus}`)
  }

  const version = openapi.components.schemas.DomainPackageVersion
  assert.deepEqual(version.properties.status.enum, [
    'DRAFT',
    'VALIDATED',
    'REVIEW_PENDING',
    'APPROVED',
    'PUBLISHED',
    'RETIRED',
    'ROLLED_BACK',
  ])
})

test('US2 manifest 必须约束三层继承、扩展治理和可回滚迁移', async () => {
  const schema = JSON.parse(
    await readFile(path.join(contracts, 'domain-package.schema.json'), 'utf8'),
  )
  const ajv = new Ajv2020({ allErrors: true, strict: false })
  const validate = ajv.compile(schema)

  const valid = {
    schemaVersion: '1.1',
    stableKey: 'network.cutover',
    name: '网络设备割接',
    layer: 'INDUSTRY',
    version: '1.0.0',
    extends: {
      packageKey: 'pdp.standard',
      versionRange: '^1.0.0',
    },
    objects: [
      {
        stableKey: 'cutover.plan',
        kind: 'NEW_OBJECT',
        label: { 'zh-CN': '割接计划' },
        fields: [
          {
            stableKey: 'cutover.window',
            label: { 'zh-CN': '割接窗口' },
            dataType: 'DATETIME',
            required: true,
          },
        ],
        states: [
          {
            stableKey: 'draft',
            label: { 'zh-CN': '草稿' },
            topLifecycleState: 'PLANNING',
            initial: true,
          },
        ],
      },
    ],
    extensions: [
      {
        stableKey: 'device.precheck',
        artifact: 'sha256:abc',
        entrypoint: 'precheck',
        permissions: ['device.read'],
        timeoutMs: 1000,
        isolation: 'PROCESS',
        signature: 'sig',
      },
    ],
    migrations: [
      {
        fromVersion: '0.9.0',
        toVersion: '1.0.0',
        steps: [{ type: 'SET_DEFAULT', parameters: { field: 'cutover.window' } }],
        rollback: { type: 'RESTORE_SNAPSHOT' },
      },
    ],
  }

  assert.equal(validate(valid), true, JSON.stringify(validate.errors))

  const invalid = structuredClone(valid) as {
    layer: string
    extends?: unknown
    extensions: Array<{ isolation: string }>
    migrations: Array<{ rollback: Record<string, unknown> }>
  }
  invalid.layer = 'WORKSPACE_CUSTOMER'
  delete invalid.extends
  invalid.extensions[0].isolation = 'NONE'
  invalid.migrations[0].rollback = {}
  assert.equal(validate(invalid), false)
})

test('网络设备割接示例包必须通过正式 manifest 契约', async () => {
  const schema = JSON.parse(
    await readFile(path.join(contracts, 'domain-package.schema.json'), 'utf8'),
  )
  const fixture = JSON.parse(
    await readFile(
      path.resolve(
        import.meta.dirname,
        '../fixtures/domain-package/network-cutover-package.json',
      ),
      'utf8',
    ),
  )
  const ajv = new Ajv2020({ allErrors: true, strict: false })
  const validate = ajv.compile(schema)
  assert.equal(validate(fixture), true, JSON.stringify(validate.errors))
})

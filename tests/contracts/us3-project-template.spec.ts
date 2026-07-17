import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'

import YAML from 'yaml'

const contractRoot = path.resolve(import.meta.dirname, '../../specs/002-pdp-product/contracts')

async function loadOpenApi() {
  return YAML.parse(await readFile(path.join(contractRoot, 'openapi.yaml'), 'utf8'))
}

interface OperationContract {
  parameters?: Array<{ $ref?: string }>
}

function hasParameter(operation: OperationContract, reference: string) {
  return operation.parameters?.some((parameter: { $ref?: string }) => parameter.$ref === reference)
}

test('US3 项目模板维护、版本治理和实例化预览操作保持稳定契约', async () => {
  const openapi = await loadOpenApi()
  const operations = [
    ['/project-templates', 'get', 'listProjectTemplates', '200'],
    ['/project-templates', 'post', 'createProjectTemplate', '201'],
    ['/project-templates/{templateId}/versions', 'get', 'listProjectTemplateVersions', '200'],
    ['/project-templates/{templateId}/versions', 'post', 'createProjectTemplateVersion', '201'],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}',
      'get',
      'getProjectTemplateVersion',
      '200',
    ],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}',
      'put',
      'updateProjectTemplateVersion',
      '200',
    ],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}/freeze',
      'post',
      'freezeProjectTemplateVersion',
      '200',
    ],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}/publish',
      'post',
      'publishProjectTemplateVersion',
      '200',
    ],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}/retire',
      'post',
      'retireProjectTemplateVersion',
      '200',
    ],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}/comparison',
      'get',
      'compareProjectTemplateVersions',
      '200',
    ],
    [
      '/project-templates/{templateId}/versions/{templateVersionId}/instantiation-preview',
      'post',
      'previewProjectTemplateInstantiation',
      '200',
    ],
  ]

  for (const [resourcePath, method, operationId, successStatus] of operations) {
    const operation = openapi.paths?.[resourcePath]?.[method]
    assert.ok(operation, `缺少操作 ${method.toUpperCase()} ${resourcePath}`)
    assert.equal(operation.operationId, operationId)
    assert.ok(operation.responses?.[successStatus], `${operationId} 缺少 ${successStatus}`)
  }

  const versionResource =
    openapi.paths['/project-templates/{templateId}/versions/{templateVersionId}']
  assert.ok(
    hasParameter(versionResource.put, '#/components/parameters/IfMatch'),
    '模板草稿更新必须使用 If-Match 防止并发覆盖',
  )

  for (const action of ['freeze', 'publish', 'retire']) {
    const operation =
      openapi.paths[`/project-templates/{templateId}/versions/{templateVersionId}/${action}`].post
    assert.ok(hasParameter(operation, '#/components/parameters/IfMatch'))
    assert.ok(hasParameter(operation, '#/components/parameters/IdempotencyKey'))
    assert.ok(operation.responses['409'])
  }
})

test('US3 模板版本状态和定义必须约束不可变发布及完整默认计划', async () => {
  const openapi = await loadOpenApi()
  const version = openapi.components.schemas.ProjectTemplateVersion
  assert.deepEqual(version.properties.status.enum, ['DRAFT', 'FROZEN', 'PUBLISHED', 'RETIRED'])
  assert.match(version.properties.status.description, /仅 DRAFT 可编辑/)
  assert.ok(version.required.includes('contentHash'))
  assert.match(version.properties.contentHash.description, /冻结时生成/)

  const definition = openapi.components.schemas.ProjectTemplateDefinition
  assert.deepEqual(definition.required, [
    'stages',
    'tasks',
    'ownerRules',
    'durationRules',
    'milestones',
    'checklistItems',
    'deliverables',
    'approvals',
    'views',
  ])
  for (const property of definition.required) {
    assert.equal(
      definition.properties[property].$ref,
      '#/components/schemas/ProjectTemplateComponentList',
    )
  }

  const preview = openapi.components.schemas.ProjectInstantiationPreview
  assert.ok(preview.required.includes('templateStatus'))
  assert.ok(preview.required.includes('templateContentHash'))
  assert.ok(preview.required.includes('generatedSummary'))
  assert.ok(preview.required.includes('validationIssues'))
  assert.ok(preview.required.includes('creatable'))
})

test('US3 从模板创建项目必须判别创建模式并一次返回原子实例化证据', async () => {
  const openapi = await loadOpenApi()
  const createOperation = openapi.paths['/projects'].post
  assert.equal(createOperation.operationId, 'createProject')
  assert.ok(hasParameter(createOperation, '#/components/parameters/IdempotencyKey'))
  assert.ok(createOperation.responses['409'])
  assert.ok(createOperation.responses['422'])
  assert.equal(
    createOperation.responses['201'].content['application/json'].schema.$ref,
    '#/components/schemas/ProjectInstantiationResult',
  )

  const command = openapi.components.schemas.CreateProjectCommand
  assert.equal(command.discriminator.propertyName, 'creationMode')
  assert.deepEqual(command.discriminator.mapping, {
    BLANK: '#/components/schemas/CreateBlankProjectCommand',
    TEMPLATE: '#/components/schemas/CreateProjectFromTemplateCommand',
  })

  const templateCommand = openapi.components.schemas.CreateProjectFromTemplateCommand.allOf[1]
  assert.deepEqual(templateCommand.required, ['creationMode', 'templateVersionId'])
  assert.equal(templateCommand.properties.creationMode.const, 'TEMPLATE')
  assert.match(templateCommand.properties.templateVersionId.description, /PUBLISHED 不可变版本/)

  const result = openapi.components.schemas.ProjectInstantiationResult
  assert.deepEqual(result.required, [
    'project',
    'creationMode',
    'templateVersionId',
    'templateContentHash',
    'domainPackageSnapshotId',
    'instantiationRecordId',
    'generatedSummary',
    'generatedObjects',
  ])
  assert.equal(result.properties.project.$ref, '#/components/schemas/Project')
  assert.equal(
    result.properties.generatedSummary.$ref,
    '#/components/schemas/ProjectInstantiationSummary',
  )
  assert.equal(
    result.properties.generatedObjects.items.$ref,
    '#/components/schemas/GeneratedProjectObjectRef',
  )

  const summary = openapi.components.schemas.ProjectInstantiationSummary
  assert.deepEqual(summary.required, [
    'stages',
    'tasks',
    'milestones',
    'checklistItems',
    'deliverables',
    'approvals',
    'views',
  ])
  assert.ok(
    summary.required.every((property: string) => summary.properties[property].minimum === 0),
  )
})

test('US3 覆盖矩阵必须保留需求追踪和准确率门禁', async () => {
  const coverage = await readFile(path.join(contractRoot, 'coverage.md'), 'utf8')
  assert.match(coverage, /US3 模板与创建（FR-021、FR-022、SC-002）/)
  assert.match(coverage, /仅已发布不可变版本可实例化/)
  assert.match(coverage, /原子创建、幂等返回/)
  assert.match(coverage, /生成对象准确率 100%/)
})

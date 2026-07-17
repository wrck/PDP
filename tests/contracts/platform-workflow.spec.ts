// 平台工作流契约失败测试（T077）
//
// 对应 spec.md FR-174（平台工作流公共基础能力）与 contracts/coverage.md
// “横向工作流编排”门禁行。本测试在实现（T078-T090）之前先固化契约不变量：
//   1. OpenAPI 工作流端点与 schema 完整且符合 BPMN 2.0.2 / 版本化 / 受控管理约束；
//   2. events.md 包含 pdp.workflow.orchestration.requested / failed 两个稳定事件；
//   3. 契约不得暴露 Flowable 私有 API、表名或内部异常文本；
//   4. 管理动作、状态机与幂等键约束与 FR-174 一致。
//
// 运行方式：`node --import tsx --test tests/contracts/platform-workflow.spec.ts`
// 依赖：js-yaml（tests/package.json devDependency）。

import { readFileSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const CONTRACTS_DIR = resolve(__dirname, '../../specs/002-pdp-product/contracts');
const OPENAPI_PATH = join(CONTRACTS_DIR, 'openapi.yaml');
const EVENTS_PATH = join(CONTRACTS_DIR, 'events.md');
const COVERAGE_PATH = join(CONTRACTS_DIR, 'coverage.md');

// 动态加载 js-yaml（与 validate-contracts.mjs 一致的降级策略）。
async function loadYaml(): Promise<any> {
  const mod = await import('js-yaml');
  return mod.default ?? mod;
}

async function loadOpenapi(): Promise<any> {
  const yaml = await loadYaml();
  const text = readFileSync(OPENAPI_PATH, 'utf8');
  return yaml.load(text);
}

function loadEventsText(): string {
  return readFileSync(EVENTS_PATH, 'utf8');
}

function loadCoverageText(): string {
  return readFileSync(COVERAGE_PATH, 'utf8');
}

// 在 components.schemas 中查找指定 schema。
function getSchema(doc: any, name: string): any {
  return doc?.components?.schemas?.[name];
}

// 在 paths 中查找指定路径对象。
function getPath(doc: any, path: string): any {
  return doc?.paths?.[path];
}

describe('平台工作流 OpenAPI 契约', () => {
  let doc: any;

  test.before(async () => {
    doc = await loadOpenapi();
  });

  test('workflow-definitions/validate 端点存在且为 POST', () => {
    const op = getPath(doc, '/workflow-definitions/validate');
    assert.ok(op, '缺少 /workflow-definitions/validate 路径');
    assert.ok(op.post, '/workflow-definitions/validate 必须为 POST');
    assert.equal(op.post.operationId, 'validateWorkflowDefinition');
    assert.deepEqual(op.post.tags, ['平台工作流']);
  });

  test('validate 请求体要求 processDefinitionKey、businessVersion、bpmnXml', () => {
    const body = getPath(doc, '/workflow-definitions/validate').post.requestBody;
    const schema = body.content['application/json'].schema;
    assert.deepEqual(schema.required.sort(), ['bpmnXml', 'businessVersion', 'processDefinitionKey']);
    // processDefinitionKey 必须有稳定命名约束（kebab-case 风格）。
    assert.ok(schema.properties.processDefinitionKey.pattern, 'processDefinitionKey 必须声明 pattern');
    // businessVersion 必须是语义化版本。
    assert.match(schema.properties.businessVersion.pattern, /\^?\[0\-9\]/, 'businessVersion 必须约束为语义化版本');
    // bpmnXml 必须有最小长度，防止空内容通过校验。
    assert.ok(schema.properties.bpmnXml.minLength >= 50, 'bpmnXml minLength 必须 >= 50');
  });

  test('validate 响应包含 valid、contentHash、findings', () => {
    const resp = getPath(doc, '/workflow-definitions/validate').post.responses['200'];
    const schema = resp.content['application/json'].schema;
    assert.deepEqual(schema.required.sort(), ['contentHash', 'findings', 'valid']);
    assert.equal(schema.properties.valid.type, 'boolean');
    assert.equal(schema.properties.contentHash.type, 'string');
    const finding = schema.properties.findings.items;
    assert.deepEqual(finding.required.sort(), ['code', 'message', 'severity']);
    assert.deepEqual(finding.properties.severity.enum.sort(), ['ERROR', 'WARNING']);
  });

  test('workflow-definitions/deploy 端点存在、为 POST 且支持幂等键', () => {
    const op = getPath(doc, '/workflow-definitions/deploy');
    assert.ok(op?.post, '缺少 /workflow-definitions/deploy 路径');
    assert.equal(op.post.operationId, 'deployWorkflowDefinition');
    const hasIdempotency = op.post.parameters?.some(
      (p: any) => p.$ref === '#/components/parameters/IdempotencyKey',
    );
    assert.ok(hasIdempotency, 'deploy 必须支持 IdempotencyKey 幂等键');
  });

  test('deploy 请求体要求 contentHash 以保证部署内容与校验一致', () => {
    const body = getPath(doc, '/workflow-definitions/deploy').post.requestBody;
    const schema = body.content['application/json'].schema;
    assert.ok(
      schema.required.includes('contentHash'),
      'deploy 请求必须包含 contentHash，确保部署内容与校验阶段一致',
    );
    assert.ok(schema.required.includes('bpmnResource'), 'deploy 请求必须包含 bpmnResource');
  });

  test('deploy 响应引用 WorkflowDefinitionSummary', () => {
    const resp = getPath(doc, '/workflow-definitions/deploy').post.responses['201'];
    assert.equal(
      resp.content['application/json'].schema.$ref,
      '#/components/schemas/WorkflowDefinitionSummary',
    );
  });

  test('workflow-instances/{id} 端点存在且为 GET', () => {
    const op = getPath(doc, '/workflow-instances/{workflowInstanceId}');
    assert.ok(op?.get, '缺少 /workflow-instances/{workflowInstanceId} 路径');
    assert.equal(op.get.operationId, 'getWorkflowInstance');
    assert.equal(op.get.responses['200'].content['application/json'].schema.$ref, '#/components/schemas/WorkflowInstanceSummary');
  });

  test('workflow-instances/{id}/actions 端点存在、为 POST 且支持幂等键', () => {
    const op = getPath(doc, '/workflow-instances/{workflowInstanceId}/actions');
    assert.ok(op?.post, '缺少 /workflow-instances/{workflowInstanceId}/actions 路径');
    assert.equal(op.post.operationId, 'applyWorkflowAdministrationAction');
    const hasIdempotency = op.post.parameters?.some(
      (p: any) => p.$ref === '#/components/parameters/IdempotencyKey',
    );
    assert.ok(hasIdempotency, 'actions 必须支持 IdempotencyKey 幂等键');
  });

  test('actions 响应为 202 JobAccepted（异步受控管理）', () => {
    const resp = getPath(doc, '/workflow-instances/{workflowInstanceId}/actions').post.responses;
    assert.ok(resp['202'], 'actions 必须返回 202 表示异步受控管理动作');
    assert.equal(
      resp['202'].content['application/json'].schema.$ref,
      '#/components/schemas/JobAccepted',
    );
    assert.ok(resp['409'], 'actions 必须声明 409 冲突响应');
  });
});

describe('平台工作流 Schema 契约', () => {
  let doc: any;

  test.before(async () => {
    doc = await loadOpenapi();
  });

  test('WorkflowDefinitionSummary 包含版本化与状态机字段', () => {
    const schema = getSchema(doc, 'WorkflowDefinitionSummary');
    assert.ok(schema, '缺少 WorkflowDefinitionSummary schema');
    const required = schema.required;
    for (const field of ['id', 'processDefinitionKey', 'businessVersion', 'contentHash', 'status', 'deployedAt']) {
      assert.ok(required.includes(field), `WorkflowDefinitionSummary 缺少必填字段 ${field}`);
    }
    // 定义状态机：VALIDATED → DEPLOYED → DEPRECATED → RETIRED（FR-174 版本化定义）。
    assert.deepEqual(
      schema.properties.status.enum.sort(),
      ['DEPRECATED', 'DEPLOYED', 'RETIRED', 'VALIDATED'],
    );
    assert.equal(schema.properties.deployedAt.format, 'date-time');
  });

  test('WorkflowInstanceSummary 包含实例状态机与诊断字段', () => {
    const schema = getSchema(doc, 'WorkflowInstanceSummary');
    assert.ok(schema, '缺少 WorkflowInstanceSummary schema');
    const required = schema.required;
    for (const field of ['id', 'definitionId', 'businessObjectRef', 'state', 'revision', 'incidentCount']) {
      assert.ok(required.includes(field), `WorkflowInstanceSummary 缺少必填字段 ${field}`);
    }
    // 实例状态机：STARTING/ACTIVE/SUSPENDED/COMPLETED/TERMINATED/INCIDENT（FR-174 运行诊断）。
    assert.deepEqual(
      schema.properties.state.enum.sort(),
      ['ACTIVE', 'COMPLETED', 'INCIDENT', 'STARTING', 'SUSPENDED', 'TERMINATED'],
    );
    // revision 用于乐观并发控制（受控管理动作期望版本）。
    assert.ok(schema.properties.revision.minimum === 0, 'revision 必须 >= 0');
    // incidentCount 用于运行诊断（死信/告警）。
    assert.ok(schema.properties.incidentCount.minimum === 0, 'incidentCount 必须 >= 0');
  });

  test('WorkflowAdminActionCommand 覆盖 FR-174 受控管理动作集', () => {
    const schema = getSchema(doc, 'WorkflowAdminActionCommand');
    assert.ok(schema, '缺少 WorkflowAdminActionCommand schema');
    // 受控管理动作：暂停、恢复、重试、迁移、终止、人工补偿（FR-174 受控迁移与补偿）。
    assert.deepEqual(
      schema.properties.action.enum.sort(),
      ['MANUAL_COMPENSATE', 'MIGRATE', 'PAUSE', 'RESUME', 'RETRY', 'TERMINATE'],
    );
    // 必须携带原因（审计要求）。
    assert.ok(schema.required.includes('reason'), '管理动作必须携带 reason（审计要求）');
    assert.ok(schema.properties.reason.minLength >= 5, 'reason minLength 必须 >= 5');
    // 必须携带期望版本（乐观并发控制）。
    assert.ok(schema.required.includes('expectedRevision'), '管理动作必须携带 expectedRevision（乐观并发控制）');
    // MIGRATE 动作需要目标定义（targetDefinitionId 可选字段存在）。
    assert.ok('targetDefinitionId' in schema.properties, 'MIGRATE 动作需要 targetDefinitionId 字段');
    // 高风险操作需要影响预览引用（impactPreviewId 可选字段存在，对应横向高风险操作门禁）。
    assert.ok('impactPreviewId' in schema.properties, '高风险管理动作需要 impactPreviewId 字段');
  });

  test('JobAccepted schema 用于异步管理动作确认', () => {
    const schema = getSchema(doc, 'JobAccepted');
    assert.ok(schema, '缺少 JobAccepted schema');
    assert.deepEqual(schema.required.sort(), ['jobId', 'status']);
    assert.equal(schema.properties.status.const, 'QUEUED');
  });
});

describe('平台工作流事件契约', () => {
  let eventsText: string;

  test.before(() => {
    eventsText = loadEventsText();
  });

  test('events.md 包含 pdp.workflow.orchestration.requested 事件', () => {
    assert.ok(
      eventsText.includes('pdp.workflow.orchestration.requested'),
      'events.md 必须包含 pdp.workflow.orchestration.requested 事件',
    );
  });

  test('events.md 包含 pdp.workflow.orchestration.failed 事件', () => {
    assert.ok(
      eventsText.includes('pdp.workflow.orchestration.failed'),
      'events.md 必须包含 pdp.workflow.orchestration.failed 事件',
    );
  });

  test('事件信封定义 eventVersion 版本契约', () => {
    assert.ok(eventsText.includes('eventVersion'), '事件信封必须定义 eventVersion 版本契约');
  });

  test('Flowable 隔离约束：业务消费者不得依赖 Flowable 内部', () => {
    // FR-174 / events.md：Flowable 只能由 workflow 模块消费，业务消费者不得依赖
    // Flowable 类名、表名、内部事件或异常文本。
    assert.ok(
      /Flowable/.test(eventsText),
      'events.md 必须声明 Flowable 隔离约束',
    );
  });
});

describe('平台工作流 Flowable 边界契约', () => {
  let doc: any;

  test.before(async () => {
    doc = await loadOpenapi();
  });

  test('OpenAPI 不得暴露 Flowable REST 私有端点', () => {
    // FR-174 / coverage.md：不暴露 Flowable API。
    // Flowable 内置 REST 通常以 /runtime、/repository、/task、/management、/history 等开头。
    const forbiddenPrefixes = [
      '/flowable',
      '/runtime',
      '/repository',
      '/process-runtime',
      '/workflow-engine',
    ];
    const paths = Object.keys(doc.paths ?? {});
    for (const prefix of forbiddenPrefixes) {
      const found = paths.filter((p) => p.startsWith(prefix));
      assert.deepEqual(
        found,
        [],
        `OpenAPI 不得暴露 Flowable 私有端点前缀 ${prefix}，实际发现：${found.join(', ')}`,
      );
    }
  });

  test('OpenAPI 工作流端点仅限四个稳定操作', () => {
    // FR-174 / ADR-0005：四类稳定端口（定义校验、部署、实例诊断、受控管理动作）。
    const expectedPaths = [
      '/workflow-definitions/validate',
      '/workflow-definitions/deploy',
      '/workflow-instances/{workflowInstanceId}',
      '/workflow-instances/{workflowInstanceId}/actions',
    ];
    const paths = Object.keys(doc.paths ?? {});
    const workflowPaths = paths.filter((p) => p.startsWith('/workflow'));
    assert.deepEqual(
      workflowPaths.sort(),
      expectedPaths.sort(),
      '工作流端点必须仅限四个稳定操作，不得暴露额外 Flowable 私有接口',
    );
  });
});

describe('平台工作流覆盖矩阵契约', () => {
  let coverageText: string;

  test.before(() => {
    coverageText = loadCoverageText();
  });

  test('coverage.md 包含横向工作流编排门禁行', () => {
    assert.ok(
      coverageText.includes('横向工作流编排'),
      'coverage.md 必须包含横向工作流编排门禁行',
    );
  });

  test('覆盖矩阵引用四个稳定端点', () => {
    assert.ok(
      coverageText.includes('/workflow-definitions/validate'),
      'coverage.md 必须引用 /workflow-definitions/validate',
    );
    assert.ok(
      coverageText.includes('/workflow-definitions/deploy'),
      'coverage.md 必须引用 /workflow-definitions/deploy',
    );
    assert.ok(
      coverageText.includes('/workflow-instances/{id}'),
      'coverage.md 必须引用 /workflow-instances/{id}',
    );
  });

  test('覆盖矩阵引用工作流编排事件', () => {
    assert.ok(
      coverageText.includes('pdp.workflow.orchestration.requested/failed'),
      'coverage.md 必须引用 pdp.workflow.orchestration.requested/failed 事件',
    );
  });

  test('覆盖矩阵声明不暴露 Flowable API', () => {
    assert.ok(
      /不暴露 Flowable API/.test(coverageText),
      'coverage.md 必须声明“不暴露 Flowable API”',
    );
  });

  test('覆盖矩阵声明验证维度覆盖 BPMN 校验、版本固定、幂等关联、权限复核、死信和 MySQL 升级', () => {
    // coverage.md 横向工作流编排行的兼容与验证列。
    const requiredDimensions = ['BPMN 校验', '版本固定', '幂等关联', '权限复核', '死信', 'MySQL 升级'];
    for (const dim of requiredDimensions) {
      assert.ok(
        coverageText.includes(dim),
        `coverage.md 必须声明验证维度：${dim}`,
      );
    }
  });
});

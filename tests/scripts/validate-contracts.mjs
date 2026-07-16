#!/usr/bin/env node
// PDP 契约校验脚本（T024）
//
// 校验范围：
//   1. openapi.yaml 是合法 YAML 且为 OpenAPI 3.x 文档，并包含 paths 对象。
//   2. domain-package.schema.json 与 migration-report.schema.json 是合法 JSON，
//      且 $schema 声明为 JSON Schema draft 2020-12；ajv 可用时进一步编译校验。
//   3. events.md 事件目录中每个事件具备名称与描述，且事件版本契约（eventVersion）已在信封定义。
//   4. coverage.md 覆盖矩阵中每个故事行（US*）均引用了 HTTP 契约。
//
// 运行：`node tests/scripts/validate-contracts.mjs`
// 退出码：全部通过或跳过返回 0；存在失败返回 1。
//
// 依赖说明：`js-yaml`（YAML 解析）与 `ajv`（draft 2020-12 编译校验）为
// tests/package.json 中的 devDependencies。脚本通过动态 import 加载，缺失时
// 给出清晰提示并降级跳过对应深度校验，而非崩溃。

import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const CONTRACTS_DIR = resolve(__dirname, '../../specs/002-pdp-product/contracts');
const OPENAPI_PATH = join(CONTRACTS_DIR, 'openapi.yaml');
const DOMAIN_SCHEMA_PATH = join(CONTRACTS_DIR, 'domain-package.schema.json');
const MIGRATION_SCHEMA_PATH = join(CONTRACTS_DIR, 'migration-report.schema.json');
const EVENTS_PATH = join(CONTRACTS_DIR, 'events.md');
const COVERAGE_PATH = join(CONTRACTS_DIR, 'coverage.md');

// 校验结果：{ check, status: 'pass'|'skip'|'fail', detail }
const results = [];

function record(check, status, detail = '') {
  results.push({ check, status, detail });
}

function readFileText(p) {
  if (!existsSync(p)) {
    throw new Error(`文件不存在：${p}`);
  }
  return readFileSync(p, 'utf8');
}

// 动态加载 js-yaml；缺失时返回 null（由调用方降级处理）。
async function loadYaml() {
  try {
    return await import('js-yaml');
  } catch {
    return null;
  }
}

// 动态加载 ajv draft 2020-12 构造器；缺失时返回 null。
async function loadAjv2020() {
  try {
    const mod = await import('ajv/dist/2020.js');
    const Ajv2020 = mod.default?.default ?? mod.default;
    if (typeof Ajv2020 !== 'function') return null;
    return new Ajv2020({ allErrors: true, strict: false });
  } catch {
    return null;
  }
}

// --- 通用 Markdown 表格解析 ---

function splitRow(line) {
  let s = line.trim();
  if (s.startsWith('|')) s = s.slice(1);
  if (s.endsWith('|')) s = s.slice(0, -1);
  return s.split('|').map((c) => c.trim());
}

function isSeparatorRow(line) {
  const cells = splitRow(line);
  return cells.length > 0 && cells.every((c) => /^:?-{3,}:?$/.test(c) || c === '');
}

// 在 markdown 中查找首个表头满足 headerPredicate 的表格，返回 { header, rows }。
function findTable(md, headerPredicate) {
  const lines = md.split(/\r?\n/);
  let i = 0;
  while (i < lines.length) {
    if (!lines[i].trim().startsWith('|')) {
      i += 1;
      continue;
    }
    const block = [];
    while (i < lines.length && lines[i].trim().startsWith('|')) {
      block.push(lines[i]);
      i += 1;
    }
    if (block.length >= 2) {
      const header = splitRow(block[0]);
      if (headerPredicate(header)) {
        const rows = block
          .slice(1)
          .filter((line) => !isSeparatorRow(line))
          .map(splitRow);
        return { header, rows };
      }
    }
  }
  return null;
}

// --- 1. OpenAPI 校验 ---

async function validateOpenapi() {
  const yaml = await loadYaml();
  if (!yaml) {
    record(
      'openapi.yaml 校验',
      'skip',
      '缺少依赖 js-yaml，已跳过 YAML 解析。安装：`pnpm --filter pdp-tests add -D js-yaml`',
    );
    return;
  }
  let doc;
  try {
    const text = readFileText(OPENAPI_PATH);
    doc = yaml.load(text);
  } catch (e) {
    record('openapi.yaml 解析', 'fail', e.message);
    return;
  }
  if (!doc || typeof doc !== 'object') {
    record('openapi.yaml 结构', 'fail', '解析结果不是对象');
    return;
  }
  if (typeof doc.openapi !== 'string' || !doc.openapi.startsWith('3.')) {
    record('openapi.yaml 版本', 'fail', `openapi 字段不是 3.x，实际为 ${String(doc.openapi)}`);
    return;
  }
  if (!doc.paths || typeof doc.paths !== 'object') {
    record('openapi.yaml paths', 'fail', '缺少 paths 对象');
    return;
  }
  const pathCount = Object.keys(doc.paths).length;
  record('openapi.yaml 校验', 'pass', `OpenAPI ${doc.openapi}，${pathCount} 个路径`);
}

// --- 2. JSON Schema 校验 ---

async function validateJsonSchema(file, label) {
  const ajv = await loadAjv2020();
  let schema;
  try {
    schema = JSON.parse(readFileText(file));
  } catch (e) {
    record(`${label} JSON 解析`, 'fail', e.message);
    return;
  }
  if (typeof schema !== 'object' || schema === null) {
    record(`${label} 结构`, 'fail', 'Schema 不是 JSON 对象');
    return;
  }
  const draftOk =
    typeof schema.$schema === 'string' && schema.$schema.includes('2020-12');
  if (!draftOk) {
    record(
      `${label} draft 版本`,
      'fail',
      `$schema 不是 draft 2020-12，实际为 ${String(schema.$schema)}`,
    );
    return;
  }
  if (!ajv) {
    record(
      `${label} draft 2020-12`,
      'pass',
      'JSON 合法且 $schema 为 draft 2020-12；ajv 缺失，未做编译校验。安装：`pnpm --filter pdp-tests add -D ajv`',
    );
    return;
  }
  try {
    const valid = ajv.validateSchema(schema);
    if (!valid) {
      record(`${label} draft 2020-12`, 'fail', ajv.errorsText(ajv.errors));
      return;
    }
    ajv.compile(schema); // 确认 schema 可编译
    record(`${label} draft 2020-12`, 'pass', '结构合法且可编译');
  } catch (e) {
    record(`${label} draft 2020-12`, 'fail', e.message);
  }
}

// --- 3. events.md 事件目录校验 ---

function validateEvents() {
  let md;
  try {
    md = readFileText(EVENTS_PATH);
  } catch (e) {
    record('events.md 读取', 'fail', e.message);
    return;
  }

  const table = findTable(md, (h) => h.some((c) => c.includes('事件类型')));
  if (!table) {
    record('events.md 事件目录', 'fail', '未找到事件目录表（表头需包含“事件类型”）');
    return;
  }

  // 事件版本契约：信封中应定义 eventVersion。
  const versionDefined = /eventVersion/.test(md);

  let checked = 0;
  const problems = [];
  for (const row of table.rows) {
    if (row.length < 2) {
      problems.push('存在列数不足的事件行');
      continue;
    }
    const rawName = row[0];
    const name = rawName.replace(/`/g, '').trim();
    const desc = row[1].trim();
    checked += 1;
    if (!name) {
      problems.push(`事件缺少名称（行：${rawName}）`);
    } else if (!/^pdp\./.test(name)) {
      problems.push(`事件名称不以 pdp. 开头：${name}`);
    }
    if (!desc) {
      problems.push(`事件缺少描述：${name || rawName}`);
    }
  }

  if (!versionDefined) {
    problems.push('事件信封未定义 eventVersion 版本契约');
  }

  if (problems.length > 0) {
    record(
      'events.md 事件目录',
      'fail',
      `${checked} 个事件；${problems.length} 个问题：${problems.slice(0, 3).join('；')}${problems.length > 3 ? ' …' : ''}`,
    );
    return;
  }
  record(
    'events.md 事件目录',
    'pass',
    `${checked} 个事件均具名称与描述；事件版本契约（eventVersion）已在信封定义`,
  );
}

// --- 4. coverage.md 故事覆盖校验 ---

function validateCoverage() {
  let md;
  try {
    md = readFileText(COVERAGE_PATH);
  } catch (e) {
    record('coverage.md 读取', 'fail', e.message);
    return;
  }

  const table = findTable(
    md,
    (h) => h.some((c) => c.includes('HTTP')) && h.some((c) => c.includes('故事')),
  );
  if (!table) {
    record('coverage.md 故事覆盖', 'fail', '未找到覆盖矩阵表（表头需包含“故事”与“HTTP”）');
    return;
  }

  const httpIdx = table.header.findIndex((c) => c.includes('HTTP'));
  const storyRe = /US\d+/;
  let stories = 0;
  const missing = [];

  for (const row of table.rows) {
    if (row.length === 0) continue;
    const firstCell = row[0];
    if (!storyRe.test(firstCell)) continue; // 仅校验故事行，跳过横向门禁行
    stories += 1;
    const http = httpIdx >= 0 && row[httpIdx] ? row[httpIdx].trim() : '';
    if (!http || http === '-') {
      missing.push(firstCell.split(/\s+/)[0]);
    }
  }

  if (missing.length > 0) {
    record(
      'coverage.md 故事覆盖',
      'fail',
      `${stories} 个故事行；缺少 HTTP 契约引用：${missing.join(', ')}`,
    );
    return;
  }
  record('coverage.md 故事覆盖', 'pass', `${stories} 个故事行均引用 HTTP 契约`);
}

// --- 汇总输出 ---

function printSummary() {
  const statusLabel = { pass: 'PASS', skip: 'SKIP', fail: 'FAIL' };
  const nameWidth = Math.max(8, ...results.map((r) => r.check.length));
  const bar = '='.repeat(nameWidth + 50);
  console.log('\nPDP 契约校验');
  console.log(bar);
  console.log(
    `${'检查项'.padEnd(nameWidth)}  ${'状态'.padEnd(6)}  详情`,
  );
  console.log('-'.repeat(nameWidth + 50));
  for (const r of results) {
    console.log(
      `${r.check.padEnd(nameWidth)}  ${statusLabel[r.status].padEnd(6)}  ${r.detail}`,
    );
  }
  console.log(bar);
  const pass = results.filter((r) => r.status === 'pass').length;
  const skip = results.filter((r) => r.status === 'skip').length;
  const fail = results.filter((r) => r.status === 'fail').length;
  console.log(`结果：${pass} 通过 / ${skip} 跳过 / ${fail} 失败\n`);
  return fail;
}

async function main() {
  await validateOpenapi();
  await validateJsonSchema(DOMAIN_SCHEMA_PATH, 'PDP 领域包清单');
  await validateJsonSchema(MIGRATION_SCHEMA_PATH, 'PDP 迁移报告');
  validateEvents();
  validateCoverage();
  const fail = printSummary();
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error('契约校验发生未预期错误：', e);
  process.exit(1);
});

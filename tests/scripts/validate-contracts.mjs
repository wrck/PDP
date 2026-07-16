import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import Ajv2020 from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import YAML from 'yaml'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const contracts = path.join(root, 'specs/002-pdp-product/contracts')

const openapi = YAML.parse(fs.readFileSync(path.join(contracts, 'openapi.yaml'), 'utf8'))
if (openapi.openapi !== '3.1.0') {
  throw new Error(`OpenAPI 版本必须为 3.1.0，当前为 ${openapi.openapi}`)
}

const operationIds = []
for (const pathItem of Object.values(openapi.paths ?? {})) {
  for (const operation of Object.values(pathItem ?? {})) {
    if (operation && typeof operation === 'object' && operation.operationId) {
      operationIds.push(operation.operationId)
    }
  }
}
if (new Set(operationIds).size !== operationIds.length) {
  throw new Error('OpenAPI operationId 存在重复')
}

const ajv = new Ajv2020({ allErrors: true, strict: false })
addFormats(ajv)
for (const file of ['domain-package.schema.json', 'migration-report.schema.json']) {
  const schema = JSON.parse(fs.readFileSync(path.join(contracts, file), 'utf8'))
  ajv.compile(schema)
}

console.log(`契约校验通过：${operationIds.length} 个 OpenAPI 操作，2 个 JSON Schema`)


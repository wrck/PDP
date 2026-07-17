/**
 * JSON Schema 渲染基础组件类型定义（T093、FR-009、FR-063）。
 *
 * 与 JSON Schema Draft 2020-12 对齐，用于领域包元模型驱动的表单、表格与详情渲染。
 * 后端领域包定义字段、关系、页面、状态、规则、动作和权限（FR-009），
 * 前端通过此组件库将领域包元模型渲染为可交互 UI。
 *
 * <p><strong>设计约束</strong>：
 * <ul>
 *   <li>字段权限由 v-permission 指令控制（FR-063、FR-066），敏感字段独立授权；</li>
 *   <li>校验规则与 JSON Schema 一致，前端校验为用户体验优化，后端为安全边界；</li>
 *   <li>组件不直接依赖 Ant Design Vue 业务组件，通过 props 注入，便于未来替换 UI 库。</li>
 * </ul>
 */

/** JSON Schema 类型关键字（Draft 2020-12）。 */
export type JsonSchemaType =
  | 'string'
  | 'number'
  | 'integer'
  | 'boolean'
  | 'array'
  | 'object'
  | 'null'

/** 字段格式约束（JSON Schema format）。 */
export type FieldFormat =
  | 'date-time'
  | 'date'
  | 'time'
  | 'duration'
  | 'email'
  | 'idn-email'
  | 'hostname'
  | 'idn-hostname'
  | 'ipv4'
  | 'ipv6'
  | 'uri'
  | 'uri-reference'
  | 'iri'
  | 'iri-reference'
  | 'uuid'
  | 'json-pointer'
  | 'relative-json-pointer'
  | 'regex'
  | 'password'
  | 'text'
  | 'textarea'
  | 'rich-text'
  | 'code'
  | 'reference'
  | 'attachment'

/** 字段 UI 渲染提示（扩展 JSON Schema 的 ui:hint）。 */
export type FieldWidget =
  | 'input'
  | 'textarea'
  | 'number'
  | 'switch'
  | 'date-picker'
  | 'date-range-picker'
  | 'time-picker'
  | 'select'
  | 'radio'
  | 'checkbox'
  | 'cascader'
  | 'tree-select'
  | 'auto-complete'
  | 'transfer'
  | 'upload'
  | 'rich-text'
  | 'code-editor'
  | 'password'
  | 'color-picker'
  | 'rate'
  | 'slider'
  | 'mention'
  | 'tag-input'
  | 'reference-picker'

/** 字段元模型（领域包字段定义的运行时表示）。 */
export interface FieldSchema {
  /** 字段稳定键（如 `network.device.name`）。 */
  key: string
  /** 显示名称。 */
  label: string
  /** 字段描述/帮助文本。 */
  description?: string
  /** JSON Schema 类型。 */
  type: JsonSchemaType | JsonSchemaType[]
  /** 格式约束。 */
  format?: FieldFormat
  /** UI 渲染提示。 */
  widget?: FieldWidget
  /** 是否必填（JSON Schema required 由父级声明，此处冗余便于 UI 渲染）。 */
  required?: boolean
  /** 是否只读。 */
  readOnly?: boolean
  /** 是否写唯一（创建后不可修改）。 */
  writeOnce?: boolean
  /** 是否敏感字段（FR-066 独立授权）。 */
  sensitive?: boolean
  /** 默认值。 */
  default?: unknown
  /** 枚举可选值（type 为 string/number 时适用）。 */
  enum?: Array<{ label: string; value: string | number }>
  /** 最小长度/值。 */
  minimum?: number
  /** 最大长度/值。 */
  maximum?: number
  /** 正则校验模式。 */
  pattern?: string
  /** 最小项数（数组类型）。 */
  minItems?: number
  /** 最大项数（数组类型）。 */
  maxItems?: number
  /** 引用对象类型（reference 格式时使用）。 */
  referenceType?: string
  /** 引用过滤条件（reference 格式时使用）。 */
  referenceFilter?: Record<string, unknown>
  /** 字段权限稳定键（FR-063，未授权时隐藏）。 */
  permission?: string
  /** 字段占位符。 */
  placeholder?: string
  /** 字段帮助文本。 */
  help?: string
  /** 字段所属分组（用于表单分区展示）。 */
  group?: string
  /** 字段跨列数（响应式布局，1-24）。 */
  span?: number
}

/** 对象元模型（领域包对象定义的运行时表示）。 */
export interface ObjectSchema {
  /** 对象稳定键（如 `network.device`）。 */
  key: string
  /** 显示名称。 */
  label: string
  /** 对象描述。 */
  description?: string
  /** 对象图标。 */
  icon?: string
  /** 字段列表。 */
  fields: FieldSchema[]
  /** 主键字段键列表（用于表格行键与详情标识）。 */
  primaryKeyFields: string[]
  /** 显示字段键列表（用于表格列与列表展示）。 */
  displayFields: string[]
  /** 默认排序字段。 */
  defaultSort?: { field: string; order: 'asc' | 'desc' }
  /** 对象权限稳定键。 */
  permission?: string
  /** 是否支持分页。 */
  pageable?: boolean
  /** 是否支持搜索。 */
  searchable?: boolean
  /** 搜索字段键列表。 */
  searchFields?: string[]
  /** 是否支持导出。 */
  exportable?: boolean
  /** 是否支持批量操作。 */
  batchable?: boolean
}

/** 表格列定义（从 ObjectSchema 派生）。 */
export interface TableColumn {
  /** 字段键。 */
  key: string
  /** 列标题。 */
  title: string
  /** 列宽。 */
  width?: number | string
  /** 是否固定列。 */
  fixed?: 'left' | 'right'
  /** 是否可排序。 */
  sortable?: boolean
  /** 是否可筛选。 */
  filterable?: boolean
  /** 是否可编辑（行内编辑）。 */
  editable?: boolean
  /** 是否省略过长内容。 */
  ellipsis?: boolean
  /** 自定义渲染槽名。 */
  slot?: string
}

/** 表单值（字段键到值的映射）。 */
export type FormValue = Record<string, unknown>

/** 表单校验错误（字段键到错误消息的映射）。 */
export type FormErrors = Record<string, string>

/** 表格行数据。 */
export type TableRow = Record<string, unknown>

/** 详情数据。 */
export type DetailData = Record<string, unknown>

/** 校验结果。 */
export interface ValidationResult {
  valid: boolean
  errors: FormErrors
}

/** 表单提交事件载荷。 */
export interface FormSubmitPayload {
  value: FormValue
  valid: boolean
  errors: FormErrors
}

/** 字段解析后的渲染配置（合并 schema 与运行时状态）。 */
export interface ResolvedField extends FieldSchema {
  /** 当前值。 */
  value: unknown
  /** 当前错误。 */
  error?: string
  /** 是否禁用（运行时计算）。 */
  disabled: boolean
  /** 是否可见（权限与条件控制）。 */
  visible: boolean
}

/**
 * 将 JSON Schema type 字符串规范化为单一类型（取首个非 null 类型）。
 */
export function normalizeType(type: JsonSchemaType | JsonSchemaType[]): JsonSchemaType {
  if (typeof type === 'string') return type
  return type.find((t) => t !== 'null') ?? 'string'
}

/**
 * 判断字段是否为引用类型。
 */
export function isReferenceField(field: FieldSchema): boolean {
  return field.format === 'reference' || field.widget === 'reference-picker'
}

/**
 * 判断字段是否为敏感字段（FR-066 独立授权）。
 */
export function isSensitiveField(field: FieldSchema): boolean {
  return field.sensitive === true
}

/**
 * 判断字段是否为数组类型。
 */
export function isArrayField(field: FieldSchema): boolean {
  return normalizeType(field.type) === 'array'
}

/**
 * 判断字段是否为对象类型。
 */
export function isObjectField(field: FieldSchema): boolean {
  return normalizeType(field.type) === 'object'
}

/**
 * 判断字段是否为布尔类型。
 */
export function isBooleanField(field: FieldSchema): boolean {
  return normalizeType(field.type) === 'boolean'
}

/**
 * 判断字段是否为数值类型。
 */
export function isNumberField(field: FieldSchema): boolean {
  const t = normalizeType(field.type)
  return t === 'number' || t === 'integer'
}

/**
 * 判断字段是否为字符串类型。
 */
export function isStringField(field: FieldSchema): boolean {
  return normalizeType(field.type) === 'string'
}

/**
 * 根据字段 schema 推断默认 widget。
 */
export function inferWidget(field: FieldSchema): FieldWidget {
  if (field.widget) return field.widget
  if (field.enum && field.enum.length > 0) return 'select'
  if (field.format === 'date-time') return 'date-picker'
  if (field.format === 'date') return 'date-picker'
  if (field.format === 'time') return 'time-picker'
  if (field.format === 'email') return 'input'
  if (field.format === 'uri') return 'input'
  if (field.format === 'uuid') return 'input'
  if (field.format === 'password') return 'password'
  if (field.format === 'textarea' || field.format === 'rich-text') return 'textarea'
  if (field.format === 'code') return 'code-editor'
  if (field.format === 'attachment') return 'upload'
  if (field.format === 'reference') return 'reference-picker'
  const type = normalizeType(field.type)
  switch (type) {
    case 'boolean':
      return 'switch'
    case 'number':
    case 'integer':
      return 'number'
    case 'array':
      return 'checkbox'
    default:
      return 'input'
  }
}

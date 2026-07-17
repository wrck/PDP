/**
 * JSON Schema 字段级校验工具（T093、FR-009）。
 *
 * 与 JSON Schema Draft 2020-12 校验语义对齐，但不引入完整 ajv 运行时依赖，
 * 仅实现 PDP 领域包字段定义所需的子集（type/format/required/enum/min-max/pattern）。
 * 后端为安全边界，前端校验仅为用户体验优化。
 */

import {
  isArrayField,
  isBooleanField,
  isNumberField,
  isStringField,
  normalizeType,
  type FieldSchema,
  type FormErrors,
  type FormValue,
  type ValidationResult,
} from './types'

/** 空值判定（null、undefined、空字符串、空数组视为空）。 */
export function isEmpty(value: unknown): boolean {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

/** 校验单个字段，返回错误消息（无错误返回 undefined）。 */
export function validateField(field: FieldSchema, value: unknown): string | undefined {
  // 必填校验
  if (field.required && isEmpty(value)) {
    return `${field.label}不能为空`
  }

  // 空值跳过后续约束校验
  if (isEmpty(value)) return undefined

  const type = normalizeType(field.type)

  // 类型与范围校验
  if (isNumberField(field)) {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      return `${field.label}必须是数值`
    }
    if (type === 'integer' && !Number.isInteger(value)) {
      return `${field.label}必须是整数`
    }
    if (field.minimum !== undefined && value < field.minimum) {
      return `${field.label}不能小于 ${field.minimum}`
    }
    if (field.maximum !== undefined && value > field.maximum) {
      return `${field.label}不能大于 ${field.maximum}`
    }
  }

  if (isStringField(field)) {
    if (typeof value !== 'string') {
      return `${field.label}必须是文本`
    }
    if (field.minimum !== undefined && value.length < field.minimum) {
      return `${field.label}长度不能少于 ${field.minimum} 个字符`
    }
    if (field.maximum !== undefined && value.length > field.maximum) {
      return `${field.label}长度不能超过 ${field.maximum} 个字符`
    }
    if (field.pattern) {
      const re = safeRegExp(field.pattern)
      if (re && !re.test(value)) {
        return `${field.label}格式不正确`
      }
    }
    if (field.enum && field.enum.length > 0) {
      const matched = field.enum.some((opt) => opt.value === value)
      if (!matched) return `${field.label}取值不在允许范围内`
    }
  }

  if (isBooleanField(field)) {
    if (typeof value !== 'boolean') {
      return `${field.label}必须是布尔值`
    }
  }

  if (isArrayField(field)) {
    if (!Array.isArray(value)) {
      return `${field.label}必须是数组`
    }
    if (field.minItems !== undefined && value.length < field.minItems) {
      return `${field.label}至少需要 ${field.minItems} 项`
    }
    if (field.maxItems !== undefined && value.length > field.maxItems) {
      return `${field.label}不能超过 ${field.maxItems} 项`
    }
  }

  // format 软校验
  if (isStringField(field) && typeof value === 'string') {
    const formatError = validateFormat(field, value)
    if (formatError) return formatError
  }

  return undefined
}

/** 校验整个对象表单。 */
export function validateObject(
  fields: FieldSchema[],
  value: FormValue,
): ValidationResult {
  const errors: FormErrors = {}
  for (const field of fields) {
    const err = validateField(field, value[field.key])
    if (err) errors[field.key] = err
  }
  return { valid: Object.keys(errors).length === 0, errors }
}

/** 格式软校验（不抛错，仅返回错误消息）。 */
function validateFormat(field: FieldSchema, value: string): string | undefined {
  switch (field.format) {
    case 'email':
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
        return `${field.label}邮箱格式不正确`
      }
      break
    case 'uri':
    case 'iri':
      try {
        // eslint-disable-next-line no-new
        new URL(value)
      } catch {
        return `${field.label}URL 格式不正确`
      }
      break
    case 'uuid':
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) {
        return `${field.label}UUID 格式不正确`
      }
      break
    case 'ipv4':
      if (!/^(\d{1,3}\.){3}\d{1,3}$/.test(value) || value.split('.').some((p) => Number(p) > 255)) {
        return `${field.label}IPv4 格式不正确`
      }
      break
    case 'date':
      if (Number.isNaN(Date.parse(value))) {
        return `${field.label}日期格式不正确`
      }
      break
    case 'date-time':
      if (Number.isNaN(Date.parse(value))) {
        return `${field.label}日期时间格式不正确`
      }
      break
    default:
      return undefined
  }
  return undefined
}

/** 安全构造正则（无效 pattern 返回 null）。 */
function safeRegExp(pattern: string): RegExp | null {
  try {
    return new RegExp(pattern)
  } catch {
    return null
  }
}

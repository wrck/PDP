<script setup lang="ts">
/**
 * JSON Schema 详情视图组件（T093、FR-009、FR-063、FR-066）。
 *
 * 基于 {@link ObjectSchema} 渲染只读详情视图，支持：
 * <ul>
 *   <li>字段分组展示；</li>
 *   <li>敏感字段脱敏展示（FR-066）；</li>
 *   <li>字段权限控制（FR-063）；</li>
 *   <li>枚举字段映射标签；</li>
 *   <li>自定义渲染槽（slot:field-{key}）。</li>
 * </ul>
 */
import { computed } from 'vue'
import {
  Descriptions as ADescriptions,
  DescriptionsItem as ADescriptionsItem,
  Tag as ATag,
  Divider as ADivider,
  Tooltip as ATooltip,
  Empty as AEmpty,
} from 'ant-design-vue'
import { LockOutlined } from '@ant-design/icons-vue'
import {
  isSensitiveField,
  type DetailData,
  type FieldSchema,
  type ObjectSchema,
} from './types'

const props = defineProps<{
  /** 对象元模型。 */
  schema: ObjectSchema
  /** 详情数据。 */
  data: DetailData
  /** 描述列表布局。 */
  layout?: 'horizontal' | 'vertical'
  /** 列数。 */
  column?: number | Partial<Record<string, number>>
}>()

const layout = computed(() => props.layout ?? 'horizontal')
const column = computed(() => props.column ?? 2)

/** 按分组组织字段。 */
const groupedFields = computed(() => {
  const groups = new Map<string, FieldSchema[]>()
  for (const field of props.schema.fields) {
    const g = field.group ?? '_default'
    if (!groups.has(g)) groups.set(g, [])
    groups.get(g)!.push(field)
  }
  return Array.from(groups.entries()).map(([key, fields]) => ({
    key,
    label: key === '_default' ? props.schema.label : key,
    fields,
  }))
})

/** 格式化字段值。 */
function formatValue(field: FieldSchema, value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'

  // 敏感字段脱敏
  if (isSensitiveField(field)) {
    return maskValue(value)
  }

  // 枚举字段
  if (field.enum && field.enum.length > 0) {
    const opt = field.enum.find((o) => o.value === value)
    return opt?.label ?? String(value)
  }

  // 布尔字段
  const t = typeof field.type === 'string' ? field.type : field.type[0]
  if (t === 'boolean') {
    return value === true ? '是' : value === false ? '否' : '-'
  }

  // 数组
  if (Array.isArray(value)) {
    return value.length === 0 ? '-' : value.join(', ')
  }

  // 日期时间
  if (field.format === 'date' || field.format === 'date-time') {
    const d = new Date(String(value))
    if (!Number.isNaN(d.getTime())) {
      return field.format === 'date-time'
        ? d.toLocaleString('zh-CN')
        : d.toLocaleDateString('zh-CN')
    }
  }

  return String(value)
}

/** 敏感字段脱敏。 */
function maskValue(value: unknown): string {
  const s = String(value)
  if (s.length <= 2) return '*'.repeat(s.length)
  return `${s[0]}${'*'.repeat(Math.min(s.length - 2, 6))}${s[s.length - 1]}`
}

/** 是否为枚举字段。 */
function isEnumField(field: FieldSchema): boolean {
  return !!(field.enum && field.enum.length > 0) && !isSensitiveField(field)
}

/** 获取枚举值对应的标签颜色。 */
function enumTagColor(_value: unknown): string {
  return 'blue'
}
</script>

<template>
  <div class="schema-detail">
    <AEmpty v-if="groupedFields.length === 0" description="无字段定义" />

    <template v-for="group in groupedFields" :key="group.key">
      <ADivider v-if="group.key !== '_default'" orientation="left" plain>
        {{ group.label }}
      </ADivider>

      <ADescriptions :layout="layout" :column="column" bordered size="middle">
        <ADescriptionsItem
          v-for="field in group.fields"
          :key="field.key"
          :label="field.label"
          :span="field.span ?? 1"
        >
          <template #label>
            <span>{{ field.label }}</span>
            <ATooltip v-if="isSensitiveField(field)" title="敏感字段">
              <LockOutlined class="schema-detail__sensitive-icon" />
            </ATooltip>
          </template>

          <div v-permission="field.permission" class="schema-detail__value">
            <slot
              v-if="$slots[`field-${field.key}`]"
              :name="`field-${field.key}`"
              :value="data[field.key]"
              :data="data"
            />
            <ATag
              v-else-if="isEnumField(field)"
              :color="enumTagColor(data[field.key])"
            >
              {{ formatValue(field, data[field.key]) }}
            </ATag>
            <span v-else>{{ formatValue(field, data[field.key]) }}</span>
          </div>
        </ADescriptionsItem>
      </ADescriptions>
    </template>
  </div>
</template>

<style scoped lang="scss">
.schema-detail {
  &__sensitive-icon {
    margin-left: 4px;
    color: var(--ant-color-warning, #faad14);
    font-size: 12px;
  }

  &__value {
    word-break: break-all;
  }
}
</style>

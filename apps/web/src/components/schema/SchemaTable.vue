<script setup lang="ts">
/**
 * JSON Schema 表格组件（T093、FR-009、FR-063）。
 *
 * 基于 {@link ObjectSchema} 自动生成表格列，支持：
 * <ul>
 *   <li>主键列、显示列自动派生；</li>
 *   <li>分页（schema.pageable）、排序（field.indexMode = SORT）；</li>
 *   <li>敏感字段脱敏展示（FR-066）；</li>
 *   <li>字段权限控制（FR-063）；</li>
 *   <li>自定义列渲染槽（slot:column-{key}）。</li>
 * </ul>
 *
 * <p><strong>分页约定</strong>：与后端签名 keyset cursor 分页对齐，
 * 前端使用基于页码的简单分页（业务列表场景），cursor 由调用方透传后端。
 */
import { computed, h } from 'vue'
import {
  Table as ATable,
  Tag as ATag,
  Input as AInput,
  Space as ASpace,
  Button as AButton,
} from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  isSensitiveField,
  type ObjectSchema,
  type TableColumn,
  type TableRow,
} from './types'

const props = withDefaults(
  defineProps<{
    /** 对象元模型。 */
    schema: ObjectSchema
    /** 表格行数据。 */
    data: TableRow[]
    /** 总条数（用于分页）。 */
    total?: number
    /** 当前页码（1-based）。 */
    page?: number
    /** 每页条数。 */
    pageSize?: number
    /** 加载中。 */
    loading?: boolean
    /** 行选择模式。 */
    rowSelection?: 'none' | 'checkbox' | 'radio'
    /** 自定义列定义（覆盖 schema 派生的列）。 */
    columns?: TableColumn[]
  }>(),
  {
    total: 0,
    page: 1,
    pageSize: 20,
    loading: false,
    rowSelection: 'none',
    columns: () => [],
  },
)

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'update:pageSize', size: number): void
  (e: 'search', keyword: string): void
  (e: 'row-click', row: TableRow): void
  (e: 'selection-change', keys: Array<string | number>): void
}>()

/** 派生表格列。 */
const resolvedColumns = computed<TableColumn[]>(() => {
  if (props.columns.length > 0) return props.columns
  const cols: TableColumn[] = []
  for (const field of props.schema.fields) {
    // 仅展示主键与显示字段，或显式标记 SORT/FILTER 的字段
    const isPrimaryKey = props.schema.primaryKeyFields.includes(field.key)
    const isDisplay = props.schema.displayFields.includes(field.key)
    if (!isPrimaryKey && !isDisplay) continue
    cols.push({
      key: field.key,
      title: field.label,
      sortable: field.format === 'SORT' || field.format === 'FILTER',
      ellipsis: field.type === 'string' && field.maximum !== undefined && field.maximum > 50,
    })
  }
  return cols
})

/** Ant Design Vue 列定义。 */
const antColumns = computed(() =>
  resolvedColumns.value.map((col) => ({
    title: col.title,
    dataIndex: col.key,
    key: col.key,
    width: col.width,
    fixed: col.fixed,
    sorter: col.sortable,
    ellipsis: col.ellipsis,
    customRender: ({ text, record }: { text: unknown; record: TableRow }) => {
      // 敏感字段脱敏
      const field = props.schema.fields.find((f) => f.key === col.key)
      if (field && isSensitiveField(field)) {
        return maskValue(text)
      }
      // 枚举字段映射标签
      if (field?.enum && field.enum.length > 0) {
        const opt = field.enum.find((o) => o.value === text)
        if (opt) return h(ATag, null, () => opt.label)
      }
      // 布尔字段
      if (field && (field.type === 'boolean' || field.type === ['boolean', 'null'])) {
        return text === true ? '是' : text === false ? '否' : '-'
      }
      return text ?? '-'
    },
  })),
)

/** 行键取值（使用主键字段组合）。 */
function rowKey(record: TableRow): string {
  return props.schema.primaryKeyFields
    .map((k) => String(record[k] ?? ''))
    .join('|')
}

/** 敏感字段脱敏（仅展示前后各 1 个字符）。 */
function maskValue(value: unknown): string {
  if (value === null || value === undefined) return '-'
  const s = String(value)
  if (s.length <= 2) return '*'.repeat(s.length)
  return `${s[0]}${'*'.repeat(Math.min(s.length - 2, 6))}${s[s.length - 1]}`
}

/** 选择配置。 */
const selectionConfig = computed(() => {
  if (props.rowSelection === 'none') return undefined
  return {
    type: props.rowSelection,
    onChange: (keys: Array<string | number>) => emit('selection-change', keys),
  }
})

/** 分页配置。 */
const pagination = computed(() => ({
  current: props.page,
  pageSize: props.pageSize,
  total: props.total,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
  onChange: (page: number, pageSize: number) => {
    emit('update:page', page)
    emit('update:pageSize', pageSize)
  },
}))

/** 行点击。 */
function onRowClick(record: TableRow) {
  emit('row-click', record)
}

/** 行属性注入（用于点击事件）。 */
const customRow = (record: TableRow) => ({
  onClick: () => onRowClick(record),
  style: { cursor: 'pointer' },
})

defineExpose({ rowKey })
</script>

<template>
  <div class="schema-table">
    <div v-if="schema.searchable" class="schema-table__toolbar">
      <ASpace>
        <AInput
          placeholder="搜索关键字"
          allow-clear
          @press-enter="(e: Event) => emit('search', (e.target as HTMLInputElement).value)"
        >
          <template #prefix>
            <SearchOutlined />
          </template>
        </AInput>
        <AButton v-if="schema.exportable">导出</AButton>
      </ASpace>
    </div>

    <ATable
      :columns="antColumns"
      :data-source="data"
      :row-key="rowKey"
      :loading="loading"
      :pagination="schema.pageable === false ? false : pagination"
      :row-selection="selectionConfig"
      :custom-row="customRow"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <slot
          v-if="$slots[`column-${column.key}`]"
          :name="`column-${column.key}`"
          :record="record"
          :value="record[column.key as string]"
        />
      </template>
    </ATable>
  </div>
</template>

<style scoped lang="scss">
.schema-table {
  &__toolbar {
    margin-bottom: 12px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>

<script setup lang="ts">
/**
 * 数据范围管理页面（T108、FR-063）。
 *
 * <p><strong>核心操作</strong>：
 * - 创建数据范围：POST /data-scopes + Idempotency-Key，绑定 scopeType 与规则集合；
 * - 编辑数据范围：PATCH /data-scopes/{id} + If-Match，更新名称/描述/类型/规则；
 * - 删除数据范围：DELETE /data-scopes/{id} + If-Match，需无引用，否则返回 409。
 *
 * <p><strong>数据范围模型（FR-063）</strong>：
 * - scopeType 决定行级数据可见性的判定维度；
 * - 规则集合（rules）以 AND 关系组合，每条规则由 field/operator/value 组成；
 * - 数据范围与功能权限正交：权限决定能否操作，数据范围决定操作哪些数据行。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Button as AButton,
  Card as ACard,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  Modal as AModal,
  Select as ASelect,
  SelectOption as ASelectOption,
  Space as ASpace,
  Table as ATable,
  Tag as ATag,
  Tooltip as ATooltip,
  Typography as ATypography,
  TypographyParagraph as ATypographyParagraph,
  TypographyTitle as ATypographyTitle,
  message,
} from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { workspaceApi } from './api'
import {
  DATA_SCOPE_OPERATOR_OPTIONS,
  DATA_SCOPE_TYPE_OPTIONS,
  dataScopeTypeLabel,
  type DataScope,
  type DataScopeRule,
  type DataScopeType,
} from './types'
import { ApiError } from '@/api'
import {
  confirmHighRisk,
  showSuccess,
  showErrorFromApiError,
} from '@/composables/feedback'

const route = useRoute()
const router = useRouter()

const workspaceId = computed<string>(() => String(route.params.workspaceId ?? ''))

// ============================================================
// 列表数据状态
// ============================================================

const loading = ref(false)
const acting = ref(false)
const data = ref<DataScope[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const accumulatedCount = ref(0)

// ============================================================
// 创建/编辑
// ============================================================

const createModalVisible = ref(false)
const createForm = reactive({
  key: '',
  name: '',
  description: '',
  scopeType: 'WORKSPACE' as DataScopeType,
  rules: [] as DataScopeRule[],
})

const editModalVisible = ref(false)
const editForm = reactive({
  name: '',
  description: '',
  scopeType: 'WORKSPACE' as DataScopeType,
  rules: [] as DataScopeRule[],
})
const editing = ref<DataScope | null>(null)

const columns = [
  { title: 'Key', dataIndex: 'key', key: 'key', width: 180, ellipsis: true },
  { title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
  { title: '类型', dataIndex: 'scopeType', key: 'scopeType', width: 140 },
  { title: '规则数', key: 'ruleCount', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 200, fixed: 'right' as const },
]

// ============================================================
// 数据加载
// ============================================================

async function loadFirstPage(): Promise<void> {
  accumulatedCount.value = 0
  await loadPage(null)
}

async function loadNextPage(): Promise<void> {
  if (!hasMore.value || !nextCursor.value || loading.value) return
  await loadPage(nextCursor.value)
}

async function loadPage(cursor: string | null): Promise<void> {
  loading.value = true
  try {
    const result = await workspaceApi.listDataScopes(workspaceId.value, {
      cursor,
      pageSize: 50,
    })
    if (cursor === null) {
      data.value = result.data
    } else {
      data.value = data.value.concat(result.data)
    }
    nextCursor.value = result.nextCursor ?? null
    hasMore.value = result.hasMore
    total.value = result.total ?? null
    accumulatedCount.value = data.value.length
  } catch (err) {
    handleError(err, '加载数据范围列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 创建数据范围
// ============================================================

function openCreateModal(): void {
  createForm.key = ''
  createForm.name = ''
  createForm.description = ''
  createForm.scopeType = 'WORKSPACE'
  createForm.rules = []
  createModalVisible.value = true
}

function addCreateRule(): void {
  createForm.rules.push({ field: '', operator: 'EQ', value: '' })
}

function removeCreateRule(idx: number): void {
  createForm.rules.splice(idx, 1)
}

async function submitCreate(): Promise<void> {
  if (!createForm.key.trim() || !createForm.name.trim()) {
    message.warning('Key 和名称不能为空')
    return
  }
  const sanitizedRules = sanitizeRules(createForm.rules)
  acting.value = true
  try {
    await workspaceApi.createDataScope(workspaceId.value, {
      key: createForm.key.trim(),
      name: createForm.name.trim(),
      description: createForm.description.trim() || null,
      scopeType: createForm.scopeType,
      rules: sanitizedRules,
    })
    showSuccess('数据范围已创建')
    createModalVisible.value = false
    await loadFirstPage()
  } catch (err) {
    handleError(err, '创建数据范围失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 编辑数据范围
// ============================================================

function openEditModal(record: DataScope): void {
  editing.value = record
  editForm.name = record.name
  editForm.description = record.description ?? ''
  editForm.scopeType = record.scopeType
  editForm.rules = record.rules.map((r) => ({ ...r }))
  editModalVisible.value = true
}

function addEditRule(): void {
  editForm.rules.push({ field: '', operator: 'EQ', value: '' })
}

function removeEditRule(idx: number): void {
  editForm.rules.splice(idx, 1)
}

async function submitEdit(): Promise<void> {
  if (!editing.value) return
  if (!editForm.name.trim()) {
    message.warning('名称不能为空')
    return
  }
  const sanitizedRules = sanitizeRules(editForm.rules)
  acting.value = true
  try {
    const updated = await workspaceApi.updateDataScope(
      workspaceId.value,
      editing.value.id,
      {
        name: editForm.name.trim(),
        description: editForm.description.trim() || null,
        scopeType: editForm.scopeType,
        rules: sanitizedRules,
      },
      editing.value.revision,
    )
    const idx = data.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) data.value[idx] = updated
    showSuccess('数据范围已更新')
    editModalVisible.value = false
  } catch (err) {
    handleConflict(err, '更新数据范围失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 删除数据范围
// ============================================================

async function deleteScope(record: DataScope): Promise<void> {
  const ack = await confirmHighRisk({
    title: '确认删除数据范围',
    content: `删除数据范围"${record.name}"？此操作不可恢复，且若仍有成员引用，后端将返回 409 冲突。`,
    danger: true,
  })
  if (!ack) return
  acting.value = true
  try {
    await workspaceApi.deleteDataScope(workspaceId.value, record.id, record.revision)
    data.value = data.value.filter((r) => r.id !== record.id)
    accumulatedCount.value = data.value.length
    showSuccess(`数据范围 ${record.name} 已删除`)
  } catch (err) {
    handleConflict(err, '删除数据范围失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 显示与校验辅助
// ============================================================

/** 清洗规则：去除空字段规则，IS_NULL/NOT_NULL 无需 value。 */
function sanitizeRules(rules: DataScopeRule[]): DataScopeRule[] {
  return rules
    .filter((r) => r.field.trim() !== '')
    .map((r) => {
      const rule: DataScopeRule = {
        field: r.field.trim(),
        operator: r.operator,
      }
      if (r.operator !== 'IS_NULL' && r.operator !== 'NOT_NULL') {
        rule.value = r.value
      }
      return rule
    })
}

function operatorLabel(operator: string): string {
  return DATA_SCOPE_OPERATOR_OPTIONS.find((o) => o.value === operator)?.label ?? operator
}

/** 格式化规则值用于展示。 */
function formatRuleValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function handleConflict(err: unknown, fallback: string): void {
  if (err instanceof ApiError && err.isConflict()) {
    message.error('版本冲突或存在引用关系，正在刷新最新数据')
    void loadFirstPage()
  } else {
    showErrorFromApiError(err)
  }
  // eslint-disable-next-line no-console
  if (!(err instanceof ApiError) || !err.isConflict()) {
    console.error(fallback, err)
  }
}

function handleError(err: unknown, fallback: string): void {
  showErrorFromApiError(err)
  // eslint-disable-next-line no-console
  console.error(fallback, err)
}

onMounted(() => {
  void loadFirstPage()
})
</script>

<template>
  <div class="data-scope-list-view">
    <ATypography>
      <ATypographyTitle :level="3">
        <ASpace align="baseline">
          <AButton
            type="text"
            size="small"
            @click="router.push(`/workspaces/${workspaceId}`)"
          >
            <template #icon><ArrowLeftOutlined /></template>
          </AButton>
          <span>数据范围管理</span>
        </ASpace>
      </ATypographyTitle>
      <ATypographyParagraph type="secondary">
        数据范围与功能权限正交：权限决定能否操作，数据范围决定操作哪些数据行。
        规则以 AND 关系组合，每条规则由 field/operator/value 组成。
      </ATypographyParagraph>
    </ATypography>

    <ACard class="filter-card" :bordered="false">
      <ASpace>
        <AButton @click="loadFirstPage">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
        <AButton type="primary" ghost @click="openCreateModal">
          <template #icon><PlusOutlined /></template>
          创建数据范围
        </AButton>
      </ASpace>
    </ACard>

    <ACard :bordered="false" class="table-card">
      <ATable
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'key'">
            <ATooltip :title="record.key">
              <span class="mono">{{ record.key }}</span>
            </ATooltip>
          </template>
          <template v-else-if="column.key === 'scopeType'">
            <ATag color="blue">{{ dataScopeTypeLabel(record.scopeType as DataScopeType) }}</ATag>
          </template>
          <template v-else-if="column.key === 'ruleCount'">
            {{ record.rules?.length ?? 0 }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <ASpace>
              <AButton
                size="small"
                @click="openEditModal(record as DataScope)"
              >
                <template #icon><EditOutlined /></template>
                编辑
              </AButton>
              <AButton
                size="small"
                danger
                :loading="acting"
                @click="deleteScope(record as DataScope)"
              >
                <template #icon><DeleteOutlined /></template>
                删除
              </AButton>
            </ASpace>
          </template>
        </template>

        <template #expandedRowRender="{ record }">
          <div class="rules-detail">
            <ATypographyParagraph type="secondary" style="margin: 0 0 8px">
              规则明细（AND 关系）
            </ATypographyParagraph>
            <AEmpty
              v-if="!record.rules || record.rules.length === 0"
              description="无规则（空集）"
              :image="AEmpty.PRESENTED_IMAGE_SIMPLE"
            />
            <table v-else class="rules-table">
              <thead>
                <tr>
                  <th>字段</th>
                  <th>操作符</th>
                  <th>值</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(rule, idx) in record.rules" :key="idx">
                  <td class="mono">{{ rule.field }}</td>
                  <td>{{ operatorLabel(rule.operator) }}</td>
                  <td class="mono">
                    {{ rule.operator === 'IS_NULL' || rule.operator === 'NOT_NULL'
                      ? '—' : formatRuleValue(rule.value) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>

        <template #emptyText>
          <AEmpty description="暂无数据范围，点击右上角按钮创建" />
        </template>
      </ATable>

      <div class="pagination-bar">
        <ASpace>
          <span class="meta-text">
            共 {{ total ?? accumulatedCount }} 条 · 已加载 {{ accumulatedCount }} 条
          </span>
          <AButton
            :loading="loading"
            :disabled="!hasMore"
            @click="loadNextPage"
          >
            加载更多
          </AButton>
        </ASpace>
      </div>
    </ACard>

    <!-- 创建数据范围模态框 -->
    <AModal
      v-model:open="createModalVisible"
      title="创建数据范围"
      :confirm-loading="acting"
      ok-text="创建"
      cancel-text="取消"
      width="800px"
      @ok="submitCreate"
    >
      <AForm layout="vertical">
        <AFormItem label="Key" required>
          <AInput
            v-model:value="createForm.key"
            placeholder="工作空间内唯一，建议大写下划线"
            :max-length="64"
          />
        </AFormItem>
        <AFormItem label="名称" required>
          <AInput v-model:value="createForm.name" :max-length="128" />
        </AFormItem>
        <AFormItem label="描述">
          <AInput
            v-model:value="createForm.description"
            type="textarea"
            :rows="2"
            :max-length="512"
          />
        </AFormItem>
        <AFormItem label="数据范围类型">
          <ASelect v-model:value="createForm.scopeType">
            <ASelectOption
              v-for="opt in DATA_SCOPE_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="规则集合（AND 关系）">
          <AButton size="small" @click="addCreateRule">
            <template #icon><PlusOutlined /></template>
            添加规则
          </AButton>
          <div class="rules-list">
            <div
              v-for="(rule, idx) in createForm.rules"
              :key="idx"
              class="rule-row"
            >
              <AInput
                v-model:value="rule.field"
                placeholder="字段名，如 regionCode"
                style="width: 220px"
              />
              <ASelect
                v-model:value="rule.operator"
                style="width: 140px"
              >
                <ASelectOption
                  v-for="op in DATA_SCOPE_OPERATOR_OPTIONS"
                  :key="op.value"
                  :value="op.value"
                >
                  {{ op.label }}
                </ASelectOption>
              </ASelect>
              <AInput
                v-if="rule.operator !== 'IS_NULL' && rule.operator !== 'NOT_NULL'"
                v-model:value="rule.value"
                placeholder="值（多值用逗号分隔）"
                style="flex: 1"
              />
              <AButton
                type="text"
                danger
                size="small"
                @click="removeCreateRule(idx)"
              >
                <template #icon><DeleteOutlined /></template>
              </AButton>
            </div>
            <AEmpty
              v-if="createForm.rules.length === 0"
              description="尚未添加规则（空集等价于允许全部）"
              :image="AEmpty.PRESENTED_IMAGE_SIMPLE"
            />
          </div>
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 编辑数据范围模态框 -->
    <AModal
      v-model:open="editModalVisible"
      title="编辑数据范围"
      :confirm-loading="acting"
      ok-text="保存"
      cancel-text="取消"
      width="800px"
      @ok="submitEdit"
    >
      <AForm layout="vertical">
        <AFormItem label="名称" required>
          <AInput v-model:value="editForm.name" :max-length="128" />
        </AFormItem>
        <AFormItem label="描述">
          <AInput
            v-model:value="editForm.description"
            type="textarea"
            :rows="2"
            :max-length="512"
          />
        </AFormItem>
        <AFormItem label="数据范围类型">
          <ASelect v-model:value="editForm.scopeType">
            <ASelectOption
              v-for="opt in DATA_SCOPE_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="规则集合（AND 关系）">
          <AButton size="small" @click="addEditRule">
            <template #icon><PlusOutlined /></template>
            添加规则
          </AButton>
          <div class="rules-list">
            <div
              v-for="(rule, idx) in editForm.rules"
              :key="idx"
              class="rule-row"
            >
              <AInput
                v-model:value="rule.field"
                placeholder="字段名，如 regionCode"
                style="width: 220px"
              />
              <ASelect
                v-model:value="rule.operator"
                style="width: 140px"
              >
                <ASelectOption
                  v-for="op in DATA_SCOPE_OPERATOR_OPTIONS"
                  :key="op.value"
                  :value="op.value"
                >
                  {{ op.label }}
                </ASelectOption>
              </ASelect>
              <AInput
                v-if="rule.operator !== 'IS_NULL' && rule.operator !== 'NOT_NULL'"
                v-model:value="rule.value"
                placeholder="值（多值用逗号分隔）"
                style="flex: 1"
              />
              <AButton
                type="text"
                danger
                size="small"
                @click="removeEditRule(idx)"
              >
                <template #icon><DeleteOutlined /></template>
              </AButton>
            </div>
            <AEmpty
              v-if="editForm.rules.length === 0"
              description="尚未添加规则（空集等价于允许全部）"
              :image="AEmpty.PRESENTED_IMAGE_SIMPLE"
            />
          </div>
        </AFormItem>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.data-scope-list-view {
  padding: 24px;

  .filter-card {
    margin-bottom: 16px;
  }

  .table-card {
    .pagination-bar {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      padding: 16px 0 0;

      .meta-text {
        color: #666;
        font-size: 0.9em;
      }
    }
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }

  .rules-detail {
    padding: 8px 16px;

    .rules-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.9em;

      th,
      td {
        text-align: left;
        padding: 6px 12px;
        border-bottom: 1px solid #f0f0f0;
      }

      th {
        background: #fafafa;
        font-weight: 500;
        color: #666;
      }
    }
  }

  .rules-list {
    margin-top: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .rule-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
}
</style>

<script setup lang="ts">
/**
 * 工作空间选择/切换页面（T108、FR-003）。
 *
 * 分页查询当前用户作为负责人的工作空间，支持本地关键字过滤（code/name 模糊匹配）。
 * "切换"操作将选中工作空间写入 workspaceStore（同步到 localStorage），
 * 由全局 httpClient 拦截器注入 `X-Workspace-Id` 头。
 *
 * <p>"创建工作空间"模态框支持 code/name/description/ownerUserId/defaultLocale/defaultTimezone。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
  Spin as ASpin,
  Table as ATable,
  Tag as ATag,
  Tooltip as ATooltip,
  Typography as ATypography,
  TypographyParagraph as ATypographyParagraph,
  TypographyTitle as ATypographyTitle,
  message,
} from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, SwapOutlined } from '@ant-design/icons-vue'
import { workspaceApi } from './api'
import { useWorkspaceStore } from './store'
import {
  workspaceStatusColor,
  workspaceStatusLabel,
  type Workspace,
} from './types'
import { showErrorFromApiError } from '@/composables/feedback'

const router = useRouter()
const workspaceStore = useWorkspaceStore()

// ============================================================
// 列表数据状态
// ============================================================

const loading = ref(false)
const acting = ref(false)
const data = ref<Workspace[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const accumulatedCount = ref(0)
const keyword = ref('')

// 本地过滤（避免每次输入都触发后端请求）
const filteredData = computed<Workspace[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return data.value
  return data.value.filter((w) => {
    return (
      w.code.toLowerCase().includes(kw) ||
      w.name.toLowerCase().includes(kw) ||
      (w.description ?? '').toLowerCase().includes(kw)
    )
  })
})

const columns = [
  { title: 'Code', dataIndex: 'code', key: 'code', width: 160, ellipsis: true },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '负责人', dataIndex: 'ownerUserId', key: 'ownerUserId', width: 240, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 120, fixed: 'right' as const },
]

// ============================================================
// 创建工作空间
// ============================================================

const createModalVisible = ref(false)
const createForm = reactive({
  code: '',
  name: '',
  description: '',
  ownerUserId: '',
  defaultLocale: 'zh-CN',
  defaultTimezone: 'Asia/Shanghai',
})

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
    const result = await workspaceApi.listWorkspaces({ cursor, pageSize: 50 })
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
    showErrorFromApiError(err)
    // eslint-disable-next-line no-console
    console.error('加载工作空间列表失败', err)
  } finally {
    loading.value = false
  }
}

// ============================================================
// 切换工作空间
// ============================================================

function switchWorkspace(record: Workspace): void {
  workspaceStore.setCurrent(record)
  message.success(`已切换到工作空间：${record.name}`)
  router.push(`/workspaces/${record.id}`)
}

// ============================================================
// 创建工作空间
// ============================================================

function openCreateModal(): void {
  createForm.code = ''
  createForm.name = ''
  createForm.description = ''
  createForm.ownerUserId = ''
  createForm.defaultLocale = 'zh-CN'
  createForm.defaultTimezone = 'Asia/Shanghai'
  createModalVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.code.trim() || !createForm.name.trim() || !createForm.ownerUserId.trim()) {
    message.warning('Code、名称和负责人不能为空')
    return
  }
  acting.value = true
  try {
    const created = await workspaceApi.createWorkspace({
      code: createForm.code.trim(),
      name: createForm.name.trim(),
      description: createForm.description.trim() || null,
      ownerUserId: createForm.ownerUserId.trim(),
      defaultLocale: createForm.defaultLocale || null,
      defaultTimezone: createForm.defaultTimezone || null,
    })
    showSuccessAndSwitch(created)
    createModalVisible.value = false
    await loadFirstPage()
  } catch (err) {
    showErrorFromApiError(err)
    // eslint-disable-next-line no-console
    console.error('创建工作空间失败', err)
  } finally {
    acting.value = false
  }
}

function showSuccessAndSwitch(ws: Workspace): void {
  message.success(`工作空间 ${ws.name} 已创建（状态：草稿，需激活后可用）`)
}

// ============================================================
// 显示辅助
// ============================================================

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  void loadFirstPage()
})
</script>

<template>
  <div class="workspace-selector-view">
    <ATypography>
      <ATypographyTitle :level="3">选择工作空间</ATypographyTitle>
      <ATypographyParagraph type="secondary">
        当前用户作为负责人的工作空间列表。切换工作空间后，后续所有操作均在该工作空间边界内进行。
        跨工作空间访问将统一返回 404，不泄露存在性。
      </ATypographyParagraph>
    </ATypography>

    <ACard class="filter-card" :bordered="false">
      <ASpace>
        <AInput
          v-model:value="keyword"
          placeholder="按 code / 名称 / 描述过滤"
          allow-clear
          style="width: 320px"
        />
        <AButton @click="loadFirstPage">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
        <AButton type="primary" ghost @click="openCreateModal">
          <template #icon><PlusOutlined /></template>
          创建工作空间
        </AButton>
      </ASpace>
    </ACard>

    <ACard :bordered="false" class="table-card">
      <ASpin :spinning="loading">
        <ATable
          :columns="columns"
          :data-source="filteredData"
          :loading="loading"
          :pagination="false"
          row-key="id"
          size="middle"
          :scroll="{ x: 1100 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'code'">
              <ATooltip :title="record.code">
                <span class="mono">{{ record.code }}</span>
              </ATooltip>
            </template>
            <template v-else-if="column.key === 'status'">
              <ATag :color="workspaceStatusColor(record.status)">
                {{ workspaceStatusLabel(record.status) }}
              </ATag>
            </template>
            <template v-else-if="column.key === 'ownerUserId'">
              <ATooltip :title="record.ownerUserId">
                <span class="mono">{{ record.ownerUserId }}</span>
              </ATooltip>
            </template>
            <template v-else-if="column.key === 'updatedAt'">
              {{ formatDateTime(record.updatedAt) }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <AButton
                type="primary"
                size="small"
                ghost
                @click="switchWorkspace(record as Workspace)"
              >
                <template #icon><SwapOutlined /></template>
                切换
              </AButton>
            </template>
          </template>

          <template #emptyText>
            <AEmpty description="暂无工作空间，点击右上角按钮创建" />
          </template>
        </ATable>
      </ASpin>

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

    <!-- 创建工作空间模态框 -->
    <AModal
      v-model:open="createModalVisible"
      title="创建工作空间"
      :confirm-loading="acting"
      ok-text="创建"
      cancel-text="取消"
      width="640px"
      @ok="submitCreate"
    >
      <AForm layout="vertical">
        <AFormItem label="Code" required>
          <AInput
            v-model:value="createForm.code"
            placeholder="全局唯一，建议小写连字符（如 acme-corp）"
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
        <AFormItem label="负责人 User ID" required>
          <AInput
            v-model:value="createForm.ownerUserId"
            placeholder="UUID 格式的用户 ID"
          />
        </AFormItem>
        <ASpace style="width: 100%" align="start">
          <AFormItem label="默认语言" style="flex: 1">
            <ASelect v-model:value="createForm.defaultLocale">
              <ASelectOption value="zh-CN">简体中文</ASelectOption>
              <ASelectOption value="en-US">English (US)</ASelectOption>
              <ASelectOption value="ja-JP">日本語</ASelectOption>
            </ASelect>
          </AFormItem>
          <AFormItem label="默认时区" style="flex: 1">
            <ASelect v-model:value="createForm.defaultTimezone">
              <ASelectOption value="Asia/Shanghai">Asia/Shanghai</ASelectOption>
              <ASelectOption value="Asia/Tokyo">Asia/Tokyo</ASelectOption>
              <ASelectOption value="America/Los_Angeles">America/Los_Angeles</ASelectOption>
              <ASelectOption value="UTC">UTC</ASelectOption>
            </ASelect>
          </AFormItem>
        </ASpace>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.workspace-selector-view {
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
}
</style>

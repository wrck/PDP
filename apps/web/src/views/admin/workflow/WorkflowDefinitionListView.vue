<script setup lang="ts">
/**
 * 平台流程定义列表页面（T090、FR-174）。
 *
 * 提供流程定义的分页查询、过滤、部署入口与状态迁移操作。
 * 对接 `GET /workflow-definitions`、`POST /workflow-definitions/{id}/transitions`。
 *
 * 部署通过 {@link WorkflowDefinitionDeployDrawer} 完成，
 * 状态迁移通过 {@link WorkflowTransitionModal} 完成。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Button,
  Card,
  Form,
  Input,
  Select,
  SelectOption,
  Space,
  SpaceItem,
  Table,
  Tag,
  Tooltip,
  Typography,
  TypographyParagraph,
  TypographyTitle,
  message,
} from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import WorkflowDefinitionDeployDrawer from './WorkflowDefinitionDeployDrawer.vue'
import WorkflowTransitionModal from './WorkflowTransitionModal.vue'
import {
  definitionStatusColor,
  definitionStatusLabel,
  type WorkflowDefinitionStatus,
  type WorkflowDefinitionSummary,
} from './types'

const router = useRouter()

// ============================================================
// 列表数据状态
// ============================================================

const loading = ref(false)
const data = ref<WorkflowDefinitionSummary[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const currentPage = ref(1)
const accumulatedCount = ref(0)

// 过滤条件
const filters = reactive({
  keyPrefix: '',
  status: undefined as WorkflowDefinitionStatus | undefined,
})

// 抽屉/模态框状态
const deployDrawerVisible = ref(false)
const transitionTarget = ref<WorkflowDefinitionSummary | null>(null)
const transitionVisible = ref(false)

const columns = [
  {
    title: '流程键',
    dataIndex: 'processDefinitionKey',
    key: 'processDefinitionKey',
    ellipsis: true,
  },
  {
    title: '业务版本',
    dataIndex: 'businessVersion',
    key: 'businessVersion',
    width: 140,
  },
  {
    title: '内容哈希',
    dataIndex: 'contentHash',
    key: 'contentHash',
    width: 180,
    ellipsis: true,
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 110,
  },
  {
    title: '部署时间',
    dataIndex: 'deployedAt',
    key: 'deployedAt',
    width: 180,
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right' as const,
  },
]

/** 加载首页数据。 */
async function loadFirstPage(): Promise<void> {
  currentPage.value = 1
  accumulatedCount.value = 0
  await loadPage(null)
}

/** 加载下一页。 */
async function loadNextPage(): Promise<void> {
  if (!hasMore.value || !nextCursor.value || loading.value) return
  await loadPage(nextCursor.value)
  currentPage.value++
}

/** 调用 API 加载一页数据。 */
async function loadPage(cursor: string | null): Promise<void> {
  loading.value = true
  try {
    const result = await workflowApi.listDefinitions({
      keyPrefix: filters.keyPrefix.trim() || undefined,
      status: filters.status,
      cursor,
      size: 20,
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
    handleError(err, '加载流程定义失败')
  } finally {
    loading.value = false
  }
}

/** 应用过滤条件。 */
function applyFilters(): void {
  loadFirstPage()
}

/** 重置过滤条件。 */
function resetFilters(): void {
  filters.keyPrefix = ''
  filters.status = undefined
  loadFirstPage()
}

/** 打开部署抽屉。 */
function openDeployDrawer(): void {
  deployDrawerVisible.value = true
}

/** 部署成功回调。 */
function onDeployed(summary: WorkflowDefinitionSummary): void {
  deployDrawerVisible.value = false
  message.success(`流程定义 ${summary.processDefinitionKey}@${summary.businessVersion} 部署成功`)
  loadFirstPage()
}

/** 打开状态迁移模态框。 */
function openTransitionModal(record: WorkflowDefinitionSummary): void {
  transitionTarget.value = record
  transitionVisible.value = true
}

/** 状态迁移成功回调。 */
function onTransitioned(summary: WorkflowDefinitionSummary): void {
  transitionVisible.value = false
  transitionTarget.value = null
  message.success(`流程定义已迁移至 ${definitionStatusLabel(summary.status)}`)
  loadFirstPage()
}

/** 跳转到实例诊断页面（按定义 ID 过滤）。 */
function goToInstances(_record: WorkflowDefinitionSummary): void {
  // 当前 P1 实例列表按工作空间边界查询 incident 实例，不支持按定义 ID 过滤
  // 直接跳转到实例列表，操作者可在列表页手动检索
  router.push('/admin/workflow/instances')
}

/** 格式化日期时间。 */
function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

/** 统一错误处理。 */
function handleError(err: unknown, fallback: string): void {
  if (err instanceof ApiError) {
    message.error(err.message)
  } else {
    message.error(fallback)
  }
  // eslint-disable-next-line no-console
  console.error(err)
}

onMounted(() => {
  loadFirstPage()
})
</script>

<template>
  <div class="workflow-definition-list-view">
    <Typography>
      <TypographyTitle :level="3">平台流程定义</TypographyTitle>
      <TypographyParagraph type="secondary">
        管理 BPMN 2.0.2 流程定义的校验、部署、状态迁移（DEPLOYED → DEPRECATED → RETIRED）。
        所有定义归属于当前工作空间，跨工作空间访问将被拒绝。
      </TypographyParagraph>
    </Typography>

    <Card class="filter-card" :bordered="false">
      <Form layout="inline" @submit.prevent="applyFilters">
        <SpaceItem>
          <Input
            v-model:value="filters.keyPrefix"
            placeholder="按流程键前缀过滤"
            allow-clear
            style="width: 240px"
            @press-enter="applyFilters"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </Input>
        </SpaceItem>
        <SpaceItem>
          <Select
            v-model:value="filters.status"
            placeholder="按状态过滤"
            allow-clear
            style="width: 160px"
          >
            <SelectOption value="VALIDATED">已校验</SelectOption>
            <SelectOption value="DEPLOYED">已部署</SelectOption>
            <SelectOption value="DEPRECATED">已弃用</SelectOption>
            <SelectOption value="RETIRED">已退役</SelectOption>
          </Select>
        </SpaceItem>
        <SpaceItem>
          <Space>
            <Button type="primary" @click="applyFilters">
              <template #icon><SearchOutlined /></template>
              查询
            </Button>
            <Button @click="resetFilters">重置</Button>
            <Button @click="loadFirstPage">
              <template #icon><ReloadOutlined /></template>
              刷新
            </Button>
            <Button type="primary" ghost @click="openDeployDrawer">
              <template #icon><PlusOutlined /></template>
              部署流程定义
            </Button>
          </Space>
        </SpaceItem>
      </Form>
    </Card>

    <Card :bordered="false" class="table-card">
      <Table
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'processDefinitionKey'">
            <Tooltip :title="record.processDefinitionKey">
              <span class="mono">{{ record.processDefinitionKey }}</span>
            </Tooltip>
          </template>
          <template v-else-if="column.key === 'contentHash'">
            <Tooltip :title="record.contentHash">
              <span class="mono hash-cell">{{ record.contentHash }}</span>
            </Tooltip>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="definitionStatusColor(record.status as WorkflowDefinitionStatus)">
              {{ definitionStatusLabel(record.status as WorkflowDefinitionStatus) }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'deployedAt'">
            {{ formatDateTime(record.deployedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <Space>
              <Button
                size="small"
                :disabled="record.status === 'RETIRED'"
                @click="openTransitionModal(record)"
              >
                状态迁移
              </Button>
              <Button size="small" type="link" @click="goToInstances(record)">
                查看实例
              </Button>
            </Space>
          </template>
        </template>

        <template #emptyText>
          <TypographyParagraph type="secondary">
            暂无流程定义，点击右上角"部署流程定义"开始
          </TypographyParagraph>
        </template>
      </Table>

      <div class="pagination-bar">
        <Space>
          <span class="meta-text">
            共 {{ total ?? accumulatedCount }} 条 · 已加载 {{ accumulatedCount }} 条
          </span>
          <Button
            :loading="loading"
            :disabled="!hasMore"
            @click="loadNextPage"
          >
            加载更多
          </Button>
        </Space>
      </div>
    </Card>

    <!-- 部署流程定义抽屉 -->
    <WorkflowDefinitionDeployDrawer
      v-model:visible="deployDrawerVisible"
      @deployed="onDeployed"
    />

    <!-- 状态迁移模态框 -->
    <WorkflowTransitionModal
      v-model:visible="transitionVisible"
      :definition="transitionTarget"
      @transitioned="onTransitioned"
    />
  </div>
</template>

<style scoped lang="scss">
.workflow-definition-list-view {
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
    font-size: 0.92em;
  }

  .hash-cell {
    color: #888;
  }
}
</style>

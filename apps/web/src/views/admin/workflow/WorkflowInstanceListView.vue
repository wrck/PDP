<script setup lang="ts">
/**
 * 流程实例列表页面（T090、FR-174、FR-175）。
 *
 * 运维监控端点：分页查询工作空间内有未解决 incident 的实例。
 * 对接 `GET /workflow-instances`。
 *
 * 点击实例行跳转到实例诊断详情页面。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Button,
  Card,
  Empty,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  TypographyParagraph,
  TypographyTitle,
  message,
} from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import {
  instanceStatusColor,
  instanceStatusLabel,
  type WorkflowInstanceSummary,
} from './types'

const router = useRouter()

const loading = ref(false)
const data = ref<WorkflowInstanceSummary[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const accumulatedCount = ref(0)

const columns = [
  {
    title: '实例 ID',
    dataIndex: 'id',
    key: 'id',
    width: 320,
    ellipsis: true,
  },
  {
    title: '定义 ID',
    dataIndex: 'definitionId',
    key: 'definitionId',
    width: 320,
    ellipsis: true,
  },
  {
    title: '业务对象',
    dataIndex: 'businessObjectRef',
    key: 'businessObjectRef',
    ellipsis: true,
  },
  {
    title: '状态',
    dataIndex: 'state',
    key: 'state',
    width: 110,
  },
  {
    title: '当前节点',
    dataIndex: 'currentActivityKeys',
    key: 'currentActivityKeys',
    width: 200,
  },
  {
    title: '未解决 Incident',
    dataIndex: 'incidentCount',
    key: 'incidentCount',
    width: 150,
    sorter: (a: WorkflowInstanceSummary, b: WorkflowInstanceSummary) =>
      a.incidentCount - b.incidentCount,
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    fixed: 'right' as const,
  },
]

/** 加载首页。 */
async function loadFirstPage(): Promise<void> {
  accumulatedCount.value = 0
  await loadPage(null)
}

/** 加载下一页。 */
async function loadNextPage(): Promise<void> {
  if (!hasMore.value || !nextCursor.value || loading.value) return
  await loadPage(nextCursor.value)
}

/** 调用 API。 */
async function loadPage(cursor: string | null): Promise<void> {
  loading.value = true
  try {
    const result = await workflowApi.listInstancesWithIncidents({
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
    handleError(err, '加载流程实例失败')
  } finally {
    loading.value = false
  }
}

/** 跳转到实例详情页。 */
function goToDetail(record: WorkflowInstanceSummary): void {
  router.push(`/admin/workflow/instances/${record.id}`)
}

/** 格式化业务对象引用（截断 JSON）。 */
function formatBusinessObject(ref: Record<string, unknown>): string {
  if (!ref || Object.keys(ref).length === 0) return '—'
  try {
    const json = JSON.stringify(ref)
    return json.length > 80 ? `${json.slice(0, 80)}...` : json
  } catch {
    return '—'
  }
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
  <div class="workflow-instance-list-view">
    <Typography>
      <TypographyTitle :level="3">流程实例诊断</TypographyTitle>
      <TypographyParagraph type="secondary">
        运维监控端点：分页查询当前工作空间内有未解决 incident 的流程实例。
        点击行查看实例诊断详情、incident 列表与迁移历史，并可执行受控管理动作。
      </TypographyParagraph>
    </Typography>

    <Card :bordered="false">
      <template #title>
        <Space>
          <span>实例列表</span>
          <Button size="small" @click="loadFirstPage">
            <template #icon><ReloadOutlined /></template>
            刷新
          </Button>
        </Space>
      </template>

      <Table
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 1300 }"
        :custom-row="(record: WorkflowInstanceSummary) => ({
          onClick: () => goToDetail(record),
          style: { cursor: 'pointer' },
        })"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'id'">
            <Tooltip :title="record.id">
              <span class="mono">{{ record.id }}</span>
            </Tooltip>
          </template>
          <template v-else-if="column.key === 'definitionId'">
            <Tooltip :title="record.definitionId">
              <span class="mono muted">{{ record.definitionId }}</span>
            </Tooltip>
          </template>
          <template v-else-if="column.key === 'businessObjectRef'">
            <Tooltip :title="formatBusinessObject(record.businessObjectRef)">
              <span class="mono">{{ formatBusinessObject(record.businessObjectRef) }}</span>
            </Tooltip>
          </template>
          <template v-else-if="column.key === 'state'">
            <Tag :color="instanceStatusColor(record.state)">
              {{ instanceStatusLabel(record.state) }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'currentActivityKeys'">
            <Space v-if="record.currentActivityKeys && record.currentActivityKeys.length > 0" wrap>
              <Tag v-for="key in record.currentActivityKeys" :key="key" color="blue">
                {{ key }}
              </Tag>
            </Space>
            <span v-else class="muted">—</span>
          </template>
          <template v-else-if="column.key === 'incidentCount'">
            <Tag :color="record.incidentCount > 0 ? 'red' : 'default'">
              {{ record.incidentCount }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <Button size="small" type="link" @click.stop="goToDetail(record)">
              查看详情
            </Button>
          </template>
        </template>

        <template #emptyText>
          <Empty description="当前工作空间无未解决 incident 的实例" />
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
  </div>
</template>

<style scoped lang="scss">
.workflow-instance-list-view {
  padding: 24px;

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

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }

  .muted {
    color: #888;
  }
}
</style>

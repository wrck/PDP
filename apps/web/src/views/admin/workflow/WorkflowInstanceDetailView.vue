<script setup lang="ts">
/**
 * 流程实例诊断详情页面（T090、FR-174、FR-175、FR-176）。
 *
 * 三栏 Tab 设计：
 * <ol>
 *   <li>诊断摘要：实例基本信息 + 状态 + 当前节点；</li>
 *   <li>Incident/dead-letter：incident 列表，支持包含已解决；</li>
 *   <li>迁移历史：审计回查迁移记录。</li>
 * </ol>
 *
 * 支持受控管理动作（PAUSE/RESUME/RETRY/MIGRATE/TERMINATE/MANUAL_COMPENSATE）：
 * <ul>
 *   <li>MIGRATE 先通过 {@link MigrationPreviewDrawer} 生成迁移计划；</li>
 *   <li>所有动作通过 {@link ApplyActionModal} 执行，高风险动作 MUST 携带确认记录。</li>
 * </ul>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Empty,
  Space,
  Spin,
  Table,
  Tabs,
  TabPane,
  Tag,
  Tooltip,
  TypographyParagraph,
  TypographyTitle,
  message,
} from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  StopOutlined,
  ThunderboltOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import ApplyActionModal from './ApplyActionModal.vue'
import MigrationPreviewDrawer from './MigrationPreviewDrawer.vue'
import {
  actionLabel,
  incidentStatusLabel,
  instanceStatusColor,
  instanceStatusLabel,
  isTerminalInstanceState,
  type MigrationPlan,
  type WorkflowAdminActionType,
  type WorkflowIncident,
  type WorkflowInstanceSummary,
  type MigrationRecord,
} from './types'

const route = useRoute()
const router = useRouter()

const instanceId = computed(() => String(route.params.instanceId ?? ''))

// ============================================================
// 数据状态
// ============================================================

const loadingInstance = ref(false)
const instance = ref<WorkflowInstanceSummary | null>(null)

const activeTab = ref<'overview' | 'incidents' | 'history'>('overview')

// Incidents
const loadingIncidents = ref(false)
const incidents = ref<WorkflowIncident[]>([])
const includeResolved = ref(false)

// Migration history
const loadingHistory = ref(false)
const migrationHistory = ref<MigrationRecord[]>([])

// 模态框/抽屉状态
const actionTarget = ref<WorkflowAdminActionType | null>(null)
const actionModalVisible = ref(false)
const migrationPreviewVisible = ref(false)
const lastMigrationPlan = ref<MigrationPlan | null>(null)

const columns = [
  {
    title: 'Incident ID',
    dataIndex: 'incidentId',
    key: 'incidentId',
    width: 320,
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'incidentType',
    key: 'incidentType',
    width: 180,
  },
  {
    title: '活动节点',
    dataIndex: 'activityKey',
    key: 'activityKey',
    width: 200,
  },
  {
    title: '重试次数',
    dataIndex: 'retryCount',
    key: 'retryCount',
    width: 100,
  },
  {
    title: '发生时间',
    dataIndex: 'occurredAt',
    key: 'occurredAt',
    width: 180,
  },
  {
    title: '解决时间',
    dataIndex: 'resolvedAt',
    key: 'resolvedAt',
    width: 180,
  },
  {
    title: '错误消息',
    dataIndex: 'errorMessage',
    key: 'errorMessage',
    ellipsis: true,
  },
]

const migrationColumns = [
  {
    title: '迁移 ID',
    dataIndex: 'migrationId',
    key: 'migrationId',
    width: 320,
    ellipsis: true,
  },
  {
    title: '源定义',
    dataIndex: 'sourceDefinitionId',
    key: 'sourceDefinitionId',
    width: 320,
    ellipsis: true,
  },
  {
    title: '目标定义',
    dataIndex: 'targetDefinitionId',
    key: 'targetDefinitionId',
    width: 320,
    ellipsis: true,
  },
  {
    title: '执行者',
    dataIndex: 'triggeredBy',
    key: 'triggeredBy',
    width: 180,
  },
  {
    title: '迁移时间',
    dataIndex: 'migratedAt',
    key: 'migratedAt',
    width: 180,
  },
  {
    title: '批大小',
    dataIndex: 'batchSize',
    key: 'batchSize',
    width: 100,
  },
  {
    title: '结果',
    dataIndex: 'successful',
    key: 'successful',
    width: 100,
  },
  {
    title: '失败原因',
    dataIndex: 'failureReason',
    key: 'failureReason',
    ellipsis: true,
  },
]

const isTerminal = computed(
  () => instance.value !== null && isTerminalInstanceState(instance.value.state),
)

const canPause = computed(() => instance.value?.state === 'ACTIVE' || instance.value?.state === 'INCIDENT')
const canResume = computed(() => instance.value?.state === 'SUSPENDED')
const canRetry = computed(() => instance.value?.state === 'INCIDENT')
const canMigrate = computed(() => !isTerminal.value && instance.value !== null)
const canTerminate = computed(() => !isTerminal.value && instance.value !== null)
const canManualCompensate = computed(() => instance.value?.state === 'INCIDENT')

// ============================================================
// 数据加载
// ============================================================

async function loadInstance(): Promise<void> {
  if (!instanceId.value) return
  loadingInstance.value = true
  try {
    instance.value = await workflowApi.getInstance(instanceId.value)
  } catch (err) {
    handleError(err, '加载实例失败')
    instance.value = null
  } finally {
    loadingInstance.value = false
  }
}

async function loadIncidents(): Promise<void> {
  if (!instanceId.value) return
  loadingIncidents.value = true
  try {
    incidents.value = await workflowApi.listIncidents(
      instanceId.value,
      includeResolved.value,
    )
  } catch (err) {
    handleError(err, '加载 incident 失败')
    incidents.value = []
  } finally {
    loadingIncidents.value = false
  }
}

async function loadMigrationHistory(): Promise<void> {
  if (!instanceId.value) return
  loadingHistory.value = true
  try {
    migrationHistory.value = await workflowApi.listMigrationHistory(instanceId.value)
  } catch (err) {
    handleError(err, '加载迁移历史失败')
    migrationHistory.value = []
  } finally {
    loadingHistory.value = false
  }
}

/** Tab 切换时按需加载数据。 */
function handleTabChange(key: string): void {
  if (key === 'incidents' && incidents.value.length === 0) {
    loadIncidents()
  } else if (key === 'history' && migrationHistory.value.length === 0) {
    loadMigrationHistory()
  }
}

/** 切换是否包含已解决 incident。 */
function toggleIncludeResolved(checked: boolean): void {
  includeResolved.value = checked
  loadIncidents()
}

/** 刷新全部数据。 */
async function refreshAll(): Promise<void> {
  await Promise.all([loadInstance(), loadIncidents(), loadMigrationHistory()])
}

// ============================================================
// 受控管理动作
// ============================================================

/** 打开动作模态框。 */
function openActionModal(action: WorkflowAdminActionType): void {
  actionTarget.value = action
  actionModalVisible.value = true
}

/** 打开迁移预览抽屉（MIGRATE 专用）。 */
function openMigrationPreview(): void {
  migrationPreviewVisible.value = true
}

/** 迁移预览生成完成回调。携带计划进入 ApplyActionModal。 */
function onMigrationPlanReady(plan: MigrationPlan): void {
  lastMigrationPlan.value = plan
  migrationPreviewVisible.value = false
  actionTarget.value = 'MIGRATE'
  actionModalVisible.value = true
}

/** 动作执行完成回调（后端返回 202 + 受理后实例摘要）。 */
function onActionApplied(summary: WorkflowInstanceSummary): void {
  actionModalVisible.value = false
  actionTarget.value = null
  // MIGRATE 完成后清除缓存的迁移计划
  if (lastMigrationPlan.value) {
    lastMigrationPlan.value = null
  }
  message.success(`管理动作已受理，实例当前状态：${instanceStatusLabel(summary.state)}`)
  refreshAll()
}

/** 返回列表。 */
function goBack(): void {
  router.push('/admin/workflow/instances')
}

/** 格式化业务对象引用。 */
function formatBusinessObject(ref: Record<string, unknown>): string {
  if (!ref || Object.keys(ref).length === 0) return '—'
  try {
    return JSON.stringify(ref, null, 2)
  } catch {
    return '—'
  }
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

watch(instanceId, () => {
  if (instanceId.value) {
    loadInstance()
  }
})

onMounted(() => {
  loadInstance()
})
</script>

<template>
  <div class="workflow-instance-detail-view">
    <div class="page-header">
      <Space>
        <Button @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
          返回列表
        </Button>
        <TypographyTitle :level="3" class="page-title">
          流程实例诊断
        </TypographyTitle>
      </Space>
      <Button @click="refreshAll">
        <template #icon><ReloadOutlined /></template>
        刷新
      </Button>
    </div>

    <Spin :spinning="loadingInstance">
      <Card v-if="instance" :bordered="false" class="instance-summary-card">
        <Descriptions :column="3" bordered size="small">
          <DescriptionsItem label="实例 ID">
            <span class="mono">{{ instance.id }}</span>
          </DescriptionsItem>
          <DescriptionsItem label="定义 ID">
            <span class="mono">{{ instance.definitionId }}</span>
          </DescriptionsItem>
          <DescriptionsItem label="状态">
            <Tag :color="instanceStatusColor(instance.state)">
              {{ instanceStatusLabel(instance.state) }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="当前节点" :span="2">
            <Space v-if="instance.currentActivityKeys && instance.currentActivityKeys.length > 0" wrap>
              <Tag v-for="key in instance.currentActivityKeys" :key="key" color="blue">
                {{ key }}
              </Tag>
            </Space>
            <span v-else class="muted">—</span>
          </DescriptionsItem>
          <DescriptionsItem label="未解决 Incident">
            <Tag :color="instance.incidentCount > 0 ? 'red' : 'default'">
              {{ instance.incidentCount }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="业务对象" :span="3">
            <pre class="business-object">{{ formatBusinessObject(instance.businessObjectRef) }}</pre>
          </DescriptionsItem>
        </Descriptions>

        <!-- 受控管理动作区 -->
        <div class="actions-bar">
          <TypographyTitle :level="5">受控管理动作</TypographyTitle>
          <Alert
            v-if="isTerminal"
            type="info"
            show-icon
            message="实例已处于终态，无法执行管理动作"
            :description="`当前状态：${instanceStatusLabel(instance.state)}（终态）`"
          />
          <Space v-else wrap>
            <Button
              :disabled="!canPause"
              @click="openActionModal('PAUSE')"
            >
              <template #icon><PauseCircleOutlined /></template>
              {{ actionLabel('PAUSE') }}
            </Button>
            <Button
              :disabled="!canResume"
              @click="openActionModal('RESUME')"
            >
              <template #icon><PlayCircleOutlined /></template>
              {{ actionLabel('RESUME') }}
            </Button>
            <Button
              :disabled="!canRetry"
              @click="openActionModal('RETRY')"
            >
              <template #icon><ReloadOutlined /></template>
              {{ actionLabel('RETRY') }}
            </Button>
            <Button
              type="primary"
              ghost
              :disabled="!canMigrate"
              @click="openMigrationPreview"
            >
              <template #icon><ThunderboltOutlined /></template>
              {{ actionLabel('MIGRATE') }}（需预览）
            </Button>
            <Button
              danger
              :disabled="!canTerminate"
              @click="openActionModal('TERMINATE')"
            >
              <template #icon><StopOutlined /></template>
              {{ actionLabel('TERMINATE') }}
            </Button>
            <Button
              danger
              ghost
              :disabled="!canManualCompensate"
              @click="openActionModal('MANUAL_COMPENSATE')"
            >
              <template #icon><ToolOutlined /></template>
              {{ actionLabel('MANUAL_COMPENSATE') }}
            </Button>
          </Space>
        </div>
      </Card>

      <Card v-else-if="!loadingInstance" :bordered="false">
        <Empty description="实例不存在或跨工作空间访问被拒绝" />
      </Card>
    </Spin>

    <Card v-if="instance" :bordered="false" class="tabs-card">
      <Tabs v-model:active-key="activeTab" @change="handleTabChange">
        <TabPane key="overview" tab="诊断摘要">
          <TypographyParagraph type="secondary">
            实例当前状态、活动节点与未解决 incident 数量。如需执行管理动作请使用上方按钮。
          </TypographyParagraph>
        </TabPane>

        <TabPane key="incidents" tab="Incident / Dead-letter">
          <Space direction="vertical" class="incidents-toolbar">
            <Space>
              <Button @click="loadIncidents" :loading="loadingIncidents">
                <template #icon><ReloadOutlined /></template>
                刷新
              </Button>
              <label class="include-resolved-label">
                <input
                  type="checkbox"
                  :checked="includeResolved"
                  @change="(e) => toggleIncludeResolved((e.target as HTMLInputElement).checked)"
                />
                包含已解决 incident
              </label>
            </Space>
          </Space>
          <Table
            :columns="columns"
            :data-source="incidents"
            :loading="loadingIncidents"
            :pagination="false"
            row-key="incidentId"
            size="middle"
            :scroll="{ x: 1300 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'incidentId'">
                <Tooltip :title="record.incidentId">
                  <span class="mono">{{ record.incidentId }}</span>
                </Tooltip>
              </template>
              <template v-else-if="column.key === 'incidentType'">
                <Tag color="volcano">{{ record.incidentType }}</Tag>
              </template>
              <template v-else-if="column.key === 'activityKey'">
                <span class="mono">{{ record.activityKey ?? '—' }}</span>
              </template>
              <template v-else-if="column.key === 'occurredAt'">
                {{ formatDateTime(record.occurredAt) }}
              </template>
              <template v-else-if="column.key === 'resolvedAt'">
                <Tag v-if="record.resolvedAt" color="green">已解决</Tag>
                <Tag v-else color="red">{{ incidentStatusLabel('OPEN') }}</Tag>
                <div v-if="record.resolvedAt" class="muted">
                  {{ formatDateTime(record.resolvedAt) }}
                </div>
              </template>
              <template v-else-if="column.key === 'errorMessage'">
                <Tooltip :title="record.errorMessage ?? ''">
                  <span class="muted">{{ record.errorMessage ?? '—' }}</span>
                </Tooltip>
              </template>
            </template>
            <template #emptyText>
              <Empty description="无 incident 记录" />
            </template>
          </Table>
        </TabPane>

        <TabPane key="history" tab="迁移历史">
          <Space direction="vertical" class="history-toolbar">
            <Button @click="loadMigrationHistory" :loading="loadingHistory">
              <template #icon><ReloadOutlined /></template>
              刷新
            </Button>
          </Space>
          <Table
            :columns="migrationColumns"
            :data-source="migrationHistory"
            :loading="loadingHistory"
            :pagination="false"
            row-key="migrationId"
            size="middle"
            :scroll="{ x: 1500 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'migrationId'">
                <Tooltip :title="record.migrationId">
                  <span class="mono">{{ record.migrationId }}</span>
                </Tooltip>
              </template>
              <template v-else-if="column.key === 'sourceDefinitionId'">
                <Tooltip :title="record.sourceDefinitionId">
                  <span class="mono muted">{{ record.sourceDefinitionId }}</span>
                </Tooltip>
              </template>
              <template v-else-if="column.key === 'targetDefinitionId'">
                <Tooltip :title="record.targetDefinitionId">
                  <span class="mono muted">{{ record.targetDefinitionId }}</span>
                </Tooltip>
              </template>
              <template v-else-if="column.key === 'triggeredBy'">
                <span class="mono">{{ record.triggeredBy }}</span>
              </template>
              <template v-else-if="column.key === 'migratedAt'">
                {{ formatDateTime(record.migratedAt) }}
              </template>
              <template v-else-if="column.key === 'successful'">
                <Tag :color="record.successful ? 'green' : 'red'">
                  {{ record.successful ? '成功' : '失败' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'failureReason'">
                <Tooltip v-if="record.failureReason" :title="record.failureReason">
                  <span class="muted">{{ record.failureReason }}</span>
                </Tooltip>
                <span v-else class="muted">—</span>
              </template>
            </template>
            <template #emptyText>
              <Empty description="无迁移历史记录" />
            </template>
          </Table>
        </TabPane>
      </Tabs>
    </Card>

    <!-- 迁移预览抽屉（MIGRATE 专用） -->
    <MigrationPreviewDrawer
      v-model:visible="migrationPreviewVisible"
      :instance-id="instanceId"
      @plan-ready="onMigrationPlanReady"
    />

    <!-- 执行管理动作模态框 -->
    <ApplyActionModal
      v-model:visible="actionModalVisible"
      :instance="instance"
      :action="actionTarget"
      :migration-plan="lastMigrationPlan"
      @applied="onActionApplied"
    />
  </div>
</template>

<style scoped lang="scss">
.workflow-instance-detail-view {
  padding: 24px;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .page-title {
      margin: 0;
    }
  }

  .instance-summary-card {
    margin-bottom: 16px;

    .actions-bar {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px dashed #f0f0f0;
    }
  }

  .tabs-card {
    .incidents-toolbar,
    .history-toolbar {
      margin-bottom: 12px;
    }

    .include-resolved-label {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      user-select: none;
    }
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }

  .muted {
    color: #888;
  }

  .business-object {
    margin: 0;
    padding: 8px;
    background: #fafafa;
    border-radius: 4px;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.88em;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 240px;
    overflow: auto;
  }
}
</style>

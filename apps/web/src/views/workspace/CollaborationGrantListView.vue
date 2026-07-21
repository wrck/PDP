<script setup lang="ts">
/**
 * 跨工作空间协作授权管理页面（T108、FR-006）。
 *
 * <p><strong>核心操作</strong>：
 * - 查询授权：GET /collaboration-grants/list，支持按方向（OUTGOING/INCOMING）与状态过滤；
 * - 创建授权：POST /collaboration-grants + Idempotency-Key，授予协作方工作空间对目标对象的操作权限；
 * - 撤销授权：POST /collaboration-grants/{id}/revoke + If-Match，需填写撤销原因。
 *
 * <p><strong>边界</strong>：
 * 授权方工作空间将指定目标对象的部分操作权限授予协作方工作空间。
 * 撤销后协作方立即失去对应权限（FR-068 撤权时效）。
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
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import { workspaceApi } from './api'
import {
  canRevokeGrant,
  grantDirectionLabel,
  grantStatusColor,
  grantStatusLabel,
  type CollaborationGrant,
  type GrantDirection,
  type GrantStatus,
  type WorkspaceRole,
} from './types'
import { ApiError } from '@/api'
import {
  confirm,
  showErrorFromApiError,
  showSuccess,
} from '@/composables/feedback'

const route = useRoute()
const router = useRouter()

const workspaceId = computed<string>(() => String(route.params.workspaceId ?? ''))

// ============================================================
// 列表数据状态
// ============================================================

const loading = ref(false)
const acting = ref(false)
const data = ref<CollaborationGrant[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const accumulatedCount = ref(0)

// 过滤
const filterDirection = ref<GrantDirection>('OUTGOING')
const filterStatus = ref<GrantStatus | null>(null)

// 引用数据（角色列表，用于创建授权时选择 roleId）
const roles = ref<WorkspaceRole[]>([])

const columns = [
  { title: '协作方工作空间', dataIndex: 'collaboratorWorkspaceId', key: 'collaboratorWorkspaceId', width: 240, ellipsis: true },
  { title: '目标对象类型', dataIndex: 'targetObjectType', key: 'targetObjectType', width: 160 },
  { title: '目标对象 ID', dataIndex: 'targetObjectId', key: 'targetObjectId', width: 240, ellipsis: true },
  { title: '角色', dataIndex: 'roleId', key: 'roleId', width: 160, ellipsis: true },
  { title: '允许操作', key: 'allowedActions', width: 200 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '有效期', dataIndex: 'validUntil', key: 'validUntil', width: 180 },
  { title: '操作', key: 'actions', width: 120, fixed: 'right' as const },
]

// ============================================================
// 创建授权
// ============================================================

const createModalVisible = ref(false)
const createForm = reactive({
  collaboratorWorkspaceId: '',
  targetObjectType: '',
  targetObjectId: '',
  roleId: '' as string,
  allowedActions: [] as string[],
  validUntil: '',
  reason: '',
})
const newAction = ref('')

// ============================================================
// 撤销授权
// ============================================================

const revokeModalVisible = ref(false)
const revokeForm = reactive({ reason: '' })
const revoking = ref<CollaborationGrant | null>(null)

// ============================================================
// 角色映射
// ============================================================

const roleMap = computed<Map<string, WorkspaceRole>>(() => {
  const m = new Map<string, WorkspaceRole>()
  for (const r of roles.value) m.set(r.id, r)
  return m
})

// ============================================================
// 数据加载
// ============================================================

async function loadRoles(): Promise<void> {
  try {
    const result = await workspaceApi.listWorkspaceRoles(workspaceId.value, {
      pageSize: 200,
      includeSystem: true,
    })
    roles.value = result.data
  } catch (err) {
    handleError(err, '加载角色列表失败')
  }
}

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
    const result = await workspaceApi.listCollaborationGrants(workspaceId.value, {
      cursor,
      pageSize: 50,
      status: filterStatus.value,
      direction: filterDirection.value,
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
    handleError(err, '加载协作授权列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 创建授权
// ============================================================

function openCreateModal(): void {
  createForm.collaboratorWorkspaceId = ''
  createForm.targetObjectType = ''
  createForm.targetObjectId = ''
  createForm.roleId = ''
  createForm.allowedActions = []
  createForm.validUntil = ''
  createForm.reason = ''
  newAction.value = ''
  createModalVisible.value = true
}

function addAction(): void {
  const action = newAction.value.trim()
  if (!action) return
  if (!createForm.allowedActions.includes(action)) {
    createForm.allowedActions.push(action)
  }
  newAction.value = ''
}

async function submitCreate(): Promise<void> {
  if (!createForm.collaboratorWorkspaceId.trim()) {
    message.warning('协作方工作空间 ID 不能为空')
    return
  }
  if (!createForm.targetObjectType.trim() || !createForm.targetObjectId.trim()) {
    message.warning('目标对象类型和 ID 不能为空')
    return
  }
  if (!createForm.roleId) {
    message.warning('请选择角色')
    return
  }
  if (createForm.allowedActions.length === 0) {
    message.warning('至少添加一个允许操作')
    return
  }
  acting.value = true
  try {
    const created = await workspaceApi.createCollaborationGrant(workspaceId.value, {
      collaboratorWorkspaceId: createForm.collaboratorWorkspaceId.trim(),
      target: {
        objectType: createForm.targetObjectType.trim(),
        objectId: createForm.targetObjectId.trim(),
      },
      roleId: createForm.roleId,
      allowedActions: createForm.allowedActions,
      validUntil: createForm.validUntil ? new Date(createForm.validUntil).toISOString() : null,
      reason: createForm.reason.trim() || null,
    })
    data.value.unshift(created)
    accumulatedCount.value = data.value.length
    showSuccess('协作授权已创建')
    createModalVisible.value = false
  } catch (err) {
    handleError(err, '创建协作授权失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 撤销授权
// ============================================================

function openRevokeModal(record: CollaborationGrant): void {
  revoking.value = record
  revokeForm.reason = ''
  revokeModalVisible.value = true
}

async function submitRevoke(): Promise<void> {
  if (!revoking.value) return
  if (!revokeForm.reason.trim()) {
    message.warning('请填写撤销原因')
    return
  }
  const ack = await confirm({
    title: '确认撤销协作授权',
    content: `撤销对协作方工作空间 ${revoking.value.collaboratorWorkspaceId} 的授权？撤销后协作方立即失去对应权限。`,
    danger: true,
  })
  if (!ack) return
  acting.value = true
  try {
    await workspaceApi.revokeCollaborationGrant(
      workspaceId.value,
      revoking.value.id,
      revokeForm.reason.trim(),
      revoking.value.revision,
    )
    data.value = data.value.filter((g) => g.id !== revoking.value!.id)
    accumulatedCount.value = data.value.length
    showSuccess('协作授权已撤销，协作方权限立即失效')
    revokeModalVisible.value = false
  } catch (err) {
    handleConflict(err, '撤销协作授权失败')
  } finally {
    acting.value = false
  }
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

function roleName(id: string): string {
  return roleMap.value.get(id)?.name ?? id
}

function handleConflict(err: unknown, fallback: string): void {
  if (err instanceof ApiError && err.isConflict()) {
    message.error('版本冲突或状态非法，正在刷新最新数据')
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
  void loadRoles()
  void loadFirstPage()
})
</script>

<template>
  <div class="collaboration-grant-list-view">
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
          <span>协作授权管理</span>
        </ASpace>
      </ATypographyTitle>
      <ATypographyParagraph type="secondary">
        跨工作空间协作权限授予与撤销。授权方将指定目标对象的部分操作权限授予协作方工作空间。
        撤销后协作方立即失去对应权限（FR-068 撤权时效）。
      </ATypographyParagraph>
    </ATypography>

    <ACard class="filter-card" :bordered="false">
      <ASpace wrap>
        <ASelect
          v-model:value="filterDirection"
          style="width: 180px"
          @change="loadFirstPage"
        >
          <ASelectOption value="OUTGOING">{{ grantDirectionLabel('OUTGOING') }}</ASelectOption>
          <ASelectOption value="INCOMING">{{ grantDirectionLabel('INCOMING') }}</ASelectOption>
        </ASelect>
        <ASelect
          v-model:value="filterStatus"
          allow-clear
          placeholder="按状态过滤"
          style="width: 160px"
          @change="loadFirstPage"
        >
          <ASelectOption value="DRAFT">草稿</ASelectOption>
          <ASelectOption value="ACTIVE">生效中</ASelectOption>
          <ASelectOption value="EXPIRED">已到期</ASelectOption>
          <ASelectOption value="REVOKED">已撤销</ASelectOption>
        </ASelect>
        <AButton @click="loadFirstPage">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
        <AButton
          type="primary"
          ghost
          :disabled="filterDirection !== 'OUTGOING'"
          @click="openCreateModal"
        >
          <template #icon><PlusOutlined /></template>
          创建授权
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
        :scroll="{ x: 1400 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'collaboratorWorkspaceId'">
            <ATooltip :title="record.collaboratorWorkspaceId">
              <span class="mono">{{ record.collaboratorWorkspaceId }}</span>
            </ATooltip>
          </template>
          <template v-else-if="column.key === 'targetObjectId'">
            <ATooltip :title="record.targetObjectId">
              <span class="mono">{{ record.targetObjectId }}</span>
            </ATooltip>
          </template>
          <template v-else-if="column.key === 'roleId'">
            <ATag color="blue">{{ roleName(record.roleId) }}</ATag>
          </template>
          <template v-else-if="column.key === 'allowedActions'">
            <ATag
              v-for="action in record.allowedActions"
              :key="action"
            >
              <span class="mono">{{ action }}</span>
            </ATag>
          </template>
          <template v-else-if="column.key === 'status'">
            <ATag :color="grantStatusColor(record.status as GrantStatus)">
              {{ grantStatusLabel(record.status as GrantStatus) }}
            </ATag>
          </template>
          <template v-else-if="column.key === 'validUntil'">
            {{ formatDateTime(record.validUntil) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <AButton
              v-if="canRevokeGrant(record.status as GrantStatus) && filterDirection === 'OUTGOING'"
              size="small"
              danger
              :loading="acting"
              @click="openRevokeModal(record as CollaborationGrant)"
            >
              <template #icon><StopOutlined /></template>
              撤销
            </AButton>
            <span v-else class="meta-text">—</span>
          </template>
        </template>

        <template #emptyText>
          <AEmpty description="暂无协作授权，点击右上角按钮创建" />
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

    <!-- 创建协作授权模态框 -->
    <AModal
      v-model:open="createModalVisible"
      title="创建协作授权"
      :confirm-loading="acting"
      ok-text="创建"
      cancel-text="取消"
      width="720px"
      @ok="submitCreate"
    >
      <AForm layout="vertical">
        <AFormItem label="协作方工作空间 ID" required>
          <AInput
            v-model:value="createForm.collaboratorWorkspaceId"
            placeholder="UUID 格式的协作方工作空间 ID"
          />
        </AFormItem>
        <ASpace style="width: 100%" align="start">
          <AFormItem label="目标对象类型" required style="flex: 1">
            <AInput
              v-model:value="createForm.targetObjectType"
              placeholder="如 project、deliverable"
              :max-length="64"
            />
          </AFormItem>
          <AFormItem label="目标对象 ID" required style="flex: 1.4">
            <AInput
              v-model:value="createForm.targetObjectId"
              placeholder="UUID 格式的目标对象 ID"
            />
          </AFormItem>
        </ASpace>
        <AFormItem label="角色" required>
          <ASelect
            v-model:value="createForm.roleId"
            placeholder="选择授予的角色"
          >
            <ASelectOption
              v-for="role in roles"
              :key="role.id"
              :value="role.id"
              :disabled="role.status !== 'ACTIVE'"
            >
              {{ role.name }} ({{ role.key }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="允许操作" required>
          <ASpace style="width: 100%">
            <AInput
              v-model:value="newAction"
              placeholder="如 read、update、approve"
              style="width: 280px"
              @press-enter="addAction"
            />
            <AButton @click="addAction">添加</AButton>
          </ASpace>
          <div class="action-chips">
            <ATag
              v-for="action in createForm.allowedActions"
              :key="action"
              closable
              @close="createForm.allowedActions = createForm.allowedActions.filter((a) => a !== action)"
            >
              <span class="mono">{{ action }}</span>
            </ATag>
          </div>
        </AFormItem>
        <AFormItem label="有效期（ISO 日期时间，留空表示长期）">
          <AInput
            v-model:value="createForm.validUntil"
            placeholder="如 2026-12-31T23:59:59Z"
          />
        </AFormItem>
        <AFormItem label="授权原因">
          <AInput
            v-model:value="createForm.reason"
            type="textarea"
            :rows="2"
            :max-length="512"
            placeholder="审计记录"
          />
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 撤销授权模态框 -->
    <AModal
      v-model:open="revokeModalVisible"
      title="撤销协作授权"
      :confirm-loading="acting"
      ok-text="确认撤销"
      cancel-text="取消"
      ok-button-props="{ danger: true }"
      @ok="submitRevoke"
    >
      <AForm layout="vertical">
        <AFormItem label="撤销原因" required>
          <AInput
            v-model:value="revokeForm.reason"
            type="textarea"
            :rows="3"
            :max-length="512"
            placeholder="审计记录，撤销后协作方权限立即失效"
          />
        </AFormItem>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.collaboration-grant-list-view {
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

  .action-chips {
    margin-top: 8px;
    min-height: 32px;
  }
}
</style>

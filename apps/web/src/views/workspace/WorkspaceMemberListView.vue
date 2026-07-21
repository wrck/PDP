<script setup lang="ts">
/**
 * 工作空间成员管理页面（T108、FR-005、FR-068）。
 *
 * <p><strong>核心操作</strong>：
 * - 列表查询：GET /members，支持按状态/组织/角色过滤；
 * - 添加成员：POST /members + Idempotency-Key，绑定角色/数据范围/组织/有效期；
 * - 编辑成员：PATCH /members/{id} + If-Match；
 * - 暂停成员：POST /members/{id}/suspend + If-Match + Idempotency-Key，需填写原因；
 * - 恢复成员：POST /members/{id}/resume + If-Match + Idempotency-Key；
 * - 移除成员：DELETE /members/{id} + If-Match，使用 confirmHighRisk（FR-068）。
 *
 * <p><strong>FR-068 撤权实时性</strong>：
 * 后端单条 UPDATE 原子完成，1 分钟内下次访问拒绝。前端在移除/暂停成功后明确提示。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Button as AButton,
  Card as ACard,
  DatePicker as ADatePicker,
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
import dayjs, { type Dayjs } from 'dayjs'
import { workspaceApi } from './api'
import {
  canRemoveMember,
  canResumeMember,
  canSuspendMember,
  memberStatusColor,
  memberStatusLabel,
  type MemberStatus,
  type Organization,
  type WorkspaceMember,
  type WorkspaceRole,
  type DataScope,
} from './types'
import { ApiError } from '@/api'
import {
  confirm,
  confirmHighRisk,
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
const data = ref<WorkspaceMember[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const accumulatedCount = ref(0)

// 过滤
const filterStatus = ref<MemberStatus | null>(null)
const filterOrgId = ref<string | null>(null)
const filterRoleId = ref<string | null>(null)

// 引用数据
const roles = ref<WorkspaceRole[]>([])
const organizations = ref<Organization[]>([])
const dataScopes = ref<DataScope[]>([])

const columns = [
  { title: 'User ID', dataIndex: 'userId', key: 'userId', width: 240, ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '角色', key: 'roleIds', width: 200 },
  { title: '组织', key: 'organizationId', width: 160, ellipsis: true },
  { title: '有效期', dataIndex: 'validUntil', key: 'validUntil', width: 180 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' as const },
]

// ============================================================
// 添加成员
// ============================================================

const createModalVisible = ref(false)
const createForm = reactive({
  userId: '',
  roleIds: [] as string[],
  organizationId: '' as string | null,
  dataScopeIds: [] as string[],
  validUntil: null as Dayjs | null,
})

// ============================================================
// 编辑成员
// ============================================================

const editModalVisible = ref(false)
const editForm = reactive({
  roleIds: [] as string[],
  organizationId: '' as string | null,
  dataScopeIds: [] as string[],
  validUntil: null as Dayjs | null,
})
const editing = ref<WorkspaceMember | null>(null)

// ============================================================
// 暂停成员（原因）
// ============================================================

const suspendModalVisible = ref(false)
const suspendForm = reactive({ reason: '' })
const suspending = ref<WorkspaceMember | null>(null)

// ============================================================
// 引用数据映射
// ============================================================

const roleMap = computed<Map<string, WorkspaceRole>>(() => {
  const m = new Map<string, WorkspaceRole>()
  for (const r of roles.value) m.set(r.id, r)
  return m
})

const orgMap = computed<Map<string, Organization>>(() => {
  const m = new Map<string, Organization>()
  for (const o of organizations.value) m.set(o.id, o)
  return m
})

// ============================================================
// 数据加载
// ============================================================

async function loadReferences(): Promise<void> {
  try {
    const [rolesResult, orgsResult, scopesResult] = await Promise.all([
      workspaceApi.listWorkspaceRoles(workspaceId.value, { pageSize: 200, includeSystem: true }),
      workspaceApi.listOrganizations(workspaceId.value, { pageSize: 200 }),
      workspaceApi.listDataScopes(workspaceId.value, { pageSize: 200 }),
    ])
    roles.value = rolesResult.data
    organizations.value = orgsResult.data
    dataScopes.value = scopesResult.data
  } catch (err) {
    handleError(err, '加载引用数据失败')
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
    const result = await workspaceApi.listWorkspaceMembers(workspaceId.value, {
      cursor,
      pageSize: 50,
      status: filterStatus.value,
      organizationId: filterOrgId.value,
      roleId: filterRoleId.value,
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
    handleError(err, '加载成员列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 添加成员
// ============================================================

function openCreateModal(): void {
  createForm.userId = ''
  createForm.roleIds = []
  createForm.organizationId = null
  createForm.dataScopeIds = []
  createForm.validUntil = null
  createModalVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.userId.trim()) {
    message.warning('User ID 不能为空')
    return
  }
  if (createForm.roleIds.length === 0) {
    message.warning('至少选择一个角色')
    return
  }
  acting.value = true
  try {
    const created = await workspaceApi.addWorkspaceMember(workspaceId.value, {
      userId: createForm.userId.trim(),
      roleIds: createForm.roleIds,
      organizationId: createForm.organizationId || null,
      dataScopeIds: createForm.dataScopeIds,
      validUntil: createForm.validUntil ? createForm.validUntil.toISOString() : null,
    })
    data.value.unshift(created)
    accumulatedCount.value = data.value.length
    showSuccess('成员已添加')
    createModalVisible.value = false
  } catch (err) {
    handleError(err, '添加成员失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 编辑成员
// ============================================================

function openEditModal(record: WorkspaceMember): void {
  editing.value = record
  editForm.roleIds = [...record.roleIds]
  editForm.organizationId = record.organizationId ?? null
  editForm.dataScopeIds = [...record.dataScopeIds]
  editForm.validUntil = record.validUntil ? dayjs(record.validUntil) : null
  editModalVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editing.value) return
  if (editForm.roleIds.length === 0) {
    message.warning('至少选择一个角色')
    return
  }
  acting.value = true
  try {
    const updated = await workspaceApi.updateWorkspaceMember(
      workspaceId.value,
      editing.value.id,
      {
        roleIds: editForm.roleIds,
        organizationId: editForm.organizationId || null,
        dataScopeIds: editForm.dataScopeIds,
        validUntil: editForm.validUntil ? editForm.validUntil.toISOString() : null,
      },
      editing.value.revision,
    )
    replaceInList(updated)
    showSuccess('成员已更新')
    editModalVisible.value = false
  } catch (err) {
    handleConflict(err, '更新成员失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 暂停/恢复/移除
// ============================================================

function openSuspendModal(record: WorkspaceMember): void {
  suspending.value = record
  suspendForm.reason = ''
  suspendModalVisible.value = true
}

async function submitSuspend(): Promise<void> {
  if (!suspending.value) return
  if (!suspendForm.reason.trim()) {
    message.warning('请填写暂停原因')
    return
  }
  acting.value = true
  try {
    const updated = await workspaceApi.suspendWorkspaceMember(
      workspaceId.value,
      suspending.value.id,
      suspendForm.reason.trim(),
      suspending.value.revision,
    )
    replaceInList(updated)
    showSuccess('成员已暂停，1 分钟内会话撤销生效')
    suspendModalVisible.value = false
  } catch (err) {
    handleConflict(err, '暂停成员失败')
  } finally {
    acting.value = false
  }
}

async function doResume(record: WorkspaceMember): Promise<void> {
  const ack = await confirm({
    title: '确认恢复成员',
    content: `恢复成员 ${record.userId} 的访问权限？`,
  })
  if (!ack) return
  acting.value = true
  try {
    const updated = await workspaceApi.resumeWorkspaceMember(
      workspaceId.value,
      record.id,
      record.revision,
    )
    replaceInList(updated)
    showSuccess('成员已恢复')
  } catch (err) {
    handleConflict(err, '恢复成员失败')
  } finally {
    acting.value = false
  }
}

async function doRemove(record: WorkspaceMember): Promise<void> {
  const ack = await confirmHighRisk({
    title: '高风险确认：移除成员',
    content: `移除成员 ${record.userId}？此操作将立即撤销其访问权限，1 分钟内会话失效。`,
    impactSummary: '成员状态变更为 REMOVED；无法恢复，需重新添加。',
  })
  if (!ack) return
  acting.value = true
  try {
    await workspaceApi.removeWorkspaceMember(workspaceId.value, record.id, record.revision)
    data.value = data.value.filter((m) => m.id !== record.id)
    accumulatedCount.value = data.value.length
    showSuccess('成员已移除，1 分钟内会话撤销生效')
  } catch (err) {
    handleConflict(err, '移除成员失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 辅助方法
// ============================================================

function replaceInList(updated: WorkspaceMember): void {
  const idx = data.value.findIndex((m) => m.id === updated.id)
  if (idx >= 0) data.value[idx] = updated
}

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function roleName(id: string): string {
  return roleMap.value.get(id)?.name ?? id
}

function orgName(id: string | null | undefined): string {
  if (!id) return '—'
  return orgMap.value.get(id)?.name ?? id
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
  void loadReferences()
  void loadFirstPage()
})
</script>

<template>
  <div class="workspace-member-list-view">
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
          <span>成员管理</span>
        </ASpace>
      </ATypographyTitle>
      <ATypographyParagraph type="secondary">
        管理工作空间成员的角色、组织归属、数据范围与有效期。
        移除/暂停成员后 1 分钟内会话撤销生效（FR-068）。
      </ATypographyParagraph>
    </ATypography>

    <ACard class="filter-card" :bordered="false">
      <ASpace wrap>
        <ASelect
          v-model:value="filterStatus"
          allow-clear
          placeholder="按状态过滤"
          style="width: 160px"
          @change="loadFirstPage"
        >
          <ASelectOption value="ACTIVE">正常</ASelectOption>
          <ASelectOption value="SUSPENDED">已暂停</ASelectOption>
          <ASelectOption value="REMOVED">已移除</ASelectOption>
        </ASelect>
        <ASelect
          v-model:value="filterOrgId"
          allow-clear
          placeholder="按组织过滤"
          style="width: 200px"
          @change="loadFirstPage"
        >
          <ASelectOption
            v-for="org in organizations"
            :key="org.id"
            :value="org.id"
          >
            {{ org.name }}
          </ASelectOption>
        </ASelect>
        <ASelect
          v-model:value="filterRoleId"
          allow-clear
          placeholder="按角色过滤"
          style="width: 200px"
          @change="loadFirstPage"
        >
          <ASelectOption
            v-for="role in roles"
            :key="role.id"
            :value="role.id"
          >
            {{ role.name }}
          </ASelectOption>
        </ASelect>
        <AButton @click="loadFirstPage">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
        <AButton type="primary" ghost @click="openCreateModal">
          <template #icon><PlusOutlined /></template>
          添加成员
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
          <template v-if="column.key === 'userId'">
            <ATooltip :title="record.userId">
              <span class="mono">{{ record.userId }}</span>
            </ATooltip>
          </template>
          <template v-else-if="column.key === 'status'">
            <ATag :color="memberStatusColor(record.status as MemberStatus)">
              {{ memberStatusLabel(record.status as MemberStatus) }}
            </ATag>
          </template>
          <template v-else-if="column.key === 'roleIds'">
            <ATag
              v-for="rid in record.roleIds"
              :key="rid"
              color="blue"
            >
              {{ roleName(rid) }}
            </ATag>
          </template>
          <template v-else-if="column.key === 'organizationId'">
            {{ orgName(record.organizationId) }}
          </template>
          <template v-else-if="column.key === 'validUntil'">
            {{ formatDateTime(record.validUntil) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <ASpace>
              <AButton
                size="small"
                :disabled="record.status === 'REMOVED'"
                @click="openEditModal(record as WorkspaceMember)"
              >
                <template #icon><EditOutlined /></template>
                编辑
              </AButton>
              <AButton
                v-if="canSuspendMember(record.status as MemberStatus)"
                size="small"
                danger
                ghost
                @click="openSuspendModal(record as WorkspaceMember)"
              >
                暂停
              </AButton>
              <AButton
                v-if="canResumeMember(record.status as MemberStatus)"
                size="small"
                @click="doResume(record as WorkspaceMember)"
              >
                恢复
              </AButton>
              <AButton
                v-if="canRemoveMember(record.status as MemberStatus)"
                size="small"
                danger
                :loading="acting"
                @click="doRemove(record as WorkspaceMember)"
              >
                <template #icon><DeleteOutlined /></template>
                移除
              </AButton>
            </ASpace>
          </template>
        </template>

        <template #emptyText>
          <AEmpty description="暂无成员，点击右上角按钮添加" />
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

    <!-- 添加成员模态框 -->
    <AModal
      v-model:open="createModalVisible"
      title="添加工作空间成员"
      :confirm-loading="acting"
      ok-text="添加"
      cancel-text="取消"
      width="640px"
      @ok="submitCreate"
    >
      <AForm layout="vertical">
        <AFormItem label="User ID" required>
          <AInput
            v-model:value="createForm.userId"
            placeholder="UUID 格式的用户 ID"
          />
        </AFormItem>
        <AFormItem label="角色（可多选）" required>
          <ASelect
            v-model:value="createForm.roleIds"
            mode="multiple"
            placeholder="选择至少一个角色"
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
        <AFormItem label="所属组织">
          <ASelect
            v-model:value="createForm.organizationId"
            allow-clear
            placeholder="可选"
          >
            <ASelectOption
              v-for="org in organizations"
              :key="org.id"
              :value="org.id"
              :disabled="org.status !== 'ACTIVE'"
            >
              {{ org.name }} ({{ org.code }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="数据范围（可多选）">
          <ASelect
            v-model:value="createForm.dataScopeIds"
            mode="multiple"
            placeholder="可选"
          >
            <ASelectOption
              v-for="scope in dataScopes"
              :key="scope.id"
              :value="scope.id"
            >
              {{ scope.name }} ({{ scope.key }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="有效期">
          <ADatePicker
            v-model:value="createForm.validUntil"
            show-time
            style="width: 100%"
            placeholder="留空表示长期有效"
          />
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 编辑成员模态框 -->
    <AModal
      v-model:open="editModalVisible"
      title="编辑成员"
      :confirm-loading="acting"
      ok-text="保存"
      cancel-text="取消"
      width="640px"
      @ok="submitEdit"
    >
      <AForm layout="vertical">
        <AFormItem label="角色（可多选）" required>
          <ASelect
            v-model:value="editForm.roleIds"
            mode="multiple"
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
        <AFormItem label="所属组织">
          <ASelect
            v-model:value="editForm.organizationId"
            allow-clear
          >
            <ASelectOption
              v-for="org in organizations"
              :key="org.id"
              :value="org.id"
              :disabled="org.status !== 'ACTIVE'"
            >
              {{ org.name }} ({{ org.code }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="数据范围（可多选）">
          <ASelect
            v-model:value="editForm.dataScopeIds"
            mode="multiple"
          >
            <ASelectOption
              v-for="scope in dataScopes"
              :key="scope.id"
              :value="scope.id"
            >
              {{ scope.name }} ({{ scope.key }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="有效期">
          <ADatePicker
            v-model:value="editForm.validUntil"
            show-time
            style="width: 100%"
          />
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 暂停成员模态框 -->
    <AModal
      v-model:open="suspendModalVisible"
      title="暂停成员访问"
      :confirm-loading="acting"
      ok-text="确认暂停"
      cancel-text="取消"
      @ok="submitSuspend"
    >
      <AForm layout="vertical">
        <AFormItem label="暂停原因" required>
          <AInput
            v-model:value="suspendForm.reason"
            type="textarea"
            :rows="3"
            :max-length="512"
            placeholder="审计记录，1 分钟内会话撤销生效"
          />
        </AFormItem>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.workspace-member-list-view {
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

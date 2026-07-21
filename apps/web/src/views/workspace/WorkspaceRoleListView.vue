<script setup lang="ts">
/**
 * 工作空间角色管理页面（T108、FR-063）。
 *
 * <p><strong>核心操作</strong>：
 * - 列表查询：GET /roles，支持 includeSystem 复选框；
 * - 创建角色：POST /roles + Idempotency-Key，绑定权限集合与数据范围类型；
 * - 编辑角色：PATCH /roles/{id} + If-Match，更新权限/数据范围/描述；
 * - 停用角色：POST /roles/{id}/disable + If-Match + Idempotency-Key，系统角色不可停用。
 *
 * <p><strong>权限模型（FR-063）</strong>：
 * - 功能权限键格式 `<domain>.<resource>.<action>`；
 * - 数据范围类型与功能权限正交：权限决定能否操作，数据范围决定操作哪些数据行。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Button as AButton,
  Card as ACard,
  Checkbox as ACheckbox,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  Modal as AModal,
  Select as ASelect,
  SelectOption as ASelectOption,
  Space as ASpace,
  Switch as ASwitch,
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
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import { workspaceApi } from './api'
import {
  DATA_SCOPE_TYPE_OPTIONS,
  dataScopeTypeLabel,
  roleStatusColor,
  roleStatusLabel,
  type DataScopeType,
  type WorkspaceRole,
} from './types'
import { ApiError } from '@/api'
import {
  confirm,
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
const data = ref<WorkspaceRole[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const total = ref<number | null>(null)
const accumulatedCount = ref(0)
const includeSystem = ref(true)

// ============================================================
// 创建/编辑
// ============================================================

const createModalVisible = ref(false)
const createForm = reactive({
  key: '',
  name: '',
  description: '',
  permissions: [] as string[],
  dataScopeType: 'WORKSPACE' as DataScopeType,
  isSystem: false,
})
const newPermission = ref('')

const editModalVisible = ref(false)
const editForm = reactive({
  name: '',
  description: '',
  permissions: [] as string[],
  dataScopeType: 'WORKSPACE' as DataScopeType,
})
const editing = ref<WorkspaceRole | null>(null)
const editNewPermission = ref('')

const columns = [
  { title: 'Key', dataIndex: 'key', key: 'key', width: 180, ellipsis: true },
  { title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
  { title: '数据范围', dataIndex: 'dataScopeType', key: 'dataScopeType', width: 140 },
  { title: '权限数', key: 'permissionCount', width: 90 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '类型', key: 'isSystem', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 180, fixed: 'right' as const },
]

// ============================================================
// 常见权限键（供快速选择）
// ============================================================

const COMMON_PERMISSIONS = [
  'workspace.create',
  'workspace.update',
  'workspace.archive',
  'workspace.member.manage',
  'workspace.role.manage',
  'workspace.grant.cross',
  'project.create',
  'project.update',
  'project.rollback',
  'project.close',
  'project.archive',
  'task.create',
  'task.assign',
  'task.update',
  'task.complete',
  'baseline.create',
  'baseline.replace',
  'baseline.adjust',
  'deliverable.submit',
  'deliverable.review',
  'deliverable.release',
  'approval.act',
  'approval.delegate',
  'workflow.definition.deploy',
  'workflow.definition.transition',
  'workflow.instance.admin',
  'audit.view',
  'audit.export',
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
    const result = await workspaceApi.listWorkspaceRoles(workspaceId.value, {
      cursor,
      pageSize: 50,
      includeSystem: includeSystem.value,
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
    handleError(err, '加载角色列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 创建角色
// ============================================================

function openCreateModal(): void {
  createForm.key = ''
  createForm.name = ''
  createForm.description = ''
  createForm.permissions = []
  createForm.dataScopeType = 'WORKSPACE'
  createForm.isSystem = false
  newPermission.value = ''
  createModalVisible.value = true
}

/** 添加权限键到创建表单。 */
function addPermission(): void {
  const perm = newPermission.value.trim()
  if (!perm) return
  if (!createForm.permissions.includes(perm)) {
    createForm.permissions.push(perm)
  }
  newPermission.value = ''
}

/** 切换常见权限选中状态（创建）。 */
function toggleCommonPermission(perm: string, checked: boolean): void {
  if (checked) {
    if (!createForm.permissions.includes(perm)) {
      createForm.permissions.push(perm)
    }
  } else {
    createForm.permissions = createForm.permissions.filter((p) => p !== perm)
  }
}

async function submitCreate(): Promise<void> {
  if (!createForm.key.trim() || !createForm.name.trim()) {
    message.warning('Key 和名称不能为空')
    return
  }
  acting.value = true
  try {
    await workspaceApi.createWorkspaceRole(workspaceId.value, {
      key: createForm.key.trim(),
      name: createForm.name.trim(),
      description: createForm.description.trim() || null,
      permissions: createForm.permissions,
      dataScopeType: createForm.dataScopeType,
      isSystem: createForm.isSystem,
    })
    showSuccess('角色已创建')
    createModalVisible.value = false
    await loadFirstPage()
  } catch (err) {
    handleError(err, '创建角色失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 编辑角色
// ============================================================

function openEditModal(record: WorkspaceRole): void {
  editing.value = record
  editForm.name = record.name
  editForm.description = record.description ?? ''
  editForm.permissions = [...record.permissions]
  editForm.dataScopeType = record.dataScopeType
  editNewPermission.value = ''
  editModalVisible.value = true
}

function addEditPermission(): void {
  const perm = editNewPermission.value.trim()
  if (!perm) return
  if (!editForm.permissions.includes(perm)) {
    editForm.permissions.push(perm)
  }
  editNewPermission.value = ''
}

function toggleEditCommonPermission(perm: string, checked: boolean): void {
  if (checked) {
    if (!editForm.permissions.includes(perm)) {
      editForm.permissions.push(perm)
    }
  } else {
    editForm.permissions = editForm.permissions.filter((p) => p !== perm)
  }
}

async function submitEdit(): Promise<void> {
  if (!editing.value) return
  if (!editForm.name.trim()) {
    message.warning('名称不能为空')
    return
  }
  acting.value = true
  try {
    const updated = await workspaceApi.updateWorkspaceRole(
      workspaceId.value,
      editing.value.id,
      {
        name: editForm.name.trim(),
        description: editForm.description.trim() || null,
        permissions: editForm.permissions,
        dataScopeType: editForm.dataScopeType,
      },
      editing.value.revision,
    )
    const idx = data.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) data.value[idx] = updated
    showSuccess('角色已更新')
    editModalVisible.value = false
  } catch (err) {
    handleConflict(err, '更新角色失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 停用角色
// ============================================================

async function disableRole(record: WorkspaceRole): Promise<void> {
  if (record.isSystem) {
    message.warning('系统角色不可停用')
    return
  }
  const ack = await confirm({
    title: '确认停用角色',
    content: `停用角色"${record.name}"？绑定此角色的成员将失去对应权限。`,
    danger: true,
  })
  if (!ack) return
  acting.value = true
  try {
    const updated = await workspaceApi.disableWorkspaceRole(
      workspaceId.value,
      record.id,
      record.revision,
    )
    const idx = data.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) data.value[idx] = updated
    showSuccess(`角色 ${record.name} 已停用`)
  } catch (err) {
    handleConflict(err, '停用角色失败')
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

function handleConflict(err: unknown, fallback: string): void {
  if (err instanceof ApiError && err.isConflict()) {
    message.error('版本冲突，正在刷新最新数据')
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
  <div class="workspace-role-list-view">
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
          <span>角色管理</span>
        </ASpace>
      </ATypographyTitle>
      <ATypographyParagraph type="secondary">
        工作空间角色绑定功能权限键集合与数据范围类型。权限键格式
        <span class="mono">&lt;domain&gt;.&lt;resource&gt;.&lt;action&gt;</span>。
        系统角色不可停用。
      </ATypographyParagraph>
    </ATypography>

    <ACard class="filter-card" :bordered="false">
      <ASpace>
        <ACheckbox v-model:checked="includeSystem" @change="loadFirstPage">
          包含系统角色
        </ACheckbox>
        <AButton @click="loadFirstPage">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
        <AButton type="primary" ghost @click="openCreateModal">
          <template #icon><PlusOutlined /></template>
          创建角色
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
        :scroll="{ x: 1200 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'key'">
            <ATooltip :title="record.key">
              <span class="mono">{{ record.key }}</span>
            </ATooltip>
          </template>
          <template v-else-if="column.key === 'dataScopeType'">
            <ATag>{{ dataScopeTypeLabel(record.dataScopeType as DataScopeType) }}</ATag>
          </template>
          <template v-else-if="column.key === 'permissionCount'">
            {{ record.permissions?.length ?? 0 }}
          </template>
          <template v-else-if="column.key === 'status'">
            <ATag :color="roleStatusColor(record.status)">
              {{ roleStatusLabel(record.status) }}
            </ATag>
          </template>
          <template v-else-if="column.key === 'isSystem'">
            <ATag v-if="record.isSystem" color="purple">系统</ATag>
            <ATag v-else color="default">自定义</ATag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <ASpace>
              <AButton
                size="small"
                :disabled="record.status === 'DISABLED'"
                @click="openEditModal(record as WorkspaceRole)"
              >
                <template #icon><EditOutlined /></template>
                编辑
              </AButton>
              <AButton
                size="small"
                danger
                :disabled="record.isSystem || record.status === 'DISABLED'"
                :loading="acting"
                @click="disableRole(record as WorkspaceRole)"
              >
                <template #icon><StopOutlined /></template>
                停用
              </AButton>
            </ASpace>
          </template>
        </template>

        <template #emptyText>
          <AEmpty description="暂无角色，点击右上角按钮创建" />
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

    <!-- 创建角色模态框 -->
    <AModal
      v-model:open="createModalVisible"
      title="创建自定义角色"
      :confirm-loading="acting"
      ok-text="创建"
      cancel-text="取消"
      width="720px"
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
          <ASelect v-model:value="createForm.dataScopeType">
            <ASelectOption
              v-for="opt in DATA_SCOPE_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="系统角色">
          <ASpace>
            <ASwitch v-model:checked="createForm.isSystem" />
            <ATypographyParagraph type="secondary" style="margin: 0">
              系统角色不可停用（如 WORKSPACE_ADMIN）
            </ATypographyParagraph>
          </ASpace>
        </AFormItem>
        <AFormItem label="权限键">
          <ASpace style="width: 100%">
            <AInput
              v-model:value="newPermission"
              placeholder="如 workspace.member.manage"
              style="width: 280px"
              @press-enter="addPermission"
            />
            <AButton @click="addPermission">添加</AButton>
          </ASpace>
          <div class="permission-chips">
            <ATag
              v-for="perm in createForm.permissions"
              :key="perm"
              closable
              @close="createForm.permissions = createForm.permissions.filter((p) => p !== perm)"
            >
              <span class="mono">{{ perm }}</span>
            </ATag>
          </div>
        </AFormItem>
        <AFormItem label="常见权限快捷选择">
          <div class="common-permissions">
            <ACheckbox
              v-for="perm in COMMON_PERMISSIONS"
              :key="perm"
              :checked="createForm.permissions.includes(perm)"
              @change="(e: { target: { checked: boolean } }) => toggleCommonPermission(perm, e.target.checked)"
            >
              <span class="mono">{{ perm }}</span>
            </ACheckbox>
          </div>
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 编辑角色模态框 -->
    <AModal
      v-model:open="editModalVisible"
      title="编辑角色"
      :confirm-loading="acting"
      ok-text="保存"
      cancel-text="取消"
      width="720px"
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
          <ASelect v-model:value="editForm.dataScopeType">
            <ASelectOption
              v-for="opt in DATA_SCOPE_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </ASelectOption>
          </ASelect>
        </AFormItem>
        <AFormItem label="权限键">
          <ASpace style="width: 100%">
            <AInput
              v-model:value="editNewPermission"
              placeholder="如 workspace.member.manage"
              style="width: 280px"
              @press-enter="addEditPermission"
            />
            <AButton @click="addEditPermission">添加</AButton>
          </ASpace>
          <div class="permission-chips">
            <ATag
              v-for="perm in editForm.permissions"
              :key="perm"
              closable
              @close="editForm.permissions = editForm.permissions.filter((p) => p !== perm)"
            >
              <span class="mono">{{ perm }}</span>
            </ATag>
          </div>
        </AFormItem>
        <AFormItem label="常见权限快捷选择">
          <div class="common-permissions">
            <ACheckbox
              v-for="perm in COMMON_PERMISSIONS"
              :key="perm"
              :checked="editForm.permissions.includes(perm)"
              @change="(e: { target: { checked: boolean } }) => toggleEditCommonPermission(perm, e.target.checked)"
            >
              <span class="mono">{{ perm }}</span>
            </ACheckbox>
          </div>
        </AFormItem>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.workspace-role-list-view {
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

  .permission-chips {
    margin-top: 8px;
    min-height: 32px;
  }

  .common-permissions {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 8px;
    margin-top: 8px;
  }
}
</style>

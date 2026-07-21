<script setup lang="ts">
/**
 * 工作空间详情页面（T108、FR-003、FR-068）。
 *
 * 最复杂的工作空间治理页面，承担以下职责：
 * - 展示工作空间基本信息（ADescriptions）与状态（ATag）；
 * - 编辑基本信息（PATCH /workspaces/{id} + If-Match，409 自动刷新）；
 * - 状态迁移：activate / suspend / archive / restore
 *   - 暂停/归档需填写原因（ReasonCommand）；
 *   - 归档使用 confirmHighRisk 二次确认（FR-168）。
 * - 转移负责人（PUT /workspaces/{id}/owner + If-Match + Idempotency-Key）；
 * - 子管理页面入口（组织树 / 成员 / 角色 / 数据范围 / 协作授权）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Button as AButton,
  Card as ACard,
  Descriptions as ADescriptions,
  DescriptionsItem as ADescriptionsItem,
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  Modal as AModal,
  Select as ASelect,
  SelectOption as ASelectOption,
  Space as ASpace,
  Spin as ASpin,
  Tag as ATag,
  Typography as ATypography,
  TypographyParagraph as ATypographyParagraph,
  TypographyTitle as ATypographyTitle,
  message,
} from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  EditOutlined,
  ReloadOutlined,
  SwapOutlined,
  TeamOutlined,
  ApartmentOutlined,
  SafetyCertificateOutlined,
  FilterOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { workspaceApi } from './api'
import { useWorkspaceStore } from './store'
import {
  canActivateWorkspace,
  canArchiveWorkspace,
  canRestoreWorkspace,
  canSuspendWorkspace,
  workspaceStatusColor,
  workspaceStatusLabel,
  type Workspace,
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
const workspaceStore = useWorkspaceStore()

const workspaceId = computed<string>(() => String(route.params.workspaceId ?? ''))

// ============================================================
// 详情状态
// ============================================================

const loading = ref(false)
const acting = ref(false)
const detail = ref<Workspace | null>(null)

// ============================================================
// 编辑基本信息
// ============================================================

const editModalVisible = ref(false)
const editForm = reactive({
  name: '',
  description: '',
  defaultLocale: 'zh-CN',
  defaultTimezone: 'Asia/Shanghai',
})

// ============================================================
// 状态迁移（暂停/归档原因）
// ============================================================

const reasonModalVisible = ref(false)
const reasonModalTitle = ref('')
const reasonForm = reactive({ reason: '' })
const pendingAction = ref<'suspend' | 'archive' | null>(null)

// ============================================================
// 转移负责人
// ============================================================

const transferModalVisible = ref(false)
const transferForm = reactive({
  newOwnerUserId: '',
  reason: '',
})

// ============================================================
// 数据加载
// ============================================================

async function loadDetail(): Promise<void> {
  if (!workspaceId.value) return
  loading.value = true
  try {
    const ws = await workspaceApi.getWorkspace(workspaceId.value)
    detail.value = ws
    workspaceStore.setCurrent(ws)
  } catch (err) {
    if (err instanceof ApiError && err.isNotFound()) {
      message.error('工作空间不存在或跨工作空间访问被拒绝')
      router.push('/workspace')
      return
    }
    showErrorFromApiError(err)
    // eslint-disable-next-line no-console
    console.error('加载工作空间详情失败', err)
  } finally {
    loading.value = false
  }
}

// ============================================================
// 编辑基本信息
// ============================================================

function openEditModal(): void {
  if (!detail.value) return
  editForm.name = detail.value.name
  editForm.description = detail.value.description ?? ''
  editForm.defaultLocale = detail.value.defaultLocale ?? 'zh-CN'
  editForm.defaultTimezone = detail.value.defaultTimezone ?? 'Asia/Shanghai'
  editModalVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!detail.value) return
  if (!editForm.name.trim()) {
    message.warning('名称不能为空')
    return
  }
  acting.value = true
  try {
    const updated = await workspaceApi.patchWorkspace(workspaceId.value, {
      name: editForm.name.trim(),
      description: editForm.description.trim() || null,
      defaultLocale: editForm.defaultLocale || null,
      defaultTimezone: editForm.defaultTimezone || null,
    }, detail.value.revision)
    detail.value = updated
    workspaceStore.setCurrent(updated)
    showSuccess('工作空间信息已更新')
    editModalVisible.value = false
  } catch (err) {
    handleConflict(err, '更新工作空间失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 状态迁移
// ============================================================

async function doActivate(): Promise<void> {
  if (!detail.value) return
  const ack = await confirm({
    title: '确认激活工作空间',
    content: `激活工作空间"${detail.value.name}"？激活后成员可正常访问。`,
  })
  if (!ack) return
  acting.value = true
  try {
    const updated = await workspaceApi.activateWorkspace(workspaceId.value, detail.value.revision)
    detail.value = updated
    workspaceStore.setCurrent(updated)
    showSuccess('工作空间已激活')
  } catch (err) {
    handleConflict(err, '激活工作空间失败')
  } finally {
    acting.value = false
  }
}

function openSuspendModal(): void {
  pendingAction.value = 'suspend'
  reasonModalTitle.value = '暂停工作空间'
  reasonForm.reason = ''
  reasonModalVisible.value = true
}

function openArchiveModal(): void {
  pendingAction.value = 'archive'
  reasonModalTitle.value = '归档工作空间'
  reasonForm.reason = ''
  reasonModalVisible.value = true
}

async function submitReason(): Promise<void> {
  if (!detail.value || !pendingAction.value) return
  if (!reasonForm.reason.trim()) {
    message.warning('请填写原因')
    return
  }
  acting.value = true
  try {
    if (pendingAction.value === 'archive') {
      // 归档为高风险操作，先二次确认
      const ack = await confirmHighRisk({
        title: '高风险确认：归档工作空间',
        content: `归档工作空间"${detail.value.name}"？归档后所有成员访问将被拒绝，恢复需通过 restore 回到 SUSPENDED 状态。`,
        impactSummary: '所有成员立即失去访问权限；数据保留但不可写入；需管理员手动恢复。',
      })
      if (!ack) {
        reasonModalVisible.value = false
        return
      }
      const updated = await workspaceApi.archiveWorkspace(
        workspaceId.value,
        detail.value.revision,
        reasonForm.reason.trim(),
      )
      detail.value = updated
      workspaceStore.setCurrent(updated)
      showSuccess('工作空间已归档')
    } else {
      const updated = await workspaceApi.suspendWorkspace(
        workspaceId.value,
        detail.value.revision,
        reasonForm.reason.trim(),
      )
      detail.value = updated
      workspaceStore.setCurrent(updated)
      showSuccess('工作空间已暂停')
    }
    reasonModalVisible.value = false
  } catch (err) {
    handleConflict(err, '状态迁移失败')
  } finally {
    acting.value = false
  }
}

async function doRestore(): Promise<void> {
  if (!detail.value) return
  const ack = await confirm({
    title: '确认恢复工作空间',
    content: `恢复工作空间"${detail.value.name}"？将回到 SUSPENDED 状态，需再次激活才能正常使用。`,
  })
  if (!ack) return
  acting.value = true
  try {
    const updated = await workspaceApi.restoreWorkspace(workspaceId.value, detail.value.revision)
    detail.value = updated
    workspaceStore.setCurrent(updated)
    showSuccess('工作空间已恢复至 SUSPENDED 状态')
  } catch (err) {
    handleConflict(err, '恢复工作空间失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 转移负责人
// ============================================================

function openTransferModal(): void {
  transferForm.newOwnerUserId = ''
  transferForm.reason = ''
  transferModalVisible.value = true
}

async function submitTransfer(): Promise<void> {
  if (!detail.value) return
  if (!transferForm.newOwnerUserId.trim()) {
    message.warning('新负责人 ID 不能为空')
    return
  }
  acting.value = true
  try {
    const updated = await workspaceApi.transferWorkspaceOwner(
      workspaceId.value,
      {
        newOwnerUserId: transferForm.newOwnerUserId.trim(),
        reason: transferForm.reason.trim(),
      },
      detail.value.revision,
    )
    detail.value = updated
    workspaceStore.setCurrent(updated)
    showSuccess('工作空间负责人已转移')
    transferModalVisible.value = false
  } catch (err) {
    handleConflict(err, '转移负责人失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 子页面入口
// ============================================================

function navigateTo(sub: string): void {
  router.push(`/workspaces/${workspaceId.value}/${sub}`)
}

// ============================================================
// 显示与错误处理辅助
// ============================================================

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function handleConflict(err: unknown, fallback: string): void {
  if (err instanceof ApiError && err.isConflict()) {
    message.error('版本冲突或状态非法，正在刷新最新数据')
    void loadDetail()
  } else {
    showErrorFromApiError(err)
  }
  // eslint-disable-next-line no-console
  if (!(err instanceof ApiError) || !err.isConflict()) {
    console.error(fallback, err)
  }
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <div class="workspace-detail-view">
    <ATypography>
      <ATypographyTitle :level="3">
        <ASpace align="baseline">
          <AButton
            type="text"
            size="small"
            @click="router.push('/workspace')"
          >
            <template #icon><ArrowLeftOutlined /></template>
          </AButton>
          <span>{{ detail?.name ?? '工作空间详情' }}</span>
          <ATag
            v-if="detail"
            :color="workspaceStatusColor(detail.status)"
          >
            {{ workspaceStatusLabel(detail.status) }}
          </ATag>
        </ASpace>
      </ATypographyTitle>
    </ATypography>

    <ASpin :spinning="loading">
      <ACard :bordered="false" class="detail-card">
        <template #title>
          <ASpace>
            <span>基本信息</span>
            <AButton
              size="small"
              type="text"
              @click="loadDetail"
            >
              <template #icon><ReloadOutlined /></template>
            </AButton>
          </ASpace>
        </template>
        <template #extra>
          <ASpace>
            <AButton
              size="small"
              :disabled="!detail || !canActivateWorkspace(detail.status)"
              @click="doActivate"
            >
              激活
            </AButton>
            <AButton
              size="small"
              :disabled="!detail || !canSuspendWorkspace(detail.status)"
              @click="openSuspendModal"
            >
              暂停
            </AButton>
            <AButton
              size="small"
              danger
              :disabled="!detail || !canArchiveWorkspace(detail.status)"
              @click="openArchiveModal"
            >
              归档
            </AButton>
            <AButton
              size="small"
              :disabled="!detail || !canRestoreWorkspace(detail.status)"
              @click="doRestore"
            >
              恢复
            </AButton>
          </ASpace>
        </template>

        <ADescriptions :column="2" bordered size="middle">
          <ADescriptionsItem label="ID">
            <span class="mono">{{ detail?.id ?? '—' }}</span>
          </ADescriptionsItem>
          <ADescriptionsItem label="Code">
            <span class="mono">{{ detail?.code ?? '—' }}</span>
          </ADescriptionsItem>
          <ADescriptionsItem label="名称">{{ detail?.name ?? '—' }}</ADescriptionsItem>
          <ADescriptionsItem label="状态">
            <ATag
              v-if="detail"
              :color="workspaceStatusColor(detail.status)"
            >
              {{ workspaceStatusLabel(detail.status) }}
            </ATag>
            <span v-else>—</span>
          </ADescriptionsItem>
          <ADescriptionsItem label="负责人 User ID">
            <span class="mono">{{ detail?.ownerUserId ?? '—' }}</span>
          </ADescriptionsItem>
          <ADescriptionsItem label="Revision">{{ detail?.revision ?? '—' }}</ADescriptionsItem>
          <ADescriptionsItem label="默认语言">{{ detail?.defaultLocale ?? '—' }}</ADescriptionsItem>
          <ADescriptionsItem label="默认时区">{{ detail?.defaultTimezone ?? '—' }}</ADescriptionsItem>
          <ADescriptionsItem label="描述" :span="2">
            {{ detail?.description ?? '—' }}
          </ADescriptionsItem>
          <ADescriptionsItem label="创建时间">{{ formatDateTime(detail?.createdAt) }}</ADescriptionsItem>
          <ADescriptionsItem label="更新时间">{{ formatDateTime(detail?.updatedAt) }}</ADescriptionsItem>
        </ADescriptions>

        <ASpace class="action-bar">
          <AButton @click="openEditModal">
            <template #icon><EditOutlined /></template>
            编辑基本信息
          </AButton>
          <AButton @click="openTransferModal">
            <template #icon><SwapOutlined /></template>
            转移负责人
          </AButton>
        </ASpace>
      </ACard>
    </ASpin>

    <!-- 子管理页面入口 -->
    <ACard :bordered="false" class="entry-card" title="工作空间治理">
      <div class="entry-grid">
        <ACard
          size="small"
          hoverable
          class="entry-item"
          @click="navigateTo('organizations')"
        >
          <ASpace direction="vertical" align="center" style="width: 100%">
            <ApartmentOutlined style="font-size: 28px; color: #1677ff" />
            <span>组织树管理</span>
            <ATypographyParagraph type="secondary" style="margin: 0; text-align: center">
              维护工作空间组织层级，支持创建、编辑、移动、停用
            </ATypographyParagraph>
          </ASpace>
        </ACard>
        <ACard
          size="small"
          hoverable
          class="entry-item"
          @click="navigateTo('members')"
        >
          <ASpace direction="vertical" align="center" style="width: 100%">
            <TeamOutlined style="font-size: 28px; color: #1677ff" />
            <span>成员管理</span>
            <ATypographyParagraph type="secondary" style="margin: 0; text-align: center">
              添加、编辑、暂停、移除成员（FR-068 1 分钟撤权）
            </ATypographyParagraph>
          </ASpace>
        </ACard>
        <ACard
          size="small"
          hoverable
          class="entry-item"
          @click="navigateTo('roles')"
        >
          <ASpace direction="vertical" align="center" style="width: 100%">
            <SafetyCertificateOutlined style="font-size: 28px; color: #1677ff" />
            <span>角色管理</span>
            <ATypographyParagraph type="secondary" style="margin: 0; text-align: center">
              自定义角色、权限键集合与数据范围类型
            </ATypographyParagraph>
          </ASpace>
        </ACard>
        <ACard
          size="small"
          hoverable
          class="entry-item"
          @click="navigateTo('data-scopes')"
        >
          <ASpace direction="vertical" align="center" style="width: 100%">
            <FilterOutlined style="font-size: 28px; color: #1677ff" />
            <span>数据范围</span>
            <ATypographyParagraph type="secondary" style="margin: 0; text-align: center">
              定义行级数据可见性规则（FR-063）
            </ATypographyParagraph>
          </ASpace>
        </ACard>
        <ACard
          size="small"
          hoverable
          class="entry-item"
          @click="navigateTo('collaboration-grants')"
        >
          <ASpace direction="vertical" align="center" style="width: 100%">
            <ShareAltOutlined style="font-size: 28px; color: #1677ff" />
            <span>协作授权</span>
            <ATypographyParagraph type="secondary" style="margin: 0; text-align: center">
              跨工作空间协作权限授予与撤销
            </ATypographyParagraph>
          </ASpace>
        </ACard>
      </div>
    </ACard>

    <!-- 编辑基本信息模态框 -->
    <AModal
      v-model:open="editModalVisible"
      title="编辑工作空间基本信息"
      :confirm-loading="acting"
      ok-text="保存"
      cancel-text="取消"
      width="640px"
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
        <ASpace style="width: 100%" align="start">
          <AFormItem label="默认语言" style="flex: 1">
            <ASelect v-model:value="editForm.defaultLocale">
              <ASelectOption value="zh-CN">简体中文</ASelectOption>
              <ASelectOption value="en-US">English (US)</ASelectOption>
              <ASelectOption value="ja-JP">日本語</ASelectOption>
            </ASelect>
          </AFormItem>
          <AFormItem label="默认时区" style="flex: 1">
            <ASelect v-model:value="editForm.defaultTimezone">
              <ASelectOption value="Asia/Shanghai">Asia/Shanghai</ASelectOption>
              <ASelectOption value="Asia/Tokyo">Asia/Tokyo</ASelectOption>
              <ASelectOption value="America/Los_Angeles">America/Los_Angeles</ASelectOption>
              <ASelectOption value="UTC">UTC</ASelectOption>
            </ASelect>
          </AFormItem>
        </ASpace>
      </AForm>
    </AModal>

    <!-- 暂停/归档原因模态框 -->
    <AModal
      v-model:open="reasonModalVisible"
      :title="reasonModalTitle"
      :confirm-loading="acting"
      ok-text="确认"
      cancel-text="取消"
      @ok="submitReason"
    >
      <AForm layout="vertical">
        <AFormItem label="原因" required>
          <AInput
            v-model:value="reasonForm.reason"
            type="textarea"
            :rows="3"
            :max-length="512"
            placeholder="请填写操作原因（审计记录）"
          />
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 转移负责人模态框 -->
    <AModal
      v-model:open="transferModalVisible"
      title="转移工作空间负责人"
      :confirm-loading="acting"
      ok-text="确认转移"
      cancel-text="取消"
      width="560px"
      @ok="submitTransfer"
    >
      <AForm layout="vertical">
        <AFormItem label="新负责人 User ID" required>
          <AInput
            v-model:value="transferForm.newOwnerUserId"
            placeholder="UUID 格式的用户 ID"
          />
        </AFormItem>
        <AFormItem label="转移原因" required>
          <AInput
            v-model:value="transferForm.reason"
            type="textarea"
            :rows="3"
            :max-length="512"
          />
        </AFormItem>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.workspace-detail-view {
  padding: 24px;

  .detail-card {
    margin-bottom: 16px;

    .action-bar {
      margin-top: 16px;
    }
  }

  .entry-card {
    .entry-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 12px;
    }

    .entry-item {
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        border-color: #1677ff;
        box-shadow: 0 2px 8px rgba(22, 119, 255, 0.15);
      }
    }
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }
}
</style>

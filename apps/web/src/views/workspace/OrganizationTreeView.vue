<script setup lang="ts">
/**
 * 组织树管理页面（T108、FR-004）。
 *
 * 后端按 parentId 分页查询组织，前端构建树形结构展示。
 *
 * <p><strong>核心操作</strong>：
 * - 创建组织：POST /organizations + Idempotency-Key，支持顶层或子组织；
 * - 编辑组织：PATCH /organizations/{id} + If-Match；
 * - 移动组织：POST /organizations/{id}/move + If-Match + Idempotency-Key，循环依赖检测（409）；
 * - 停用组织：DELETE /organizations/{id} + If-Match（软删除）。
 *
 * <p><strong>懒加载</strong>：
 * 后端按 parentId 分页，前端维护扁平列表，展开节点时懒加载子节点并合并。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Button as AButton,
  Card as ACard,
  Descriptions as ADescriptions,
  DescriptionsItem as ADescriptionsItem,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  Modal as AModal,
  Select as ASelect,
  SelectOption as ASelectOption,
  Space as ASpace,
  Spin as ASpin,
  Tag as ATag,
  Tree as ATree,
  Typography as ATypography,
  TypographyParagraph as ATypographyParagraph,
  TypographyTitle as ATypographyTitle,
  message,
} from 'ant-design-vue'
import type { TreeProps } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { workspaceApi } from './api'
import {
  organizationStatusColor,
  organizationStatusLabel,
  type Organization,
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
// 扁平列表与树构建
// ============================================================

const loading = ref(false)
const acting = ref(false)
const organizations = ref<Organization[]>([])
const selectedKey = ref<string | null>(null)
const selected = ref<Organization | null>(null)

/** 扁平列表 → 树形结构（基于 parentId）。 */
const treeData = computed<TreeProps['treeData']>(() => {
  const map = new Map<string, Organization & { children?: Organization[] }>()
  const roots: Organization[] = []

  // 第一遍：建立映射
  for (const org of organizations.value) {
    map.set(org.id, { ...org, children: undefined })
  }

  // 第二遍：构建父子关系
  for (const org of organizations.value) {
    const node = map.get(org.id)!
    if (org.parentId && map.has(org.parentId)) {
      const parent = map.get(org.parentId)!
      if (!parent.children) parent.children = []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }

  return roots.map(toTreeNode)
})

/** Organization → ATree node 转换。 */
function toTreeNode(org: Organization): NonNullable<TreeProps['treeData']>[number] {
  return {
    key: org.id,
    title: `${org.name} (${org.code})`,
    children: (org as Organization & { children?: Organization[] }).children?.map(toTreeNode),
    isLeaf: false, // 懒加载：始终允许展开
  }
}

// ============================================================
// 创建组织
// ============================================================

const createModalVisible = ref(false)
const createForm = reactive({
  code: '',
  name: '',
  description: '',
  parentId: '' as string | null,
})

/** 可选父组织列表（排除已停用）。 */
const parentOptions = computed(() => {
  return organizations.value.filter((o) => o.status === 'ACTIVE')
})

// ============================================================
// 编辑组织
// ============================================================

const editModalVisible = ref(false)
const editForm = reactive({
  name: '',
  description: '',
})

// ============================================================
// 移动组织
// ============================================================

const moveModalVisible = ref(false)
const moveForm = reactive({
  newParentId: '' as string | null,
})

/** 移动时的可选父组织（排除自身与子树，基于 path 前缀）。 */
function moveParentOptions(org: Organization): Organization[] {
  return organizations.value.filter((o) => {
    if (o.id === org.id) return false
    // 排除自身子树（path 前缀匹配）
    if (o.path.startsWith(org.path)) return false
    return o.status === 'ACTIVE'
  })
}

// ============================================================
// 数据加载
// ============================================================

async function loadRoots(): Promise<void> {
  loading.value = true
  try {
    const result = await workspaceApi.listOrganizations(workspaceId.value, {
      parentId: null,
      pageSize: 200,
    })
    organizations.value = result.data
    if (result.data.length > 0 && !selected.value) {
      selectOrganization(result.data[0])
    }
  } catch (err) {
    handleError(err, '加载组织树失败')
  } finally {
    loading.value = false
  }
}

/** 展开节点时懒加载子节点。 */
async function onLoadChildren(treeKey: string | number): Promise<void> {
  const id = String(treeKey)
  // 已加载过则跳过
  const hasChildren = organizations.value.some(
    (o) => o.parentId === id,
  )
  if (hasChildren) return
  try {
    const result = await workspaceApi.listOrganizations(workspaceId.value, {
      parentId: id,
      pageSize: 200,
    })
    if (result.data.length > 0) {
      organizations.value = organizations.value.concat(result.data)
    }
  } catch (err) {
    handleError(err, '加载子组织失败')
  }
}

function selectOrganization(org: Organization): void {
  selected.value = org
  selectedKey.value = org.id
}

function onSelect(keys: (string | number)[]): void {
  if (keys.length === 0) return
  const id = String(keys[0])
  const org = organizations.value.find((o) => o.id === id)
  if (org) selectOrganization(org)
}

// ============================================================
// 创建组织
// ============================================================

function openCreateModal(parentId?: string | null): void {
  createForm.code = ''
  createForm.name = ''
  createForm.description = ''
  createForm.parentId = parentId ?? null
  createModalVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.code.trim() || !createForm.name.trim()) {
    message.warning('Code 和名称不能为空')
    return
  }
  acting.value = true
  try {
    const created = await workspaceApi.createOrganization(workspaceId.value, {
      code: createForm.code.trim(),
      name: createForm.name.trim(),
      description: createForm.description.trim() || null,
      parentId: createForm.parentId || null,
    })
    organizations.value.push(created)
    selectOrganization(created)
    showSuccess('组织已创建')
    createModalVisible.value = false
  } catch (err) {
    handleError(err, '创建组织失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 编辑组织
// ============================================================

function openEditModal(): void {
  if (!selected.value) return
  editForm.name = selected.value.name
  editForm.description = selected.value.description ?? ''
  editModalVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!selected.value) return
  if (!editForm.name.trim()) {
    message.warning('名称不能为空')
    return
  }
  acting.value = true
  try {
    const updated = await workspaceApi.updateOrganization(
      workspaceId.value,
      selected.value.id,
      {
        name: editForm.name.trim(),
        description: editForm.description.trim() || null,
      },
      selected.value.revision,
    )
    replaceInList(updated)
    selectOrganization(updated)
    showSuccess('组织已更新')
    editModalVisible.value = false
  } catch (err) {
    handleConflict(err, '更新组织失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 移动组织
// ============================================================

function openMoveModal(): void {
  if (!selected.value) return
  moveForm.newParentId = null
  moveModalVisible.value = true
}

async function submitMove(): Promise<void> {
  if (!selected.value) return
  acting.value = true
  try {
    const updated = await workspaceApi.moveOrganization(
      workspaceId.value,
      selected.value.id,
      { newParentId: moveForm.newParentId || null },
      selected.value.revision,
    )
    replaceInList(updated)
    selectOrganization(updated)
    showSuccess('组织层级已调整')
    moveModalVisible.value = false
    // 重新加载以刷新 path 与子树
    await loadRoots()
  } catch (err) {
    handleConflict(err, '移动组织失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 停用组织
// ============================================================

async function deactivateOrg(): Promise<void> {
  if (!selected.value) return
  const ack = await confirm({
    title: '确认停用组织',
    content: `停用组织"${selected.value.name}"？停用后该组织及其子组织下的成员将失去对应归属。`,
    danger: true,
  })
  if (!ack) return
  acting.value = true
  try {
    await workspaceApi.deactivateOrganization(
      workspaceId.value,
      selected.value.id,
      selected.value.revision,
    )
    organizations.value = organizations.value.filter((o) => o.id !== selected.value!.id)
    selected.value = null
    selectedKey.value = null
    showSuccess('组织已停用')
  } catch (err) {
    handleConflict(err, '停用组织失败')
  } finally {
    acting.value = false
  }
}

// ============================================================
// 辅助方法
// ============================================================

function replaceInList(updated: Organization): void {
  const idx = organizations.value.findIndex((o) => o.id === updated.id)
  if (idx >= 0) organizations.value[idx] = updated
}

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function handleConflict(err: unknown, fallback: string): void {
  if (err instanceof ApiError && err.isConflict()) {
    message.error('版本冲突或循环依赖，正在刷新最新数据')
    void loadRoots()
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
  void loadRoots()
})
</script>

<template>
  <div class="organization-tree-view">
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
          <span>组织树管理</span>
        </ASpace>
      </ATypographyTitle>
      <ATypographyParagraph type="secondary">
        维护工作空间组织层级。支持创建顶层或子组织、编辑、移动层级与停用。
        移动时后端会检测循环依赖（返回 409）。
      </ATypographyParagraph>
    </ATypography>

    <ACard :bordered="false" class="action-card">
      <ASpace>
        <AButton @click="loadRoots">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
        <AButton type="primary" ghost @click="openCreateModal(null)">
          <template #icon><PlusOutlined /></template>
          创建顶层组织
        </AButton>
        <AButton
          ghost
          :disabled="!selected"
          @click="openCreateModal(selected?.id ?? null)"
        >
          <template #icon><PlusOutlined /></template>
          创建子组织
        </AButton>
      </ASpace>
    </ACard>

    <div class="content-layout">
      <!-- 左侧：组织树 -->
      <ACard :bordered="false" class="tree-card" title="组织树">
        <ASpin :spinning="loading">
          <AEmpty
            v-if="!treeData || treeData.length === 0"
            description="暂无组织，点击右上角按钮创建"
          />
          <ATree
            v-else
            :tree-data="treeData"
            :selected-keys="selectedKey ? [selectedKey] : []"
            :load-data="(treeNode: any) => onLoadChildren(treeNode.eventKey ?? treeNode.key)"
            :show-line="true"
            block-node
            @select="onSelect"
          />
        </ASpin>
      </ACard>

      <!-- 右侧：详情与操作 -->
      <ACard :bordered="false" class="detail-card" title="组织详情">
        <AEmpty
          v-if="!selected"
          description="请在左侧选择一个组织节点"
        />
        <template v-else>
          <ADescriptions :column="1" bordered size="middle">
            <ADescriptionsItem label="ID">
              <span class="mono">{{ selected.id }}</span>
            </ADescriptionsItem>
            <ADescriptionsItem label="Code">
              <span class="mono">{{ selected.code }}</span>
            </ADescriptionsItem>
            <ADescriptionsItem label="名称">{{ selected.name }}</ADescriptionsItem>
            <ADescriptionsItem label="状态">
              <ATag :color="organizationStatusColor(selected.status)">
                {{ organizationStatusLabel(selected.status) }}
              </ATag>
            </ADescriptionsItem>
            <ADescriptionsItem label="路径">
              <span class="mono">{{ selected.path }}</span>
            </ADescriptionsItem>
            <ADescriptionsItem label="层级深度">{{ selected.depth }}</ADescriptionsItem>
            <ADescriptionsItem label="父组织 ID">
              <span class="mono">{{ selected.parentId ?? '—（顶层）' }}</span>
            </ADescriptionsItem>
            <ADescriptionsItem label="描述">{{ selected.description ?? '—' }}</ADescriptionsItem>
            <ADescriptionsItem label="Revision">{{ selected.revision }}</ADescriptionsItem>
            <ADescriptionsItem label="创建时间">{{ formatDateTime(selected.createdAt) }}</ADescriptionsItem>
            <ADescriptionsItem label="更新时间">{{ formatDateTime(selected.updatedAt) }}</ADescriptionsItem>
          </ADescriptions>

          <ASpace class="action-bar">
            <AButton
              size="small"
              :disabled="selected.status !== 'ACTIVE'"
              @click="openEditModal"
            >
              <template #icon><EditOutlined /></template>
              编辑
            </AButton>
            <AButton
              size="small"
              :disabled="selected.status !== 'ACTIVE'"
              @click="openMoveModal"
            >
              移动层级
            </AButton>
            <AButton
              size="small"
              danger
              :disabled="selected.status !== 'ACTIVE'"
              :loading="acting"
              @click="deactivateOrg"
            >
              <template #icon><DeleteOutlined /></template>
              停用
            </AButton>
          </ASpace>
        </template>
      </ACard>
    </div>

    <!-- 创建组织模态框 -->
    <AModal
      v-model:open="createModalVisible"
      title="创建组织"
      :confirm-loading="acting"
      ok-text="创建"
      cancel-text="取消"
      width="560px"
      @ok="submitCreate"
    >
      <AForm layout="vertical">
        <AFormItem label="Code" required>
          <AInput
            v-model:value="createForm.code"
            placeholder="工作空间内唯一"
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
        <AFormItem label="父组织">
          <ASelect
            v-model:value="createForm.parentId"
            allow-clear
            placeholder="留空表示顶层组织"
          >
            <ASelectOption
              v-for="org in parentOptions"
              :key="org.id"
              :value="org.id"
            >
              {{ org.name }} ({{ org.code }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
      </AForm>
    </AModal>

    <!-- 编辑组织模态框 -->
    <AModal
      v-model:open="editModalVisible"
      title="编辑组织"
      :confirm-loading="acting"
      ok-text="保存"
      cancel-text="取消"
      width="560px"
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
      </AForm>
    </AModal>

    <!-- 移动组织模态框 -->
    <AModal
      v-model:open="moveModalVisible"
      title="调整组织层级"
      :confirm-loading="acting"
      ok-text="确认移动"
      cancel-text="取消"
      width="560px"
      @ok="submitMove"
    >
      <AForm layout="vertical">
        <AFormItem label="新父组织">
          <ASelect
            v-model:value="moveForm.newParentId"
            allow-clear
            placeholder="留空表示移动到顶层"
          >
            <ASelectOption
              v-for="org in (selected ? moveParentOptions(selected) : [])"
              :key="org.id"
              :value="org.id"
            >
              {{ org.name }} ({{ org.code }})
            </ASelectOption>
          </ASelect>
        </AFormItem>
      </AForm>
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.organization-tree-view {
  padding: 24px;

  .action-card {
    margin-bottom: 16px;
  }

  .content-layout {
    display: grid;
    grid-template-columns: 1fr 1.4fr;
    gap: 16px;

    @media (max-width: 1100px) {
      grid-template-columns: 1fr;
    }
  }

  .tree-card,
  .detail-card {
    .action-bar {
      margin-top: 16px;
    }
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }
}
</style>

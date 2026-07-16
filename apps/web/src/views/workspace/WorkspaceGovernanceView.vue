<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiProblemError } from '../../api/client'
import { SchemaForm, SchemaTable } from '../../components/schema'
import AppLayout from '../../layouts/AppLayout.vue'
import FeedbackBanner from '../../layouts/FeedbackBanner.vue'
import { usePlatformStore } from '../../stores'
import {
  grantFormSchema,
  grantTableSchema,
  memberFormSchema,
  memberTableSchema,
  organizationFormSchema,
  organizationTableSchema,
  roleFormSchema,
  roleTableSchema,
} from './schemas'
import type {
  Workspace,
  WorkspaceGovernanceApi,
  WorkspaceGovernanceSnapshot,
} from './types'
import { workspaceGovernanceApi } from './workspaceApi'

type GovernanceTab = 'organizations' | 'members' | 'roles' | 'grants'

const props = defineProps<{
  governanceApi?: WorkspaceGovernanceApi
}>()

const service = props.governanceApi ?? workspaceGovernanceApi
const platformStore = usePlatformStore()
const workspaces = ref<Workspace[]>([])
const snapshot = ref<WorkspaceGovernanceSnapshot>({
  organizations: [],
  members: [],
  roles: [],
  grants: [],
})
const activeTab = ref<GovernanceTab>('organizations')
const loading = ref(false)
const saving = ref(false)
const feedback = ref<{
  type: 'success' | 'error' | 'warning'
  title: string
  detail?: string
  traceId?: string
} | null>(null)

const organizationForm = ref<Record<string, unknown>>({ type: 'DEPARTMENT' })
const memberForm = ref<Record<string, unknown>>({ membershipType: 'INTERNAL' })
const roleForm = ref<Record<string, unknown>>({})
const grantForm = ref<Record<string, unknown>>({})

const currentWorkspace = computed(() =>
  workspaces.value.find((workspace) => workspace.id === platformStore.workspaceId),
)

const organizationRows = computed(() =>
  snapshot.value.organizations.map((item) => ({ ...item })),
)
const memberRows = computed(() =>
  snapshot.value.members.map((item) => ({
    ...item,
    roleIds: item.roleIds.join('、'),
    validUntil: item.validUntil ?? '长期有效',
  })),
)
const roleRows = computed(() =>
  snapshot.value.roles.map((item) => ({
    ...item,
    allowedActions: item.allowedActions.join('、'),
  })),
)
const grantRows = computed(() =>
  snapshot.value.grants.map((item) => ({
    ...item,
    targetLabel: `${item.target.objectType} / ${item.target.objectId}`,
    allowedActions: item.allowedActions.join('、'),
  })),
)

const tabs: Array<{ key: GovernanceTab; label: string; description: string }> = [
  { key: 'organizations', label: '组织', description: '维护组织层级与区域归属' },
  { key: 'members', label: '成员', description: '分配成员、角色和数据范围' },
  { key: 'roles', label: '角色', description: '用稳定动作集合定义最小授权' },
  { key: 'grants', label: '跨空间授权', description: '限时、可撤销地开放指定对象' },
]

onMounted(loadWorkspaces)

watch(
  () => platformStore.workspaceId,
  async (workspaceId) => {
    if (workspaceId) await loadGovernance(workspaceId)
  },
)

async function loadWorkspaces() {
  loading.value = true
  feedback.value = null
  try {
    workspaces.value = await service.listWorkspaces()
    const selected = workspaces.value.some(
      (workspace) => workspace.id === platformStore.workspaceId,
    )
      ? platformStore.workspaceId
      : workspaces.value[0]?.id
    if (selected) {
      if (selected === platformStore.workspaceId) await loadGovernance(selected)
      else platformStore.selectWorkspace(selected)
    } else {
      feedback.value = {
        type: 'warning',
        title: '暂无可访问工作空间',
        detail: '请联系平台管理员完成成员或负责人授权。',
      }
    }
  } catch (error) {
    showError(error, '工作空间加载失败')
  } finally {
    loading.value = false
  }
}

async function loadGovernance(workspaceId: string) {
  loading.value = true
  feedback.value = null
  try {
    snapshot.value = await service.loadGovernance(workspaceId)
  } catch (error) {
    showError(error, '治理数据加载失败')
  } finally {
    loading.value = false
  }
}

async function submitCurrentForm() {
  const workspaceId = platformStore.workspaceId
  if (!workspaceId) return
  saving.value = true
  feedback.value = null
  try {
    if (activeTab.value === 'organizations') {
      await service.createOrganization(workspaceId, compact(organizationForm.value))
      organizationForm.value = { type: 'DEPARTMENT' }
    } else if (activeTab.value === 'members') {
      await service.addMember(workspaceId, {
        ...compact(memberForm.value),
        roleIds: commaList(memberForm.value.roleIds),
        dataScopeIds: commaList(memberForm.value.dataScopeIds),
      })
      memberForm.value = { membershipType: 'INTERNAL' }
    } else if (activeTab.value === 'roles') {
      await service.createRole(workspaceId, {
        ...compact(roleForm.value),
        allowedActions: commaList(roleForm.value.allowedActions),
      })
      roleForm.value = {}
    } else {
      await service.createGrant(workspaceId, {
        collaboratorWorkspaceId: grantForm.value.collaboratorWorkspaceId,
        target: {
          objectType: grantForm.value.objectType,
          objectId: grantForm.value.objectId,
        },
        roleId: grantForm.value.roleId,
        allowedActions: commaList(grantForm.value.allowedActions),
        validUntil: grantForm.value.validUntil,
        reason: grantForm.value.reason,
      })
      grantForm.value = {}
    }
    await loadGovernance(workspaceId)
    feedback.value = { type: 'success', title: '治理配置已保存并立即生效' }
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function revokeGrant(grantId: string, revision: number) {
  const workspaceId = platformStore.workspaceId
  if (!workspaceId) return
  const reason = globalThis.prompt?.('请输入撤销原因（至少 2 个字符）', '协作已结束')
  if (!reason) return
  saving.value = true
  try {
    await service.revokeGrant(workspaceId, grantId, revision, reason)
    await loadGovernance(workspaceId)
    feedback.value = { type: 'success', title: '跨空间授权已撤销' }
  } catch (error) {
    showError(error, '撤销失败')
  } finally {
    saving.value = false
  }
}

function showError(error: unknown, title: string) {
  if (error instanceof ApiProblemError) {
    feedback.value = {
      type: 'error',
      title,
      detail: error.message,
      traceId: error.traceId,
    }
  } else {
    feedback.value = {
      type: 'error',
      title,
      detail: error instanceof Error ? error.message : '发生未知错误',
    }
  }
}

function compact(value: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => item !== '' && item !== undefined),
  )
}

function commaList(value: unknown): string[] {
  return String(value ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}
</script>

<template>
  <AppLayout title="工作空间与组织治理">
    <template #navigation>
      <p class="workspace-navigation__brand">PDP</p>
      <RouterLink class="workspace-navigation__link" to="/">平台首页</RouterLink>
      <p class="workspace-navigation__section">治理中心</p>
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="workspace-navigation__tab"
        :class="{ 'workspace-navigation__tab--active': activeTab === tab.key }"
        type="button"
        @click="activeTab = tab.key"
      >
        <strong>{{ tab.label }}</strong>
        <span>{{ tab.description }}</span>
      </button>
    </template>

    <template #eyebrow>
      <p class="eyebrow">平台治理 · US1</p>
    </template>

    <template #actions>
      <label class="workspace-selector">
        <span>当前工作空间</span>
        <select
          data-test="workspace-select"
          :value="platformStore.workspaceId ?? ''"
          :disabled="loading || workspaces.length === 0"
          @change="
            platformStore.selectWorkspace(
              ($event.target as HTMLSelectElement).value,
            )
          "
        >
          <option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">
            {{ workspace.name }}（{{ workspace.code }}）
          </option>
        </select>
      </label>
    </template>

    <div class="workspace-summary">
      <div>
        <span>空间状态</span>
        <strong>{{ currentWorkspace?.status ?? '—' }}</strong>
      </div>
      <div>
        <span>组织单元</span>
        <strong>{{ snapshot.organizations.length }}</strong>
      </div>
      <div>
        <span>有效成员</span>
        <strong>{{ snapshot.members.filter((item) => item.status === 'ACTIVE').length }}</strong>
      </div>
      <div>
        <span>活动授权</span>
        <strong>{{ snapshot.grants.filter((item) => item.status === 'ACTIVE').length }}</strong>
      </div>
    </div>

    <FeedbackBanner
      v-if="feedback"
      :type="feedback.type"
      :title="feedback.title"
      :detail="feedback.detail"
      :trace-id="feedback.traceId"
    />

    <p v-if="loading" class="workspace-loading" role="status">正在加载治理数据…</p>

    <section v-else class="workspace-panel">
      <div class="workspace-panel__list">
        <header>
          <div>
            <p class="eyebrow">当前配置</p>
            <h2>{{ tabs.find((tab) => tab.key === activeTab)?.label }}</h2>
          </div>
          <button type="button" class="secondary-button" @click="loadWorkspaces">
            刷新
          </button>
        </header>

        <SchemaTable
          v-if="activeTab === 'organizations'"
          :schema="organizationTableSchema"
          :rows="organizationRows"
        />
        <SchemaTable
          v-else-if="activeTab === 'members'"
          :schema="memberTableSchema"
          :rows="memberRows"
        />
        <SchemaTable
          v-else-if="activeTab === 'roles'"
          :schema="roleTableSchema"
          :rows="roleRows"
        />
        <div v-else class="grant-list">
          <SchemaTable :schema="grantTableSchema" :rows="grantRows" />
          <ul>
            <li v-for="grant in snapshot.grants" :key="grant.id">
              <span>{{ grant.target.objectType }} / {{ grant.target.objectId }}</span>
              <button
                type="button"
                :disabled="saving || grant.status !== 'ACTIVE'"
                @click="revokeGrant(grant.id, grant.revision)"
              >
                撤销授权
              </button>
            </li>
          </ul>
        </div>
      </div>

      <form class="workspace-panel__form" @submit.prevent="submitCurrentForm">
        <SchemaForm
          v-if="activeTab === 'organizations'"
          v-model="organizationForm"
          :schema="organizationFormSchema"
          :disabled="saving"
        />
        <SchemaForm
          v-else-if="activeTab === 'members'"
          v-model="memberForm"
          :schema="memberFormSchema"
          :disabled="saving"
        />
        <SchemaForm
          v-else-if="activeTab === 'roles'"
          v-model="roleForm"
          :schema="roleFormSchema"
          :disabled="saving"
        />
        <SchemaForm
          v-else
          v-model="grantForm"
          :schema="grantFormSchema"
          :disabled="saving"
        />
        <p class="workspace-panel__hint">
          保存前会按当前工作空间复核权限；跨空间授权必须包含对象范围、动作和期限。
        </p>
        <button class="primary-button" type="submit" :disabled="saving">
          {{ saving ? '正在保存…' : '保存配置' }}
        </button>
      </form>
    </section>
  </AppLayout>
</template>

<style scoped>
.workspace-navigation__brand {
  margin: 0 0 var(--pdp-space-8);
  color: var(--pdp-color-brand);
  font-size: 28px;
  font-weight: 800;
}

.workspace-navigation__link {
  color: var(--pdp-color-text);
}

.workspace-navigation__section {
  margin: var(--pdp-space-8) 0 var(--pdp-space-2);
  color: var(--pdp-color-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workspace-navigation__tab {
  width: 100%;
  display: grid;
  gap: var(--pdp-space-1);
  margin-bottom: var(--pdp-space-2);
  padding: var(--pdp-space-3);
  border: 0;
  border-radius: var(--pdp-radius-sm);
  color: var(--pdp-color-text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.workspace-navigation__tab span {
  color: var(--pdp-color-muted);
  font-size: 12px;
}

.workspace-navigation__tab--active {
  color: var(--pdp-color-brand-strong);
  background: color-mix(in srgb, var(--pdp-color-brand) 10%, white);
}

.workspace-selector {
  display: grid;
  gap: var(--pdp-space-1);
  min-width: 280px;
  color: var(--pdp-color-muted);
  font-size: 12px;
}

.workspace-selector select {
  min-height: 40px;
  padding: 0 var(--pdp-space-3);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-sm);
  color: var(--pdp-color-text);
  background: var(--pdp-color-surface);
}

.workspace-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--pdp-space-4);
  margin-bottom: var(--pdp-space-6);
}

.workspace-summary div {
  display: grid;
  gap: var(--pdp-space-2);
  padding: var(--pdp-space-4);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-md);
  background: var(--pdp-color-surface);
}

.workspace-summary span,
.workspace-panel__hint {
  color: var(--pdp-color-muted);
  font-size: 13px;
}

.workspace-summary strong {
  font-size: 24px;
}

.workspace-loading {
  padding: var(--pdp-space-8);
  color: var(--pdp-color-muted);
  text-align: center;
}

.workspace-panel {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: var(--pdp-space-6);
  margin-top: var(--pdp-space-6);
}

.workspace-panel__list,
.workspace-panel__form {
  padding: var(--pdp-space-6);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-md);
  background: var(--pdp-color-surface);
}

.workspace-panel__list > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--pdp-space-4);
}

.workspace-panel__list h2 {
  margin: 0;
}

.workspace-panel__form {
  align-self: start;
}

.primary-button,
.secondary-button,
.grant-list button {
  min-height: 40px;
  padding: 0 var(--pdp-space-4);
  border: 1px solid var(--pdp-color-brand);
  border-radius: var(--pdp-radius-sm);
  cursor: pointer;
}

.primary-button {
  width: 100%;
  color: white;
  background: var(--pdp-color-brand);
}

.secondary-button,
.grant-list button {
  color: var(--pdp-color-brand-strong);
  background: white;
}

.primary-button:disabled,
.grant-list button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.grant-list ul {
  display: grid;
  gap: var(--pdp-space-2);
  padding: 0;
  list-style: none;
}

.grant-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pdp-space-4);
  padding-top: var(--pdp-space-2);
  border-top: 1px solid var(--pdp-color-border);
}

@media (width <= 980px) {
  .workspace-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-panel {
    grid-template-columns: 1fr;
  }
}

@media (width <= 600px) {
  .workspace-summary {
    grid-template-columns: 1fr;
  }

  .workspace-selector {
    min-width: 0;
  }
}
</style>

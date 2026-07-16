<script setup lang="ts">
import { computed, ref } from 'vue'

import { ApiProblemError } from '../../../api/client'
import { SchemaTable } from '../../../components/schema'
import AppLayout from '../../../layouts/AppLayout.vue'
import FeedbackBanner from '../../../layouts/FeedbackBanner.vue'
import { incidentSchema } from './schemas'
import type {
  WorkflowAdministrationApi,
  WorkflowDefinitionSummary,
  WorkflowInstanceSummary,
  WorkflowValidation,
} from './types'
import { workflowAdministrationApi } from './workflowApi'

const props = defineProps<{
  administrationApi?: WorkflowAdministrationApi
}>()

const service = props.administrationApi ?? workflowAdministrationApi
const activePanel = ref<'definition' | 'instance'>('definition')
const busy = ref(false)
const feedback = ref<{
  type: 'success' | 'error' | 'warning'
  title: string
  detail?: string
  traceId?: string
} | null>(null)

const definition = ref({
  processDefinitionKey: 'approval.flow',
  businessVersion: '1.0.0',
  domainPackageVersionId: '',
  bpmnXml: `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:pdp">
  <process id="approval.flow" isExecutable="true">
    <startEvent id="start"/>
    <endEvent id="end"/>
    <sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
  </process>
</definitions>`,
})
const validation = ref<WorkflowValidation | null>(null)
const deployed = ref<WorkflowDefinitionSummary | null>(null)

const instanceId = ref('')
const instance = ref<WorkflowInstanceSummary | null>(null)
const action = ref({
  action: 'PAUSE',
  reason: '',
  targetDefinitionId: '',
  impactPreviewId: '',
})

const canDeploy = computed(
  () =>
    validation.value?.valid === true &&
    validation.value.contentHash.length > 0,
)
const deadLetterCount = computed(
  () =>
    instance.value?.incidents?.filter((item) => item.status === 'DEAD_LETTER')
      .length ?? 0,
)

async function validateDefinition() {
  busy.value = true
  feedback.value = null
  try {
    validation.value = await service.validate({
      processDefinitionKey: definition.value.processDefinitionKey,
      businessVersion: definition.value.businessVersion,
      domainPackageVersionId: definition.value.domainPackageVersionId || null,
      bpmnXml: definition.value.bpmnXml,
    })
    feedback.value = validation.value.valid
      ? { type: 'success', title: 'BPMN 定义校验通过' }
      : {
          type: 'warning',
          title: 'BPMN 定义存在阻断项',
          detail: '请修复 ERROR 级发现后重新校验。',
        }
  } catch (error) {
    showError(error, '流程定义校验失败')
  } finally {
    busy.value = false
  }
}

async function deployDefinition() {
  if (!validation.value?.valid) return
  busy.value = true
  feedback.value = null
  try {
    deployed.value = await service.deploy(
      {
        processDefinitionKey: definition.value.processDefinitionKey,
        businessVersion: definition.value.businessVersion,
        domainPackageVersionId: definition.value.domainPackageVersionId || null,
        contentHash: validation.value.contentHash,
        bpmnResource: definition.value.bpmnXml,
      },
      `workflow-deploy-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`,
    )
    feedback.value = {
      type: 'success',
      title: `流程版本 ${deployed.value.businessVersion} 已部署`,
    }
  } catch (error) {
    showError(error, '流程部署失败')
  } finally {
    busy.value = false
  }
}

async function queryInstance() {
  if (!instanceId.value.trim()) return
  busy.value = true
  feedback.value = null
  try {
    instance.value = await service.getInstance(instanceId.value.trim())
    action.value.reason = ''
  } catch (error) {
    instance.value = null
    showError(error, '流程实例查询失败')
  } finally {
    busy.value = false
  }
}

async function submitAction() {
  if (!instance.value) return
  busy.value = true
  feedback.value = null
  try {
    const accepted = await service.applyAction(
      instance.value.id,
      {
        action: action.value.action,
        reason: action.value.reason,
        expectedRevision: instance.value.revision,
        targetDefinitionId: action.value.targetDefinitionId || null,
        impactPreviewId: action.value.impactPreviewId || null,
      },
      `workflow-action-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`,
    )
    feedback.value = {
      type: 'success',
      title: '管理动作已进入队列',
      detail: `作业 ${accepted.jobId}，状态 ${accepted.status}`,
    }
    await queryInstance()
  } catch (error) {
    showError(error, '管理动作提交失败')
  } finally {
    busy.value = false
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
</script>

<template>
  <AppLayout title="平台工作流管理">
    <template #navigation>
      <p class="workflow-navigation__brand">PDP</p>
      <RouterLink class="workflow-navigation__link" to="/">平台首页</RouterLink>
      <RouterLink class="workflow-navigation__link" to="/workspaces">
        工作空间治理
      </RouterLink>
      <p class="workflow-navigation__section">工作流管理</p>
      <button
        type="button"
        :class="{ active: activePanel === 'definition' }"
        @click="activePanel = 'definition'"
      >
        定义与部署
      </button>
      <button
        type="button"
        :class="{ active: activePanel === 'instance' }"
        @click="activePanel = 'instance'"
      >
        实例与故障
      </button>
    </template>

    <template #eyebrow>
      <p class="eyebrow">平台基础能力 · Flowable</p>
    </template>

    <FeedbackBanner
      v-if="feedback"
      :type="feedback.type"
      :title="feedback.title"
      :detail="feedback.detail"
      :trace-id="feedback.traceId"
    />

    <section v-if="activePanel === 'definition'" class="workflow-grid">
      <form class="workflow-card" @submit.prevent="validateDefinition">
        <header>
          <div>
            <p class="eyebrow">BPMN 2.0.2</p>
            <h2>定义校验</h2>
          </div>
          <span class="workflow-badge">先校验，后部署</span>
        </header>
        <label>
          <span>流程定义键</span>
          <input v-model="definition.processDefinitionKey" required />
        </label>
        <label>
          <span>业务版本</span>
          <input v-model="definition.businessVersion" required />
        </label>
        <label>
          <span>领域包版本 ID</span>
          <input v-model="definition.domainPackageVersionId" />
        </label>
        <label>
          <span>BPMN XML</span>
          <textarea v-model="definition.bpmnXml" rows="16" required />
        </label>
        <div class="workflow-actions">
          <button class="secondary-button" type="submit" :disabled="busy">
            校验定义
          </button>
          <button
            class="primary-button"
            type="button"
            :disabled="busy || !canDeploy"
            @click="deployDefinition"
          >
            部署已校验版本
          </button>
        </div>
      </form>

      <aside class="workflow-card">
        <h2>校验与部署结果</h2>
        <dl v-if="validation" class="workflow-detail">
          <dt>校验状态</dt>
          <dd>{{ validation.valid ? '通过' : '未通过' }}</dd>
          <dt>内容哈希</dt>
          <dd><code>{{ validation.contentHash }}</code></dd>
        </dl>
        <ul v-if="validation?.findings.length" class="workflow-findings">
          <li v-for="finding in validation.findings" :key="finding.code">
            <strong>{{ finding.severity }} · {{ finding.code }}</strong>
            <span>{{ finding.message }}</span>
          </li>
        </ul>
        <dl v-if="deployed" class="workflow-detail">
          <dt>定义 ID</dt>
          <dd>{{ deployed.id }}</dd>
          <dt>部署状态</dt>
          <dd>{{ deployed.status }}</dd>
          <dt>部署时间</dt>
          <dd>{{ deployed.deployedAt }}</dd>
        </dl>
        <p v-if="!validation" class="workflow-empty">尚未执行定义校验。</p>
      </aside>
    </section>

    <section v-else class="workflow-instance">
      <form class="workflow-search" @submit.prevent="queryInstance">
        <label>
          <span>流程实例 ID</span>
          <input v-model="instanceId" required placeholder="输入 UUID" />
        </label>
        <button class="primary-button" type="submit" :disabled="busy">查询诊断</button>
      </form>

      <template v-if="instance">
        <div class="workflow-summary">
          <div><span>实例状态</span><strong>{{ instance.state }}</strong></div>
          <div><span>当前活动</span><strong>{{ instance.currentActivityKeys.length }}</strong></div>
          <div><span>Incident</span><strong>{{ instance.incidentCount }}</strong></div>
          <div><span>Dead Letter</span><strong>{{ deadLetterCount }}</strong></div>
        </div>

        <div class="workflow-grid">
          <section class="workflow-card">
            <h2>Incident / Dead Letter</h2>
            <SchemaTable
              :schema="incidentSchema"
              :rows="instance.incidents ?? []"
            />
            <p v-if="!instance.incidents?.length" class="workflow-empty">
              当前实例没有异常记录。
            </p>
          </section>

          <form class="workflow-card" @submit.prevent="submitAction">
            <h2>受控管理动作</h2>
            <label>
              <span>动作</span>
              <select v-model="action.action">
                <option>PAUSE</option>
                <option>RESUME</option>
                <option>RETRY</option>
                <option>MIGRATE</option>
                <option>TERMINATE</option>
                <option>MANUAL_COMPENSATE</option>
              </select>
            </label>
            <label>
              <span>操作原因</span>
              <textarea v-model="action.reason" rows="4" required minlength="5" />
            </label>
            <label v-if="action.action === 'MIGRATE'">
              <span>目标定义 ID</span>
              <input v-model="action.targetDefinitionId" required />
            </label>
            <label v-if="action.action === 'MIGRATE'">
              <span>影响预览 ID</span>
              <input v-model="action.impactPreviewId" required />
            </label>
            <p class="workflow-revision">当前对象版本：{{ instance.revision }}</p>
            <button class="primary-button" type="submit" :disabled="busy">
              提交管理动作
            </button>
          </form>
        </div>
      </template>
    </section>
  </AppLayout>
</template>

<style scoped>
.workflow-navigation__brand {
  margin: 0 0 var(--pdp-space-8);
  color: var(--pdp-color-brand);
  font-size: 28px;
  font-weight: 800;
}

.workflow-navigation__link {
  display: block;
  margin-bottom: var(--pdp-space-3);
  color: var(--pdp-color-text);
}

.workflow-navigation__section {
  margin: var(--pdp-space-8) 0 var(--pdp-space-2);
  color: var(--pdp-color-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workflow-navigation__section + button,
.workflow-navigation__section + button + button {
  width: 100%;
  margin-bottom: var(--pdp-space-2);
  padding: var(--pdp-space-3);
  border: 0;
  border-radius: var(--pdp-radius-sm);
  color: var(--pdp-color-text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.workflow-navigation__section + button.active,
.workflow-navigation__section + button + button.active {
  color: var(--pdp-color-brand-strong);
  background: color-mix(in srgb, var(--pdp-color-brand) 10%, white);
}

.workflow-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(300px, 2fr);
  gap: var(--pdp-space-6);
  margin-top: var(--pdp-space-6);
}

.workflow-card {
  display: grid;
  gap: var(--pdp-space-4);
  align-self: start;
  padding: var(--pdp-space-6);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-md);
  background: var(--pdp-color-surface);
}

.workflow-card header,
.workflow-actions,
.workflow-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pdp-space-4);
}

.workflow-card h2 {
  margin: 0;
}

.workflow-card label,
.workflow-search label {
  display: grid;
  gap: var(--pdp-space-2);
}

.workflow-card input,
.workflow-card select,
.workflow-card textarea,
.workflow-search input {
  width: 100%;
  min-height: 40px;
  padding: var(--pdp-space-2) var(--pdp-space-3);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-sm);
  background: white;
}

.workflow-card textarea {
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.workflow-badge {
  padding: var(--pdp-space-1) var(--pdp-space-2);
  border-radius: 999px;
  color: var(--pdp-color-brand-strong);
  background: color-mix(in srgb, var(--pdp-color-brand) 10%, white);
  font-size: 12px;
}

.primary-button,
.secondary-button {
  min-height: 40px;
  padding: 0 var(--pdp-space-4);
  border: 1px solid var(--pdp-color-brand);
  border-radius: var(--pdp-radius-sm);
  cursor: pointer;
}

.primary-button {
  color: white;
  background: var(--pdp-color-brand);
}

.secondary-button {
  color: var(--pdp-color-brand-strong);
  background: white;
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.workflow-detail {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: var(--pdp-space-3);
}

.workflow-detail dt,
.workflow-empty,
.workflow-revision {
  color: var(--pdp-color-muted);
}

.workflow-detail dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.workflow-findings {
  display: grid;
  gap: var(--pdp-space-3);
  padding: 0;
  list-style: none;
}

.workflow-findings li {
  display: grid;
  gap: var(--pdp-space-1);
  padding: var(--pdp-space-3);
  border-left: 3px solid var(--pdp-color-warning);
  background: var(--pdp-color-canvas);
}

.workflow-search {
  padding: var(--pdp-space-4);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-md);
  background: white;
}

.workflow-search label {
  flex: 1;
}

.workflow-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--pdp-space-4);
  margin-top: var(--pdp-space-6);
}

.workflow-summary div {
  display: grid;
  gap: var(--pdp-space-2);
  padding: var(--pdp-space-4);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-md);
  background: white;
}

.workflow-summary span {
  color: var(--pdp-color-muted);
  font-size: 13px;
}

.workflow-summary strong {
  font-size: 24px;
}

@media (width <= 980px) {
  .workflow-grid,
  .workflow-summary {
    grid-template-columns: 1fr;
  }

  .workflow-search,
  .workflow-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

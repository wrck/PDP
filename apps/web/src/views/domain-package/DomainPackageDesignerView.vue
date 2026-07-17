<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiProblemError } from '../../api/client'
import AppLayout from '../../layouts/AppLayout.vue'
import FeedbackBanner from '../../layouts/FeedbackBanner.vue'
import { usePlatformStore } from '../../stores'
import { domainPackageDesignerApi } from './domainPackageApi'
import type {
  DomainPackage,
  DomainPackageDesignerApi,
  DomainPackageManifest,
  DomainPackageVersion,
  MigrationJob,
  MigrationPreview,
  PackageLayer,
  ValidationReport,
} from './types'

type DesignerTab = 'model' | 'behavior' | 'source' | 'release'

const props = defineProps<{ designerApi?: DomainPackageDesignerApi }>()
const service = props.designerApi ?? domainPackageDesignerApi
const platformStore = usePlatformStore()

const packages = ref<DomainPackage[]>([])
const selectedPackageId = ref('')
const layerFilter = ref<PackageLayer | ''>('')
const activeTab = ref<DesignerTab>('model')
const busy = ref(false)
const sourceText = ref('')
const version = ref<DomainPackageVersion | null>(null)
const validation = ref<ValidationReport | null>(null)
const migrationPreview = ref<MigrationPreview | null>(null)
const migrationJob = ref<MigrationJob | null>(null)
const reviewComment = ref('已核对结构、权限、流程、迁移与回滚方案')
const sourceVersionId = ref('')
const migrationReason = ref('按批准的领域包版本执行分批升级')
const batchSize = ref(100)
const packageForm = ref({
  stableKey: '',
  name: '',
  layer: 'INDUSTRY' as PackageLayer,
  parentPackageId: '',
})
const feedback = ref<{
  type: 'success' | 'error' | 'warning'
  title: string
  detail?: string
  traceId?: string
} | null>(null)

const manifest = ref<DomainPackageManifest>(emptyManifest())
const selectedPackage = computed(() =>
  packages.value.find((item) => item.id === selectedPackageId.value),
)
const objectCount = computed(() => manifest.value.objects.length)
const fieldCount = computed(() =>
  manifest.value.objects.reduce((sum, item) => sum + item.fields.length, 0),
)
const stateCount = computed(() =>
  manifest.value.objects.reduce((sum, item) => sum + item.states.length, 0),
)

onMounted(loadPackages)
watch(() => platformStore.workspaceId, loadPackages)
watch(selectedPackage, (selected) => {
  if (!selected) return
  manifest.value.stableKey = selected.stableKey
  manifest.value.name = selected.name
  manifest.value.layer = selected.layer
  sourceText.value = JSON.stringify(manifest.value, null, 2)
})

async function loadPackages() {
  const workspaceId = platformStore.workspaceId
  if (!workspaceId) {
    feedback.value = {
      type: 'warning',
      title: '请先选择工作空间',
      detail: '领域包属于工作空间治理边界，未选择工作空间时不能设计或发布。',
    }
    return
  }
  await run('领域包加载失败', async () => {
    const page = await service.list(
      workspaceId,
      layerFilter.value || undefined,
    )
    packages.value = page.items
    if (!packages.value.some((item) => item.id === selectedPackageId.value)) {
      selectedPackageId.value = packages.value[0]?.id ?? ''
    }
  })
}

async function createPackage() {
  const workspaceId = requireWorkspace()
  if (!workspaceId) return
  await run('领域包创建失败', async () => {
    const created = await service.createPackage(workspaceId, {
      stableKey: packageForm.value.stableKey,
      name: packageForm.value.name,
      layer: packageForm.value.layer,
      parentPackageId: packageForm.value.parentPackageId || null,
    })
    packages.value = [...packages.value, created]
    selectedPackageId.value = created.id
    packageForm.value = {
      stableKey: '',
      name: '',
      layer: 'INDUSTRY',
      parentPackageId: '',
    }
    feedback.value = { type: 'success', title: '领域包草稿已创建' }
  })
}

function addObject() {
  const index = manifest.value.objects.length + 1
  manifest.value.objects.push({
    stableKey: `custom.object-${index}`,
    kind: 'NEW_OBJECT',
    label: { 'zh-CN': `自定义对象 ${index}` },
    fields: [],
    relations: [],
    states: [],
    transitions: [],
  })
}

function addField(objectIndex: number) {
  const object = manifest.value.objects[objectIndex]
  object.fields.push({
    stableKey: `${object.stableKey}.field-${object.fields.length + 1}`,
    label: { 'zh-CN': '新字段' },
    dataType: 'TEXT',
  })
}

function addState(objectIndex: number) {
  const object = manifest.value.objects[objectIndex]
  object.states.push({
    stableKey: `state-${object.states.length + 1}`,
    label: { 'zh-CN': '新状态' },
    topLifecycleState: 'PLANNING',
    initial: object.states.length === 0,
  })
}

function openSource() {
  sourceText.value = JSON.stringify(manifest.value, null, 2)
  activeTab.value = 'source'
}

function applySource() {
  try {
    manifest.value = JSON.parse(sourceText.value) as DomainPackageManifest
    feedback.value = { type: 'success', title: 'JSON 定义已应用到设计器' }
  } catch (error) {
    feedback.value = {
      type: 'error',
      title: 'JSON 格式无效',
      detail: error instanceof Error ? error.message : '无法解析领域包定义',
    }
  }
}

async function createVersion() {
  const context = requirePackageContext()
  if (!context) return
  await run('版本草稿创建失败', async () => {
    version.value = await service.createVersion(
      context.workspaceId,
      context.packageId,
      {
        semanticVersion: manifest.value.version,
        manifest: manifest.value,
        changeSummary: '领域设计器保存',
      },
    )
    validation.value = null
    feedback.value = { type: 'success', title: '领域包版本草稿已保存' }
  })
}

async function validateVersion() {
  const context = requireVersionContext()
  if (!context) return
  await run('领域包校验失败', async () => {
    validation.value = await service.validate(
      context.workspaceId,
      context.packageId,
      context.version.id,
      context.version.revision,
    )
    if (validation.value.valid) {
      context.version.status = 'VALIDATED'
      context.version.revision += 1
      feedback.value = { type: 'success', title: '领域包校验通过' }
    } else {
      feedback.value = {
        type: 'warning',
        title: '领域包存在阻断项',
        detail: validation.value.errors.join('；'),
      }
    }
  })
}

async function submitReview() {
  await updateVersion('提交审核失败', (context) =>
    service.submitReview(
      context.workspaceId,
      context.packageId,
      context.version.id,
      context.version.revision,
    ),
  )
}

async function approveVersion() {
  await updateVersion('独立审核失败', (context) =>
    service.review(
      context.workspaceId,
      context.packageId,
      context.version.id,
      context.version.revision,
      reviewComment.value,
    ),
  )
}

async function publishVersion() {
  await updateVersion('领域包发布失败', (context) =>
    service.publish(
      context.workspaceId,
      context.packageId,
      context.version.id,
      context.version.revision,
      reviewComment.value,
    ),
  )
}

async function previewMigration() {
  const context = requireVersionContext()
  if (!context || !sourceVersionId.value) return
  await run('迁移影响预览失败', async () => {
    migrationPreview.value = await service.previewMigration(
      context.workspaceId,
      context.packageId,
      context.version.id,
      {
        sourceVersionId: sourceVersionId.value,
        scope: { workspaceId: context.workspaceId },
        batchSize: batchSize.value,
      },
    )
  })
}

async function startMigration() {
  const context = requireVersionContext()
  if (!context || !migrationPreview.value) return
  await run('迁移作业启动失败', async () => {
    migrationJob.value = await service.startMigration(
      context.workspaceId,
      context.packageId,
      context.version.id,
      {
        previewId: migrationPreview.value?.previewId,
        confirmationToken: migrationPreview.value?.confirmationToken,
        batchSize: batchSize.value,
        reason: migrationReason.value,
      },
    )
    feedback.value = { type: 'success', title: '迁移作业已创建' }
  })
}

async function updateVersion(
  title: string,
  action: (
    context: NonNullable<ReturnType<typeof requireVersionContext>>,
  ) => Promise<DomainPackageVersion>,
) {
  const context = requireVersionContext()
  if (!context) return
  await run(title, async () => {
    version.value = await action(context)
    feedback.value = { type: 'success', title: `版本状态：${version.value.status}` }
  })
}

function requireWorkspace(): string | null {
  if (platformStore.workspaceId) return platformStore.workspaceId
  feedback.value = { type: 'warning', title: '请先选择工作空间' }
  return null
}

function requirePackageContext() {
  const workspaceId = requireWorkspace()
  if (!workspaceId || !selectedPackageId.value) {
    feedback.value = { type: 'warning', title: '请先选择领域包' }
    return null
  }
  return { workspaceId, packageId: selectedPackageId.value }
}

function requireVersionContext() {
  const context = requirePackageContext()
  if (!context || !version.value) {
    feedback.value = { type: 'warning', title: '请先保存版本草稿' }
    return null
  }
  return { ...context, version: version.value }
}

async function run(title: string, action: () => Promise<void>) {
  busy.value = true
  feedback.value = null
  try {
    await action()
  } catch (error) {
    showError(error, title)
  } finally {
    busy.value = false
  }
}

function showError(error: unknown, title: string) {
  feedback.value =
    error instanceof ApiProblemError
      ? {
          type: 'error',
          title,
          detail: error.message,
          traceId: error.traceId,
        }
      : {
          type: 'error',
          title,
          detail: error instanceof Error ? error.message : '发生未知错误',
        }
}

function emptyManifest(): DomainPackageManifest {
  return {
    schemaVersion: '1.1',
    stableKey: 'custom.delivery',
    name: '自定义交付领域包',
    description: '',
    layer: 'INDUSTRY',
    version: '1.0.0',
    objects: [],
    pages: [],
    views: [],
    rules: [],
    workflowBindings: [],
    permissions: [],
    extensions: [],
    overrides: [],
    migrations: [],
  }
}
</script>

<template>
  <AppLayout title="领域包设计器">
    <template #navigation>
      <p class="designer-brand">PDP</p>
      <RouterLink to="/">平台首页</RouterLink>
      <RouterLink to="/workspaces">工作空间治理</RouterLink>
      <p class="designer-section">领域包</p>
      <label>
        分层筛选
        <select v-model="layerFilter" @change="loadPackages">
          <option value="">全部层级</option>
          <option value="PLATFORM_STANDARD">平台标准</option>
          <option value="INDUSTRY">行业</option>
          <option value="WORKSPACE_CUSTOMER">工作空间客户</option>
        </select>
      </label>
      <button
        v-for="item in packages"
        :key="item.id"
        type="button"
        :class="{ active: selectedPackageId === item.id }"
        @click="selectedPackageId = item.id"
      >
        <strong>{{ item.name }}</strong>
        <span>{{ item.stableKey }} · {{ item.status }}</span>
      </button>
    </template>

    <template #eyebrow>
      <p class="eyebrow">平台 → 行业 → 工作空间客户</p>
    </template>

    <template #actions>
      <button class="secondary-button" type="button" @click="loadPackages">
        刷新
      </button>
    </template>

    <FeedbackBanner
      v-if="feedback"
      :type="feedback.type"
      :title="feedback.title"
      :detail="feedback.detail"
      :trace-id="feedback.traceId"
    />

    <section class="designer-summary">
      <div><span>当前领域包</span><strong>{{ selectedPackage?.name ?? '未选择' }}</strong></div>
      <div><span>对象</span><strong>{{ objectCount }}</strong></div>
      <div><span>字段</span><strong>{{ fieldCount }}</strong></div>
      <div><span>状态</span><strong>{{ stateCount }}</strong></div>
    </section>

    <details class="designer-card create-package">
      <summary>创建新的领域包草稿</summary>
      <form @submit.prevent="createPackage">
        <label>稳定标识<input v-model="packageForm.stableKey" required /></label>
        <label>名称<input v-model="packageForm.name" required /></label>
        <label>
          层级
          <select v-model="packageForm.layer">
            <option value="PLATFORM_STANDARD">平台标准</option>
            <option value="INDUSTRY">行业</option>
            <option value="WORKSPACE_CUSTOMER">工作空间客户</option>
          </select>
        </label>
        <label v-if="packageForm.layer !== 'PLATFORM_STANDARD'">
          父领域包 ID
          <input v-model="packageForm.parentPackageId" required />
        </label>
        <button class="primary-button" type="submit" :disabled="busy">创建草稿</button>
      </form>
    </details>

    <nav class="designer-tabs" aria-label="领域包设计步骤">
      <button type="button" :class="{ active: activeTab === 'model' }" @click="activeTab = 'model'">对象与字段</button>
      <button type="button" :class="{ active: activeTab === 'behavior' }" @click="activeTab = 'behavior'">状态与行为</button>
      <button type="button" :class="{ active: activeTab === 'source' }" @click="openSource">高级 JSON</button>
      <button type="button" :class="{ active: activeTab === 'release' }" @click="activeTab = 'release'">校验与发布</button>
    </nav>

    <section v-if="activeTab === 'model'" class="designer-grid">
      <div class="designer-card">
        <header><h2>对象模型</h2><button type="button" @click="addObject">添加对象</button></header>
        <article v-for="(object, objectIndex) in manifest.objects" :key="object.stableKey" class="object-editor">
          <label>对象稳定标识<input v-model="object.stableKey" /></label>
          <label>中文名称<input v-model="object.label['zh-CN']" /></label>
          <label>类型<select v-model="object.kind"><option>NEW_OBJECT</option><option>CORE_EXTENSION</option></select></label>
          <button type="button" @click="addField(objectIndex)">添加字段</button>
          <div v-for="field in object.fields" :key="field.stableKey" class="field-row">
            <input v-model="field.stableKey" aria-label="字段稳定标识" />
            <input v-model="field.label['zh-CN']" aria-label="字段名称" />
            <select v-model="field.dataType" aria-label="字段类型">
              <option>TEXT</option><option>LONG_TEXT</option><option>INTEGER</option><option>DECIMAL</option><option>BOOLEAN</option><option>DATE</option><option>DATETIME</option><option>OBJECT_REF</option><option>FILE_REF</option>
            </select>
            <label><input v-model="field.required" type="checkbox" />必填</label>
            <label><input v-model="field.sensitive" type="checkbox" />敏感</label>
          </div>
        </article>
        <p v-if="!manifest.objects.length" class="empty">尚未定义对象；可继承核心对象或添加领域对象。</p>
      </div>
      <aside class="designer-card guidance">
        <h2>设计约束</h2>
        <ul><li>稳定标识发布后不可静默改名。</li><li>优先复用平台核心字段语义。</li><li>敏感字段必须独立授权并进入审计。</li><li>客户层只能细化上层能力，不能绕过平台不变量。</li></ul>
      </aside>
    </section>

    <section v-else-if="activeTab === 'behavior'" class="designer-card">
      <header><div><h2>状态与顶层生命周期映射</h2><p>每个可运行对象只能有一个初始状态，领域状态必须映射平台顶层生命周期。</p></div></header>
      <article v-for="(object, objectIndex) in manifest.objects" :key="object.stableKey" class="object-editor">
        <header><strong>{{ object.label['zh-CN'] }} · {{ object.stableKey }}</strong><button type="button" @click="addState(objectIndex)">添加状态</button></header>
        <div v-for="state in object.states" :key="state.stableKey" class="state-row">
          <input v-model="state.stableKey" aria-label="状态稳定标识" />
          <input v-model="state.label['zh-CN']" aria-label="状态名称" />
          <select v-model="state.topLifecycleState" aria-label="顶层生命周期">
            <option>PRE_PLANNING</option><option>PLANNING</option><option>EXECUTING</option><option>ACCEPTING</option><option>SERVICING</option><option>CLOSED</option><option>CANCELLED</option>
          </select>
          <label><input v-model="state.initial" type="checkbox" />初始</label>
          <label><input v-model="state.terminal" type="checkbox" />终态</label>
        </div>
      </article>
    </section>

    <section v-else-if="activeTab === 'source'" class="designer-card">
      <header><div><h2>完整领域包定义</h2><p>用于页面、规则、权限、受治理扩展、工作流绑定和迁移等高级定制。</p></div><button type="button" @click="applySource">应用 JSON</button></header>
      <textarea v-model="sourceText" class="source-editor" rows="28" spellcheck="false" />
    </section>

    <section v-else class="release-grid">
      <div class="designer-card release-flow">
        <h2>版本闭环</h2>
        <label>语义版本<input v-model="manifest.version" pattern="[0-9]+\.[0-9]+\.[0-9]+" /></label>
        <p>当前状态：<strong>{{ version?.status ?? '尚未保存' }}</strong></p>
        <button type="button" :disabled="busy || !selectedPackage" @click="createVersion">保存版本草稿</button>
        <button type="button" :disabled="busy || version?.status !== 'DRAFT'" @click="validateVersion">执行发布校验</button>
        <button type="button" :disabled="busy || version?.status !== 'VALIDATED'" @click="submitReview">创建者提交审核</button>
        <label>审核/发布意见<textarea v-model="reviewComment" rows="3" /></label>
        <button type="button" :disabled="busy || version?.status !== 'REVIEW_PENDING'" @click="approveVersion">独立审核人批准</button>
        <button type="button" :disabled="busy || version?.status !== 'APPROVED'" @click="publishVersion">独立发布人发布</button>
        <div v-if="validation" class="validation-result">
          <strong>{{ validation.valid ? '校验通过' : '校验未通过' }} · {{ validation.compatibility }}</strong>
          <ul><li v-for="error in validation.errors" :key="error">{{ error }}</li><li v-for="warning in validation.warnings" :key="warning">{{ warning }}</li></ul>
        </div>
      </div>
      <div class="designer-card migration-flow">
        <h2>存量实例升级</h2>
        <label>源版本 ID<input v-model="sourceVersionId" /></label>
        <label>每批实例数<input v-model.number="batchSize" type="number" min="1" max="1000" /></label>
        <button type="button" :disabled="busy || version?.status !== 'PUBLISHED' || !sourceVersionId" @click="previewMigration">生成影响预览</button>
        <div v-if="migrationPreview" class="migration-preview">
          <strong>影响 {{ migrationPreview.affectedInstances }} 个实例 / {{ migrationPreview.batches }} 批</strong>
          <p>回滚可用：{{ migrationPreview.rollbackAvailable ? '是' : '否' }}</p>
          <ul><li v-for="conflict in migrationPreview.conflicts" :key="conflict">{{ conflict }}</li></ul>
          <label>迁移原因<textarea v-model="migrationReason" rows="3" /></label>
          <button type="button" :disabled="busy" @click="startMigration">确认并创建迁移作业</button>
        </div>
        <p v-if="migrationJob">作业 {{ migrationJob.id }} · {{ migrationJob.status }}</p>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
label {
  display: grid;
  gap: var(--pdp-space-2);
}

input,
select,
textarea {
  width: 100%;
  min-height: 40px;
  padding: var(--pdp-space-2) var(--pdp-space-3);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-sm);
  background: white;
}

.designer-brand {
  margin: 0 0 var(--pdp-space-8);
  color: var(--pdp-color-brand);
  font-size: 28px;
  font-weight: 800;
}

.designer-section {
  margin: var(--pdp-space-8) 0 var(--pdp-space-2);
  color: var(--pdp-color-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.app-layout__navigation a,
.app-layout__navigation label {
  display: block;
  margin-bottom: var(--pdp-space-3);
  color: var(--pdp-color-text);
}

.app-layout__navigation select {
  width: 100%;
  min-height: 38px;
  margin-top: var(--pdp-space-2);
}

.app-layout__navigation button {
  display: grid;
  width: 100%;
  gap: 4px;
  margin-bottom: 6px;
  padding: 10px;
  border: 0;
  border-radius: var(--pdp-radius-sm);
  background: transparent;
  text-align: left;
}

.designer-summary span,
.empty,
.designer-card p {
  color: var(--pdp-color-muted);
}

.app-layout__navigation button span {
  color: var(--pdp-color-muted);
  font-size: 11px;
}

.app-layout__navigation button.active {
  background: color-mix(in srgb, var(--pdp-color-brand) 10%, white);
}

.designer-summary {
  display: grid;
  grid-template-columns: 2fr repeat(3, 1fr);
  gap: var(--pdp-space-4);
  margin: var(--pdp-space-6) 0;
}

.designer-summary div,
.designer-card {
  padding: var(--pdp-space-5);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-md);
  background: white;
}

.designer-summary div {
  display: grid;
  gap: var(--pdp-space-2);
}

.designer-summary strong {
  font-size: 22px;
}

.create-package form,
.designer-card,
.release-flow,
.migration-flow {
  display: grid;
  gap: var(--pdp-space-4);
}

.create-package summary {
  cursor: pointer;
  font-weight: 700;
}

.create-package form {
  grid-template-columns: repeat(4, 1fr) auto;
  margin-top: var(--pdp-space-4);
}

.designer-tabs {
  display: flex;
  gap: var(--pdp-space-2);
  margin: var(--pdp-space-6) 0;
  border-bottom: 1px solid var(--pdp-color-border);
}

.designer-tabs button {
  padding: var(--pdp-space-3) var(--pdp-space-4);
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
}

.designer-tabs button.active {
  border-color: var(--pdp-color-brand);
  color: var(--pdp-color-brand-strong);
}

.designer-grid,
.release-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(300px, 1fr);
  gap: var(--pdp-space-6);
}

.designer-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pdp-space-4);
}

.designer-card h2 {
  margin: 0;
}

.object-editor {
  display: grid;
  grid-template-columns: repeat(3, 1fr) auto;
  gap: var(--pdp-space-3);
  padding: var(--pdp-space-4);
  border: 1px solid var(--pdp-color-border);
  border-radius: var(--pdp-radius-sm);
}

.field-row,
.state-row {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: 2fr 1.5fr 1fr auto auto;
  align-items: end;
  gap: var(--pdp-space-2);
}

.field-row label,
.state-row label {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.field-row label input,
.state-row label input {
  width: auto;
  min-height: 0;
}

.source-editor {
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.validation-result,
.migration-preview {
  padding: var(--pdp-space-4);
  border-left: 4px solid var(--pdp-color-brand);
  background: var(--pdp-color-canvas);
}

.primary-button,
.secondary-button,
.designer-card button {
  min-height: 40px;
  padding: 0 var(--pdp-space-4);
  border: 1px solid var(--pdp-color-brand);
  border-radius: var(--pdp-radius-sm);
  background: white;
  color: var(--pdp-color-brand-strong);
}

.primary-button {
  background: var(--pdp-color-brand);
  color: white;
}

button:disabled {
  opacity: 0.5;
}

@media (width <= 1000px) {
  .designer-grid,
  .release-grid,
  .create-package form {
    grid-template-columns: 1fr;
  }

  .designer-summary {
    grid-template-columns: repeat(2, 1fr);
  }

  .object-editor {
    grid-template-columns: 1fr;
  }

  .field-row,
  .state-row {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
/**
 * 流程定义部署抽屉（T090、FR-174）。
 *
 * 提供两步流程：
 * <ol>
 *   <li>校验：调用 `POST /workflow-definitions/validate`，展示内容哈希与发现项；</li>
 *   <li>部署：携带校验返回的 contentHash 调用 `POST /workflow-definitions/deploy`，
 *       高风险写操作 MUST 携带 Idempotency-Key（前端自动生成）。</li>
 * </ol>
 *
 * <p><strong>幂等</strong>：相同 (workspace, key, version, contentHash) 重复部署由后端返回已有定义。
 */
import { computed, reactive, ref, watch } from 'vue'
import {
  Alert,
  Button,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Form,
  FormItem,
  Input,
  Space,
  Steps,
  Step,
  Tag,
  Typography,
  TypographyTitle,
  message,
} from 'ant-design-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import type {
  ValidationResult,
  WorkflowDefinitionSummary,
} from './types'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'deployed', summary: WorkflowDefinitionSummary): void
}>()

/** 表单状态。 */
const form = reactive({
  processDefinitionKey: '',
  businessVersion: '',
  domainPackageVersionId: '',
  bpmnXml: '',
})

/** 校验结果。 */
const validation = ref<ValidationResult | null>(null)

/** 当前步骤：0=填写、1=校验通过待部署。 */
const currentStep = ref(0)

/** 操作进行中状态。 */
const validating = ref(false)
const deploying = ref(false)

/** 监听可见性，重置表单。 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm()
    }
  },
)

const canValidate = computed(
  () =>
    form.processDefinitionKey.trim() !== '' &&
    /^\d+\.\d+\.\d+$/.test(form.businessVersion.trim()) &&
    form.bpmnXml.trim().length >= 50,
)

const canDeploy = computed(() => validation.value?.valid === true && !deploying.value)

const hasErrors = computed(
  () => validation.value?.findings.some((f) => f.severity === 'ERROR') ?? false,
)

const hasWarnings = computed(
  () => validation.value?.findings.some((f) => f.severity === 'WARNING') ?? false,
)

/** 关闭抽屉。 */
function handleClose(): void {
  emit('update:visible', false)
}

/** 重置表单。 */
function resetForm(): void {
  form.processDefinitionKey = ''
  form.businessVersion = ''
  form.domainPackageVersionId = ''
  form.bpmnXml = ''
  validation.value = null
  currentStep.value = 0
  validating.value = false
  deploying.value = false
}

/** 执行校验。 */
async function handleValidate(): Promise<void> {
  if (!canValidate.value) {
    message.warning('请填写完整且符合格式的字段')
    return
  }
  validating.value = true
  validation.value = null
  try {
    const result = await workflowApi.validateDefinition({
      processDefinitionKey: form.processDefinitionKey.trim(),
      businessVersion: form.businessVersion.trim(),
      domainPackageVersionId: form.domainPackageVersionId.trim() || null,
      bpmnXml: form.bpmnXml,
    })
    validation.value = result
    if (result.valid && !hasErrors.value) {
      currentStep.value = 1
      message.success('校验通过，可以部署')
    } else {
      message.error(`校验未通过：${hasErrors.value ? '存在 ERROR 级发现项' : '请检查发现项'}`)
    }
  } catch (err) {
    handleError(err, '校验失败')
  } finally {
    validating.value = false
  }
}

/** 执行部署。 */
async function handleDeploy(): Promise<void> {
  if (!validation.value || !canDeploy.value) return
  deploying.value = true
  try {
    const summary = await workflowApi.deployDefinition(
      {
        processDefinitionKey: form.processDefinitionKey.trim(),
        businessVersion: form.businessVersion.trim(),
        contentHash: validation.value.contentHash,
        bpmnResource: form.bpmnXml,
        domainPackageVersionId: form.domainPackageVersionId.trim() || null,
      },
      // 高风险写操作 MUST 携带 Idempotency-Key，api.ts 中自动生成
    )
    emit('deployed', summary)
  } catch (err) {
    handleError(err, '部署失败')
  } finally {
    deploying.value = false
  }
}

/** 返回上一步修改。 */
function backToEdit(): void {
  currentStep.value = 0
  validation.value = null
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
</script>

<template>
  <Drawer
    :open="visible"
    title="部署流程定义"
    placement="right"
    :width="720"
    :mask-closable="false"
    :destroy-on-close="true"
    @close="handleClose"
  >
    <div class="deploy-drawer">
      <Steps :current="currentStep" size="small" class="steps">
        <Step title="填写与校验" />
        <Step title="确认并部署" />
      </Steps>

      <Form layout="vertical" class="deploy-form">
        <FormItem
          label="流程键"
          required
          help="小写字母开头，2-100 位，仅含 a-z 0-9 . -"
        >
          <Input
            v-model:value="form.processDefinitionKey"
            placeholder="例如 network-cutover.approval"
            :disabled="currentStep === 1"
          />
        </FormItem>
        <FormItem
          label="业务版本"
          required
          help="语义化版本 MAJOR.MINOR.PATCH"
        >
          <Input
            v-model:value="form.businessVersion"
            placeholder="例如 1.0.0"
            :disabled="currentStep === 1"
          />
        </FormItem>
        <FormItem label="领域包版本 ID" help="可选，关联已发布领域包版本">
          <Input
            v-model:value="form.domainPackageVersionId"
            placeholder="UUID 格式"
            :disabled="currentStep === 1"
          />
        </FormItem>
        <FormItem
          label="BPMN 2.0.2 XML"
          required
          help="完整 BPMN XML 内容，长度 ≥ 50"
        >
          <Input.TextArea
            v-model:value="form.bpmnXml"
            :rows="12"
            placeholder="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<definitions ...>"
            :disabled="currentStep === 1"
            class="bpmn-textarea"
          />
        </FormItem>
      </Form>

      <!-- 校验结果展示 -->
      <div v-if="validation" class="validation-result">
        <Typography>
          <TypographyTitle :level="5">校验结果</TypographyTitle>
        </Typography>
        <Descriptions bordered size="small" :column="1">
          <DescriptionsItem label="是否通过">
            <Tag :color="validation.valid && !hasErrors ? 'green' : 'red'">
              {{ validation.valid && !hasErrors ? '通过' : '未通过' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="内容哈希">
            <span class="mono">{{ validation.contentHash }}</span>
          </DescriptionsItem>
          <DescriptionsItem v-if="validation.findings.length > 0" label="发现项">
            <Space direction="vertical" class="findings-list">
              <div
                v-for="(finding, idx) in validation.findings"
                :key="idx"
                class="finding-item"
              >
                <Tag :color="finding.severity === 'ERROR' ? 'red' : 'orange'">
                  {{ finding.severity }}
                </Tag>
                <Tag>{{ finding.code }}</Tag>
                <span class="finding-message">{{ finding.message }}</span>
              </div>
            </Space>
          </DescriptionsItem>
        </Descriptions>
      </div>

      <!-- 高风险操作提示 -->
      <Alert
        v-if="currentStep === 1"
        type="warning"
        show-icon
        message="高风险写操作"
        description="部署流程定义属于高风险写操作，前端将自动生成 Idempotency-Key 头以保证幂等。相同 (workspace, key, version, contentHash) 重复部署将返回已有定义。"
        class="risk-alert"
      />

      <!-- 底部操作 -->
      <div class="drawer-footer">
        <Space>
          <Button @click="handleClose">取消</Button>
          <Button v-if="currentStep === 1" @click="backToEdit">返回修改</Button>
          <Button
            v-if="currentStep === 0"
            type="primary"
            :loading="validating"
            :disabled="!canValidate"
            @click="handleValidate"
          >
            校验
          </Button>
          <Button
            v-if="currentStep === 1"
            type="primary"
            :loading="deploying"
            :disabled="!canDeploy"
            @click="handleDeploy"
          >
            确认部署
          </Button>
        </Space>
      </div>
    </div>
  </Drawer>
</template>

<style scoped lang="scss">
.deploy-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .steps {
    margin-bottom: 8px;
  }

  .bpmn-textarea {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }

  .validation-result {
    .findings-list {
      width: 100%;

      .finding-item {
        display: flex;
        align-items: flex-start;
        gap: 8px;

        .finding-message {
          flex: 1;
        }
      }
    }
  }

  .risk-alert {
    margin-top: 8px;
  }

  .drawer-footer {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 16px 24px;
    border-top: 1px solid #f0f0f0;
    background: #fff;
    text-align: right;
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.92em;
  }
}
</style>

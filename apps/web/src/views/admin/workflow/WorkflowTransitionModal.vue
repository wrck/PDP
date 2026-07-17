<script setup lang="ts">
/**
 * 流程定义状态迁移模态框（T090、FR-174）。
 *
 * 对接 `POST /workflow-definitions/{definitionId}/transitions`，使用乐观并发控制。
 * 状态机：DEPLOYED → DEPRECATED → RETIRED（终态不可恢复）。
 */
import { computed, reactive, ref, watch } from 'vue'
import {
  Alert,
  Button,
  Form,
  FormItem,
  Input,
  Modal,
  Radio,
  RadioGroup,
  Space,
  Tag,
  Typography,
  TypographyParagraph,
  message,
} from 'ant-design-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import {
  definitionStatusColor,
  definitionStatusLabel,
  type WorkflowDefinitionStatus,
  type WorkflowDefinitionSummary,
} from './types'

const props = defineProps<{
  visible: boolean
  definition: WorkflowDefinitionSummary | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'transitioned', summary: WorkflowDefinitionSummary): void
}>()

const submitting = ref(false)

const form = reactive({
  targetStatus: undefined as WorkflowDefinitionStatus | undefined,
  reason: '',
})

/** 可达的目标状态（按当前状态计算）。 */
const availableTargets = computed<{ value: WorkflowDefinitionStatus; label: string; risk: 'low' | 'high' }[]>(() => {
  if (!props.definition) return []
  const current = props.definition.status
  const options: { value: WorkflowDefinitionStatus; label: string; risk: 'low' | 'high' }[] = []
  if (current === 'VALIDATED') {
    options.push({ value: 'DEPLOYED', label: '部署（VALIDATED → DEPLOYED）', risk: 'low' })
  }
  if (current === 'DEPLOYED') {
    options.push({ value: 'DEPRECATED', label: '弃用（DEPLOYED → DEPRECATED）', risk: 'low' })
  }
  if (current === 'DEPRECATED') {
    options.push({ value: 'RETIRED', label: '退役（DEPRECATED → RETIRED，终态不可恢复）', risk: 'high' })
  }
  return options
})

const hasHighRiskTarget = computed(() =>
  form.targetStatus === 'RETIRED',
)

const canSubmit = computed(
  () => form.targetStatus !== undefined && form.reason.trim().length >= 5 && !submitting.value,
)

/** 监听可见性，重置表单。 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      form.targetStatus = undefined
      form.reason = ''
      submitting.value = false
    }
  },
)

/** 关闭模态框。 */
function handleClose(): void {
  emit('update:visible', false)
}

/** 提交状态迁移。 */
async function handleSubmit(): Promise<void> {
  if (!props.definition || !form.targetStatus) return
  if (!canSubmit.value) {
    message.warning('请选择目标状态并填写迁移原因（≥5 字符）')
    return
  }
  submitting.value = true
  try {
    const summary = await workflowApi.transitionDefinition(props.definition.id, {
      targetStatus: form.targetStatus,
      expectedRevision: 0, // P1 简化：后端通过 ETag/If-Match 处理乐观锁，这里传 0 由后端兜底
      reason: form.reason.trim(),
    })
    emit('transitioned', summary)
  } catch (err) {
    handleError(err, '状态迁移失败')
  } finally {
    submitting.value = false
  }
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
  <Modal
    :open="visible"
    title="流程定义状态迁移"
    :width="560"
    :mask-closable="false"
    :destroy-on-close="true"
    @cancel="handleClose"
  >
    <div v-if="definition" class="transition-modal">
      <Typography>
        <TypographyParagraph>
          当前流程定义：
          <span class="mono">{{ definition.processDefinitionKey }}</span>
          @
          <span class="mono">{{ definition.businessVersion }}</span>
          &nbsp;状态：
          <Tag :color="definitionStatusColor(definition.status)">
            {{ definitionStatusLabel(definition.status) }}
          </Tag>
        </TypographyParagraph>
      </Typography>

      <Form layout="vertical">
        <FormItem label="目标状态" required>
          <RadioGroup v-model:value="form.targetStatus">
            <Space direction="vertical">
              <Radio
                v-for="opt in availableTargets"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
                <Tag v-if="opt.risk === 'high'" color="red">高风险</Tag>
              </Radio>
            </Space>
          </RadioGroup>
          <TypographyParagraph v-if="availableTargets.length === 0" type="secondary">
            当前状态无可达的目标状态
          </TypographyParagraph>
        </FormItem>
        <FormItem
          label="迁移原因"
          required
          help="将记入审计日志，5-2000 字符"
        >
          <Input.TextArea
            v-model:value="form.reason"
            :rows="3"
            placeholder="说明本次状态迁移的业务原因"
          />
        </FormItem>
      </Form>

      <Alert
        v-if="hasHighRiskTarget"
        type="error"
        show-icon
        message="退役为终态操作"
        description="RETIRED 状态不可恢复，退役后定义不可再被新实例启动。请确认所有运行中实例已迁移或完成。"
      />
    </div>

    <template #footer>
      <Button @click="handleClose">取消</Button>
      <Button
        type="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="handleSubmit"
      >
        确认迁移
      </Button>
    </template>
  </Modal>
</template>

<style scoped lang="scss">
.transition-modal {
  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.92em;
  }
}
</style>

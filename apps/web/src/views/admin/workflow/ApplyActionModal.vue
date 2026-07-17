<script setup lang="ts">
/**
 * 执行受控管理动作模态框（T090、FR-174、FR-168）。
 *
 * 对接 `POST /workflow-instances/{instanceId}/actions`，使用乐观并发控制（expectedRevision）。
 *
 * <p><strong>高风险动作治理（FR-168）</strong>：
 * <ul>
 *   <li>MIGRATE/TERMINATE/MANUAL_COMPENSATE MUST 携带 OperationConfirmation；</li>
 *   <li>后端通过 HighRiskOperationPort 校验 confirmation.previewId 关联与不可逆确认；</li>
 *   <li>前端简化：高风险动作下需操作者勾选"已确认风险并承担不可逆影响"才能提交。</li>
 * </ul>
 *
 * <p><strong>MANUAL_COMPENSATE 仅恢复流程编排状态</strong>，不重复形成审批结论或业务状态变化。
 */
import { computed, reactive, ref, watch } from 'vue'
import {
  Alert,
  Button,
  Checkbox,
  Descriptions,
  DescriptionsItem,
  Form,
  FormItem,
  Input,
  Modal,
  Space,
  Tag,
  message,
} from 'ant-design-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import {
  actionLabel,
  instanceStatusColor,
  instanceStatusLabel,
  isHighRiskAction,
  type MigrationPlan,
  type WorkflowAdminActionType,
  type WorkflowInstanceSummary,
} from './types'

const props = defineProps<{
  visible: boolean
  instance: WorkflowInstanceSummary | null
  action: WorkflowAdminActionType | null
  /** MIGRATE 动作预先生成的迁移计划。 */
  migrationPlan?: MigrationPlan | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'applied', summary: WorkflowInstanceSummary): void
}>()

const submitting = ref(false)

const form = reactive({
  reason: '',
  acknowledgedIrreversible: false,
  expectedOutcome: '',
  impactPreviewId: '',
})

const isHighRisk = computed(() => props.action !== null && isHighRiskAction(props.action))
const needsMigrationPlan = computed(() => props.action === 'MIGRATE')
const hasMigrationPlan = computed(() => props.migrationPlan != null)

const canSubmit = computed(() => {
  if (!props.action || !props.instance) return false
  if (form.reason.trim().length < 5) return false
  if (isHighRisk.value && !form.acknowledgedIrreversible) return false
  if (needsMigrationPlan.value && !hasMigrationPlan.value) return false
  return !submitting.value
})

/** 监听可见性，重置表单。 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      form.reason = ''
      form.acknowledgedIrreversible = false
      form.expectedOutcome = ''
      form.impactPreviewId = ''
      submitting.value = false
    }
  },
)

/** 关闭模态框。 */
function handleClose(): void {
  emit('update:visible', false)
}

/** 提交管理动作。 */
async function handleSubmit(): Promise<void> {
  if (!props.action || !props.instance) return
  if (!canSubmit.value) {
    message.warning('请完整填写表单并完成必要的风险确认')
    return
  }

  submitting.value = true
  try {
    // 高风险动作 MUST 携带 confirmation；P1 简化：前端构造确认 DTO，
    // 实际 previewId/previewVersion 由后端 HighRiskOperationPort 在预览阶段生成并校验
    const confirmation =
      isHighRisk.value ?
        {
          previewId: props.migrationPlan?.sourceDefinitionId ?? '00000000-0000-0000-0000-000000000000',
          previewVersion: 1,
          expectedOutcome: form.expectedOutcome.trim() || null,
          acknowledgedIrreversible: form.acknowledgedIrreversible,
        }
      : null

    const impactPreviewId = form.impactPreviewId.trim() || null

    const summary = await workflowApi.applyAction(
      props.instance.id,
      {
        action: props.action,
        reason: form.reason.trim(),
        expectedRevision: props.instance.revision,
        migrationPlan: props.migrationPlan ?? null,
        confirmation,
        impactPreviewId,
      },
      // 高风险写操作 MUST 携带 Idempotency-Key，api.ts 自动生成
    )
    emit('applied', summary)
  } catch (err) {
    handleError(err, '执行管理动作失败')
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
    :title="action ? `执行管理动作：${actionLabel(action)}` : '执行管理动作'"
    :width="640"
    :mask-closable="false"
    :destroy-on-close="true"
    @cancel="handleClose"
  >
    <div v-if="instance && action" class="apply-action-modal">
      <Descriptions bordered size="small" :column="1">
        <DescriptionsItem label="实例 ID">
          <span class="mono">{{ instance.id }}</span>
        </DescriptionsItem>
        <DescriptionsItem label="当前状态">
          <Tag :color="instanceStatusColor(instance.state)">
            {{ instanceStatusLabel(instance.state) }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="当前版本号">
          {{ instance.revision }}
        </DescriptionsItem>
        <DescriptionsItem label="动作类型">
          <Tag :color="isHighRisk ? 'red' : 'blue'">
            {{ actionLabel(action) }}
          </Tag>
          <Tag v-if="isHighRisk" color="red">高风险</Tag>
        </DescriptionsItem>
      </Descriptions>

      <Form layout="vertical" class="action-form">
        <FormItem
          label="操作原因"
          required
          help="将记入审计日志，5-2000 字符"
        >
          <Input.TextArea
            v-model:value="form.reason"
            :rows="3"
            placeholder="说明本次管理动作的业务原因"
          />
        </FormItem>

        <FormItem
          v-if="needsMigrationPlan"
          label="迁移计划"
          help="迁移计划由迁移预览抽屉生成"
        >
          <Alert
            v-if="hasMigrationPlan && migrationPlan"
            type="success"
            show-icon
            :message="`已携带迁移计划：${migrationPlan.sourceDefinitionId} → ${migrationPlan.targetDefinitionId}`"
            :description="`活动节点映射数：${migrationPlan.activityMappings.length}，批大小：${migrationPlan.batchSize}`"
          />
          <Alert
            v-else
            type="error"
            show-icon
            message="缺少迁移计划"
            description="MIGRATE 动作 MUST 先通过迁移预览生成迁移计划。请关闭此对话框，点击「迁移（需预览）」按钮。"
          />
        </FormItem>

        <FormItem
          v-if="isHighRisk"
          label="预期结果"
          help="可选，描述预期达到的状态变化"
        >
          <Input.TextArea
            v-model:value="form.expectedOutcome"
            :rows="2"
            placeholder="例如：流程暂停后所有异步作业停止派发"
          />
        </FormItem>

        <FormItem
          v-if="isHighRisk"
          label="影响预览 ID"
          help="可选，关联 HighRiskOperationPort 生成的影响预览"
        >
          <Input
            v-model:value="form.impactPreviewId"
            placeholder="UUID 格式，可选"
          />
        </FormItem>

        <!-- 高风险操作风险确认 -->
        <Alert
          v-if="isHighRisk"
          type="error"
          show-icon
          message="高风险操作确认"
          description="MIGRATE/TERMINATE/MANUAL_COMPENSATE 为高风险操作，可能导致不可逆变更。提交即表示操作者已通过 HighRiskOperationPort 完成预览确认，并接受所有影响。"
        />

        <FormItem v-if="isHighRisk" required>
          <Checkbox v-model:checked="form.acknowledgedIrreversible">
            我已确认操作影响，并承担不可逆变更后果
          </Checkbox>
        </FormItem>

        <Alert
          v-if="action === 'MANUAL_COMPENSATE'"
          type="info"
          show-icon
          message="MANUAL_COMPENSATE 仅恢复流程编排状态"
          description="人工补偿动作不重复形成审批结论或业务状态变化，仅将流程从 INCIDENT 状态恢复至可继续编排状态。"
        />
      </Form>
    </div>

    <template #footer>
      <Button @click="handleClose">取消</Button>
      <Button
        type="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        danger
        @click="handleSubmit"
      >
        确认执行
      </Button>
    </template>
  </Modal>
</template>

<style scoped lang="scss">
.apply-action-modal {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .action-form {
    margin-top: 12px;
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
  }
}
</style>

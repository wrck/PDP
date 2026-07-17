<script setup lang="ts">
/**
 * 流程实例迁移预览抽屉（T090、FR-174、FR-168）。
 *
 * 对接 `POST /workflow-instances/{instanceId}/migration-previews`，
 * 生成迁移计划（含活动节点映射、不可逆点、补偿计划）。
 *
 * <p>MIGRATE 动作 MUST 先调用此端点生成计划，操作者基于计划评估风险后，
 * 通过 HighRiskOperationPort 确认，再调用 ApplyActionModal 执行迁移。
 */
import { computed, reactive, ref, watch } from 'vue'
import {
  Alert,
  Button,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  Space,
  Spin,
  Steps,
  Step,
  Table,
  Tag,
  Typography,
  TypographyTitle,
  message,
} from 'ant-design-vue'
import { workflowApi } from './api'
import { ApiError } from './http'
import {
  compensationStrategyLabel,
  type MigrationPlan,
} from './types'

const props = defineProps<{
  visible: boolean
  instanceId: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'plan-ready', plan: MigrationPlan): void
}>()

const currentStep = ref(0)
const loading = ref(false)
const plan = ref<MigrationPlan | null>(null)

const form = reactive({
  targetDefinitionId: '',
})

const canPreview = computed(
  () => /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(form.targetDefinitionId.trim()),
)

const activityMappingColumns = [
  {
    title: '源活动节点',
    dataIndex: 'sourceActivityKey',
    key: 'sourceActivityKey',
  },
  {
    title: '目标活动节点',
    dataIndex: 'targetActivityKey',
    key: 'targetActivityKey',
  },
]

const hasPointOfNoReturn = computed(() => !!plan.value?.pointOfNoReturn)
const isIrreversible = computed(
  () => hasPointOfNoReturn.value || plan.value?.compensationPlan.strategy === 'NONE',
)

/** 监听可见性，重置状态。 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      reset()
    }
  },
)

/** 重置抽屉状态。 */
function reset(): void {
  currentStep.value = 0
  plan.value = null
  form.targetDefinitionId = ''
  loading.value = false
}

/** 关闭抽屉。 */
function handleClose(): void {
  emit('update:visible', false)
}

/** 生成迁移计划。 */
async function handlePreview(): Promise<void> {
  if (!canPreview.value) {
    message.warning('请填写有效的目标定义 ID（UUID 格式）')
    return
  }
  loading.value = true
  plan.value = null
  try {
    const result = await workflowApi.previewMigration(props.instanceId, {
      targetDefinitionId: form.targetDefinitionId.trim(),
    })
    plan.value = result
    currentStep.value = 1
    message.success('迁移计划已生成，请审阅后确认')
  } catch (err) {
    handleError(err, '生成迁移计划失败')
  } finally {
    loading.value = false
  }
}

/** 确认迁移计划，进入 ApplyActionModal。 */
function handleConfirm(): void {
  if (!plan.value) return
  emit('plan-ready', plan.value)
}

/** 返回上一步修改。 */
function backToEdit(): void {
  currentStep.value = 0
  plan.value = null
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
    title="流程实例迁移预览"
    placement="right"
    :width="760"
    :mask-closable="false"
    :destroy-on-close="true"
    @close="handleClose"
  >
    <div class="migration-preview-drawer">
      <Steps :current="currentStep" size="small" class="steps">
        <Step title="选择目标定义" />
        <Step title="审阅迁移计划" />
      </Steps>

      <Spin :spinning="loading">
        <Form v-if="currentStep === 0" layout="vertical">
          <FormItem
            label="目标流程定义 ID"
            required
            help="UUID 格式，对应已部署的目标定义"
          >
            <Input
              v-model:value="form.targetDefinitionId"
              placeholder="例如 0189abc0-0000-7000-8000-000000000001"
            />
          </FormItem>
        </Form>

        <div v-else-if="plan" class="plan-detail">
          <Typography>
            <TypographyTitle :level="5">迁移计划概览</TypographyTitle>
          </Typography>

          <Descriptions bordered size="small" :column="1">
            <DescriptionsItem label="源定义 ID">
              <span class="mono">{{ plan.sourceDefinitionId }}</span>
            </DescriptionsItem>
            <DescriptionsItem label="目标定义 ID">
              <span class="mono">{{ plan.targetDefinitionId }}</span>
            </DescriptionsItem>
            <DescriptionsItem label="批大小">
              {{ plan.batchSize }}
            </DescriptionsItem>
            <DescriptionsItem label="不可逆点">
              <Tag v-if="plan.pointOfNoReturn" color="red">存在</Tag>
              <Tag v-else color="green">无</Tag>
              <span v-if="plan.pointOfNoReturn" class="point-detail">
                {{ plan.pointOfNoReturn }}
              </span>
            </DescriptionsItem>
          </Descriptions>

          <Typography>
            <TypographyTitle :level="5">活动节点映射</TypographyTitle>
          </Typography>
          <Table
            v-if="plan.activityMappings.length > 0"
            :columns="activityMappingColumns"
            :data-source="plan.activityMappings"
            :pagination="false"
            size="small"
            row-key="sourceActivityKey"
          />
          <Empty v-else description="无活动节点映射" />

          <Typography>
            <TypographyTitle :level="5">补偿计划</TypographyTitle>
          </Typography>
          <Descriptions bordered size="small" :column="1">
            <DescriptionsItem label="补偿策略">
              <Tag :color="plan.compensationPlan.strategy === 'NONE' ? 'red' : 'green'">
                {{ compensationStrategyLabel(plan.compensationPlan.strategy) }}
              </Tag>
            </DescriptionsItem>
            <DescriptionsItem label="负责角色">
              {{ plan.compensationPlan.responsibleRole }}
            </DescriptionsItem>
            <DescriptionsItem v-if="plan.compensationPlan.estimatedDurationSeconds != null" label="预估时长">
              {{ plan.compensationPlan.estimatedDurationSeconds }} 秒
            </DescriptionsItem>
            <DescriptionsItem v-if="plan.compensationPlan.runbookReference" label="运行手册">
              {{ plan.compensationPlan.runbookReference }}
            </DescriptionsItem>
            <DescriptionsItem v-if="plan.compensationPlan.steps.length > 0" label="补偿步骤">
              <ol class="compensation-steps">
                <li v-for="(step, idx) in plan.compensationPlan.steps" :key="idx">
                  {{ step }}
                </li>
              </ol>
            </DescriptionsItem>
          </Descriptions>

          <Alert
            v-if="isIrreversible"
            type="error"
            show-icon
            message="包含不可逆变更"
            description="本次迁移包含不可逆点或无补偿策略，执行后无法回滚。请确认操作影响并承担风险。"
          />
        </div>
      </Spin>
    </div>

    <div class="drawer-footer">
      <Space>
        <Button @click="handleClose">取消</Button>
        <Button v-if="currentStep === 1" @click="backToEdit">返回修改</Button>
        <Button
          v-if="currentStep === 0"
          type="primary"
          :loading="loading"
          :disabled="!canPreview"
          @click="handlePreview"
        >
          生成迁移计划
        </Button>
        <Button
          v-if="currentStep === 1"
          type="primary"
          :disabled="!plan"
          @click="handleConfirm"
        >
          确认计划并执行迁移
        </Button>
      </Space>
    </div>
  </Drawer>
</template>

<style scoped lang="scss">
.migration-preview-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .steps {
    margin-bottom: 8px;
  }

  .plan-detail {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .point-detail {
    margin-left: 8px;
  }

  .compensation-steps {
    margin: 0;
    padding-left: 20px;
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
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
}
</style>

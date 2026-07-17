<script setup lang="ts">
/**
 * 影响预览面板（FR-168、SC-039）。
 *
 * 展示高风险操作的影响预览：摘要、影响条目、不可逆点、补偿计划。
 * 当操作禁用时（如 P1 的 DATABASE_SWITCH），展示稳定禁用原因。
 */
import { computed } from 'vue'
import { Alert, Tag, Timeline, Typography, Descriptions, Empty } from 'ant-design-vue'
import {
  TimelineItem,
  TypographyParagraph,
  TypographyTitle,
  DescriptionsItem,
} from 'ant-design-vue'
import type {
  PreviewResult,
  ImpactItem,
  ImpactSeverity,
  CompensationStrategy,
} from './types'

const props = defineProps<{
  /** 预览结果（启用时含 preview+compensationPlan，禁用时含 disabledReason）。 */
  result: PreviewResult
}>()

const isDisabled = computed(() => props.result.disabledReason != null)
const preview = computed(() => props.result.preview ?? null)
const compensationPlan = computed(() => props.result.compensationPlan ?? null)

/** 严重度对应的 Tag 颜色。 */
function severityColor(severity: ImpactSeverity): string {
  switch (severity) {
    case 'INFO':
      return 'blue'
    case 'WARNING':
      return 'orange'
    case 'IRREVERSIBLE':
      return 'red'
    default:
      return 'default'
  }
}

/** 严重度中文名。 */
function severityLabel(severity: ImpactSeverity): string {
  switch (severity) {
    case 'INFO':
      return '信息'
    case 'WARNING':
      return '警告'
    case 'IRREVERSIBLE':
      return '不可逆'
    default:
      return severity
  }
}

/** 补偿策略中文名。 */
function strategyLabel(strategy: CompensationStrategy): string {
  switch (strategy) {
    case 'ROLLBACK':
      return '回滚'
    case 'REVERSE_SYNC':
      return '反向同步'
    case 'MANUAL':
      return '人工补偿'
    case 'NONE':
      return '无补偿（不可逆）'
    default:
      return strategy
  }
}

/** 格式化受影响对象数量。 */
function formatCount(item: ImpactItem): string {
  if (item.affectedObjectCount === 0) {
    return '无对象'
  }
  return `${item.affectedObjectCount} 个`
}

/** 格式化预估补偿时长（秒）。 */
function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return '未估算'
  if (seconds < 60) return `${seconds} 秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
  return `${Math.floor(seconds / 3600)} 小时 ${Math.floor((seconds % 3600) / 60)} 分钟`
}
</script>

<template>
  <div class="impact-preview-panel">
    <!-- 禁用操作提示（如 P1 的 DATABASE_SWITCH） -->
    <Alert
      v-if="isDisabled && result.disabledReason"
      type="warning"
      show-icon
      :message="`操作禁用（目标阶段：${result.disabledReason.targetPhase}）`"
      :description="result.disabledReason.summary"
      class="disabled-alert"
    >
      <template #action>
        <Tag color="red">{{ result.disabledReason.stableKey }}</Tag>
      </template>
    </Alert>

    <!-- 启用操作的预览 -->
    <template v-else-if="preview">
      <Typography>
        <TypographyParagraph>
          <strong>影响摘要：</strong>{{ preview.summary }}
        </TypographyParagraph>
      </Typography>

      <!-- 不可逆点提示 -->
      <Alert
        v-if="preview.pointOfNoReturn"
        type="error"
        show-icon
        message="包含不可逆点"
        :description="preview.pointOfNoReturn.description"
        class="irreversible-alert"
      >
        <template #action>
          <Tag color="red">{{ preview.pointOfNoReturn.stage }}</Tag>
        </template>
      </Alert>

      <!-- 影响条目列表 -->
      <TypographyTitle :level="5">影响条目</TypographyTitle>
      <Empty v-if="preview.items.length === 0" description="无影响条目" />
      <Timeline v-else>
        <TimelineItem
          v-for="(item, index) in preview.items"
          :key="index"
          :color="severityColor(item.severity)"
        >
          <template #dot>
            <Tag :color="severityColor(item.severity)">
              {{ severityLabel(item.severity) }}
            </Tag>
          </template>
          <div class="impact-item">
            <div class="impact-item-header">
              <span class="impact-item-category">{{ item.category }}</span>
              <span class="impact-item-count">{{ formatCount(item) }}</span>
            </div>
            <div class="impact-item-description">{{ item.description }}</div>
            <Tag v-if="item.irreversible" color="red">不可逆</Tag>
          </div>
        </TimelineItem>
      </Timeline>

      <!-- 补偿计划 -->
      <TypographyTitle v-if="compensationPlan" :level="5">补偿计划</TypographyTitle>
      <Descriptions
        v-if="compensationPlan"
        bordered
        :column="1"
        size="small"
        class="compensation-plan"
      >
        <DescriptionsItem label="补偿策略">
          <Tag :color="compensationPlan.strategy === 'NONE' ? 'red' : 'green'">
            {{ strategyLabel(compensationPlan.strategy) }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="负责角色">
          {{ compensationPlan.responsibleRole }}
        </DescriptionsItem>
        <DescriptionsItem label="预估时长">
          {{ formatDuration(compensationPlan.estimatedDurationSeconds) }}
        </DescriptionsItem>
        <DescriptionsItem v-if="compensationPlan.runbookReference" label="运行手册">
          {{ compensationPlan.runbookReference }}
        </DescriptionsItem>
        <DescriptionsItem v-if="compensationPlan.steps.length > 0" label="补偿步骤">
          <ol class="compensation-steps">
            <li v-for="(step, idx) in compensationPlan.steps" :key="idx">{{ step }}</li>
          </ol>
        </DescriptionsItem>
      </Descriptions>
    </template>
  </div>
</template>

<style scoped lang="scss">
.impact-preview-panel {
  .disabled-alert,
  .irreversible-alert {
    margin-bottom: 16px;
  }

  .impact-item {
    &-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;
    }

    &-category {
      font-weight: 600;
    }

    &-count {
      color: #666;
      font-size: 0.9em;
    }

    &-description {
      margin-bottom: 8px;
    }
  }

  .compensation-steps {
    margin: 0;
    padding-left: 20px;
  }
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'

import type { HighRiskOperationPreview } from './types'

const props = withDefaults(
  defineProps<{
    preview: HighRiskOperationPreview
    busy?: boolean
    canCompensate?: boolean
  }>(),
  {
    busy: false,
    canCompensate: false,
  },
)

const emit = defineEmits<{
  confirm: [
    payload: {
      previewId: string
      confirmationToken: string
    },
  ]
  compensate: [payload: { previewId: string }]
  refresh: []
}>()

const confirmationInput = ref('')
const irreversibleAcknowledged = ref(false)

const expired = computed(() => Date.parse(props.preview.expiresAt) <= Date.now())
const confirmDisabled = computed(
  () =>
    props.busy ||
    expired.value ||
    !props.preview.availability.enabled ||
    !irreversibleAcknowledged.value ||
    confirmationInput.value !== props.preview.confirmationPhrase,
)

function confirmOperation() {
  if (confirmDisabled.value) return
  emit('confirm', {
    previewId: props.preview.previewId,
    confirmationToken: props.preview.confirmationToken,
  })
}
</script>

<template>
  <section class="high-risk-operation" aria-labelledby="high-risk-operation-title">
    <header>
      <p class="eyebrow">{{ preview.operationType }}</p>
      <h2 id="high-risk-operation-title">高风险操作影响确认</h2>
      <p>{{ preview.targetSummary }}</p>
    </header>

    <div
      v-if="!preview.availability.enabled"
      class="blocked"
      role="alert"
    >
      <strong>{{ preview.availability.reasonCode }}</strong>
      <span>{{ preview.availability.reason }}</span>
    </div>

    <dl class="impact-grid">
      <template v-for="(count, name) in preview.affectedCounts" :key="name">
        <dt>{{ name }}</dt>
        <dd>{{ count }}</dd>
      </template>
    </dl>

    <ul v-if="preview.warnings.length" class="warnings">
      <li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li>
    </ul>

    <p>
      <strong>不可逆点：</strong>
      {{ preview.irreversibleAt }}
    </p>
    <p v-if="preview.compensation">
      <strong>补偿方式：</strong>
      {{ preview.compensation }}
    </p>

    <label>
      输入“{{ preview.confirmationPhrase }}”以确认
      <input
        v-model="confirmationInput"
        data-test="confirmation-input"
        autocomplete="off"
      />
    </label>
    <label class="acknowledgement">
      <input
        v-model="irreversibleAcknowledged"
        data-test="irreversible-ack"
        type="checkbox"
      />
      我已理解不可逆点和补偿边界
    </label>

    <p v-if="expired" class="blocked" role="alert">
      影响预览已过期，必须重新获取后才能执行。
    </p>

    <div class="actions">
      <button
        data-test="confirm-operation"
        type="button"
        :disabled="confirmDisabled"
        @click="confirmOperation"
      >
        {{ busy ? '执行中…' : '确认执行' }}
      </button>
      <button v-if="expired" type="button" @click="emit('refresh')">
        重新预览
      </button>
      <button
        v-if="preview.compensation && canCompensate"
        type="button"
        @click="emit('compensate', { previewId: preview.previewId })"
      >
        发起补偿
      </button>
    </div>
  </section>
</template>

<style scoped>
.high-risk-operation {
  display: grid;
  gap: 16px;
  max-width: 720px;
  padding: 24px;
  border: 1px solid #f0a020;
  border-radius: 12px;
  background: #fffaf0;
}

.eyebrow {
  margin: 0;
  color: #8a4b08;
  font-weight: 700;
}

.blocked {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  color: #8a1c1c;
  background: #fff1f0;
}

.impact-grid {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) auto;
  gap: 8px 16px;
  margin: 0;
}

.impact-grid dd {
  margin: 0;
  font-weight: 700;
}

.warnings {
  margin: 0;
  color: #8a4b08;
}

label {
  display: grid;
  gap: 8px;
}

input {
  padding: 8px 12px;
}

.acknowledgement {
  display: flex;
  align-items: center;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

button {
  padding: 8px 16px;
}
</style>

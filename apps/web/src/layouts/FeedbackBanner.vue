<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'info' | 'success' | 'warning' | 'error'
    title: string
    detail?: string
    traceId?: string
  }>(),
  { type: 'info', detail: undefined, traceId: undefined },
)
</script>

<template>
  <section class="feedback-banner" :class="`feedback-banner--${type}`" role="status">
    <strong>{{ title }}</strong>
    <span v-if="detail">{{ detail }}</span>
    <code v-if="traceId">traceId: {{ traceId }}</code>
  </section>
</template>

<script setup lang="ts">
import type { ObjectSchema } from './types'

defineProps<{
  schema: ObjectSchema
  value: Record<string, unknown>
}>()

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}
</script>

<template>
  <dl class="schema-detail">
    <template v-for="(property, name) in schema.properties" :key="name">
      <dt>{{ property.title ?? name }}</dt>
      <dd>{{ display(value[name]) }}</dd>
    </template>
  </dl>
</template>

<script setup lang="ts">
import type { ObjectSchema } from './types'

defineProps<{
  schema: ObjectSchema
  rows: Array<Record<string, unknown>>
  rowKey?: string
}>()
</script>

<template>
  <div class="schema-table-scroll">
    <table class="schema-table">
      <thead>
        <tr>
          <th v-for="(property, name) in schema.properties" :key="name" scope="col">
            {{ property.title ?? name }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, index) in rows"
          :key="String(row[rowKey ?? 'id'] ?? index)"
        >
          <td v-for="(_property, name) in schema.properties" :key="name">
            {{ row[name] ?? '—' }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

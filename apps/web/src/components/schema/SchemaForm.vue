<script setup lang="ts">
import type { ObjectSchema } from './types'

const props = defineProps<{
  schema: ObjectSchema
  modelValue: Record<string, unknown>
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

function updateValue(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value })
}
</script>

<template>
  <fieldset class="schema-form" :disabled="disabled">
    <legend v-if="schema.title">{{ schema.title }}</legend>
    <label v-for="(property, name) in schema.properties" :key="name">
      <span>
        {{ property.title ?? name }}
        <abbr v-if="schema.required?.includes(name)" title="必填">*</abbr>
      </span>
      <select
        v-if="property.enum"
        :value="modelValue[name]"
        @change="updateValue(name, ($event.target as HTMLSelectElement).value)"
      >
        <option v-for="option in property.enum" :key="String(option)" :value="option">
          {{ option }}
        </option>
      </select>
      <input
        v-else-if="property.type === 'boolean'"
        type="checkbox"
        :checked="Boolean(modelValue[name])"
        @change="updateValue(name, ($event.target as HTMLInputElement).checked)"
      />
      <input
        v-else
        :type="property.type === 'number' || property.type === 'integer' ? 'number' : 'text'"
        :value="modelValue[name] ?? ''"
        :readonly="property.readOnly"
        :required="schema.required?.includes(name)"
        @input="updateValue(name, ($event.target as HTMLInputElement).value)"
      />
      <small v-if="property.description">{{ property.description }}</small>
    </label>
  </fieldset>
</template>

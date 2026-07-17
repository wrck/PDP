<script setup lang="ts">
/**
 * JSON Schema 表单组件（T093、FR-009、FR-063、FR-066）。
 *
 * 基于 {@link ObjectSchema} 渲染表单，支持：
 * <ul>
 *   <li>字段分组（按 field.group 分区展示）；</li>
 *   <li>权限控制（v-permission 指令隐藏未授权字段，FR-063）；</li>
 *   <li>敏感字段独立授权标识（FR-066）；</li>
 *   <li>JSON Schema 字段级校验（FR-009）；</li>
 *   <li>只读 / 写一次字段在编辑模式下禁用。</li>
 * </ul>
 *
 * <p><strong>安全边界</strong>：前端权限指令仅用于 UI 显隐，
 * 写操作 MUST 由后端二次复核（FR-068）。
 */
import { computed, reactive, watch } from 'vue'
import {
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  InputPassword as AInputPassword,
  InputNumber as AInputNumber,
  Textarea as ATextarea,
  Select as ASelect,
  Switch as ASwitch,
  DatePicker as ADatePicker,
  TimePicker as ATimePicker,
  RadioGroup as ARadioGroup,
  Radio as ARadio,
  CheckboxGroup as ACheckboxGroup,
  Checkbox as ACheckbox,
  Button as AButton,
  Space as ASpace,
  Tag as ATag,
  Tooltip as ATooltip,
  Row as ARow,
  Col as ACol,
  Divider as ADivider,
} from 'ant-design-vue'
import { LockOutlined } from '@ant-design/icons-vue'
import {
  inferWidget,
  isSensitiveField,
  type FieldSchema,
  type FormErrors,
  type FormSubmitPayload,
  type FormValue,
  type ObjectSchema,
} from './types'
import { validateObject } from './validation'

const props = withDefaults(
  defineProps<{
    /** 对象元模型。 */
    schema: ObjectSchema
    /** 表单初始值（v-model:value）。 */
    value?: FormValue
    /** 模式：create 创建、edit 编辑、view 查看。 */
    mode?: 'create' | 'edit' | 'view'
    /** 提交按钮文本。 */
    submitText?: string
    /** 取消按钮文本。 */
    cancelText?: string
    /** 是否禁用全部字段。 */
    disabled?: boolean
    /** 表单标签布局。 */
    labelLayout?: 'horizontal' | 'vertical' | 'inline'
  }>(),
  {
    value: () => ({}),
    mode: 'create',
    submitText: '提交',
    cancelText: '取消',
    disabled: false,
    labelLayout: 'horizontal',
  },
)

const emit = defineEmits<{
  (e: 'update:value', value: FormValue): void
  (e: 'submit', payload: FormSubmitPayload): void
  (e: 'cancel'): void
}>()

/** 内部表单数据。 */
const formValue = reactive<FormValue>({ ...props.value })
/** 校验错误。 */
const errors = reactive<FormErrors>({})
/** 是否查看模式。 */
const isView = computed(() => props.mode === 'view')

watch(
  () => props.value,
  (v) => {
    Object.assign(formValue, v)
    Object.keys(errors).forEach((k) => delete errors[k])
  },
  { deep: true },
)

watch(
  formValue,
  (v) => {
    emit('update:value', { ...v })
  },
  { deep: true },
)

/** 字段是否可编辑（受模式、readOnly、writeOnce、disabled 控制）。 */
function isFieldEditable(field: FieldSchema): boolean {
  if (props.disabled || isView.value) return false
  if (field.readOnly) return false
  if (field.writeOnce && props.mode === 'edit') return false
  return true
}

/** 按分组组织字段。 */
const groupedFields = computed(() => {
  const groups = new Map<string, FieldSchema[]>()
  for (const field of props.schema.fields) {
    const g = field.group ?? '_default'
    if (!groups.has(g)) groups.set(g, [])
    groups.get(g)!.push(field)
  }
  return Array.from(groups.entries()).map(([key, fields]) => ({
    key,
    label: key === '_default' ? props.schema.label : key,
    fields,
  }))
})

/** 触发字段级校验。 */
function validate(): boolean {
  const result = validateObject(props.schema.fields, formValue)
  Object.keys(errors).forEach((k) => delete errors[k])
  Object.assign(errors, result.errors)
  return result.valid
}

/** 提交表单。 */
function handleSubmit() {
  const valid = validate()
  emit('submit', {
    value: { ...formValue },
    valid,
    errors: { ...errors },
  })
}

/** 字段值变更。 */
function onFieldChange(key: string, v: unknown) {
  formValue[key] = v
  // 清除该字段错误
  if (errors[key]) delete errors[key]
}

defineExpose({ validate, formValue })
</script>

<template>
  <AForm :layout="labelLayout" :model="formValue" class="schema-form">
    <template v-for="group in groupedFields" :key="group.key">
      <ADivider v-if="group.key !== '_default'" orientation="left" plain>
        {{ group.label }}
      </ADivider>
      <ARow :gutter="16">
        <ACol
          v-for="field in group.fields"
          :key="field.key"
          :span="field.span ?? 24"
        >
          <AFormItem
            :label="field.label"
            :required="field.required && !isView"
            :validate-status="errors[field.key] ? 'error' : undefined"
            :help="errors[field.key] ?? field.help"
            v-permission="field.permission"
          >
            <template #label>
              <span>{{ field.label }}</span>
              <ATooltip v-if="isSensitiveField(field)" title="敏感字段，需独立授权">
                <LockOutlined class="schema-form__sensitive-icon" />
              </ATooltip>
            </template>

            <!-- 文本输入 -->
            <AInput
              v-if="inferWidget(field) === 'input'"
              :value="formValue[field.key] as string"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              :readonly="isView"
              allow-clear
              @update:value="(v: string) => onFieldChange(field.key, v)"
            />

            <!-- 密码 -->
            <AInputPassword
              v-else-if="inferWidget(field) === 'password'"
              :value="formValue[field.key] as string"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              @update:value="(v: string) => onFieldChange(field.key, v)"
            />

            <!-- 多行文本 -->
            <ATextarea
              v-else-if="inferWidget(field) === 'textarea'"
              :value="formValue[field.key] as string"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              :readonly="isView"
              :rows="4"
              allow-clear
              @update:value="(v: string) => onFieldChange(field.key, v)"
            />

            <!-- 数值 -->
            <AInputNumber
              v-else-if="inferWidget(field) === 'number'"
              :value="formValue[field.key] as number"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              :min="field.minimum"
              :max="field.maximum"
              style="width: 100%"
              @update:value="(v: number | null) => onFieldChange(field.key, v ?? 0)"
            />

            <!-- 开关 -->
            <ASwitch
              v-else-if="inferWidget(field) === 'switch'"
              :checked="(formValue[field.key] as boolean) ?? false"
              :disabled="!isFieldEditable(field)"
              @update:checked="(v: boolean) => onFieldChange(field.key, v)"
            />

            <!-- 下拉选择 -->
            <ASelect
              v-else-if="inferWidget(field) === 'select'"
              :value="formValue[field.key]"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              :options="(field.enum ?? []).map((o) => ({ label: o.label, value: o.value }))"
              allow-clear
              @update:value="(v: unknown) => onFieldChange(field.key, v)"
            />

            <!-- 单选 -->
            <ARadioGroup
              v-else-if="inferWidget(field) === 'radio'"
              :value="formValue[field.key]"
              :disabled="!isFieldEditable(field)"
              @update:value="(v: unknown) => onFieldChange(field.key, v)"
            >
              <ARadio
                v-for="opt in field.enum ?? []"
                :key="String(opt.value)"
                :value="opt.value"
              >
                {{ opt.label }}
              </ARadio>
            </ARadioGroup>

            <!-- 多选 -->
            <ACheckboxGroup
              v-else-if="inferWidget(field) === 'checkbox'"
              :value="(formValue[field.key] as Array<string | number>) ?? []"
              :disabled="!isFieldEditable(field)"
              @update:value="(v: Array<string | number>) => onFieldChange(field.key, v)"
            >
              <ACheckbox
                v-for="opt in field.enum ?? []"
                :key="String(opt.value)"
                :value="opt.value"
              >
                {{ opt.label }}
              </ACheckbox>
            </ACheckboxGroup>

            <!-- 日期选择 -->
            <ADatePicker
              v-else-if="inferWidget(field) === 'date-picker'"
              :value="formValue[field.key] as string"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              style="width: 100%"
              @update:value="(v: unknown) => onFieldChange(field.key, v)"
            />

            <!-- 时间选择 -->
            <ATimePicker
              v-else-if="inferWidget(field) === 'time-picker'"
              :value="formValue[field.key] as string"
              :placeholder="field.placeholder"
              :disabled="!isFieldEditable(field)"
              style="width: 100%"
              @update:value="(v: unknown) => onFieldChange(field.key, v)"
            />

            <!-- 兜底：纯文本展示 -->
            <span v-else>{{ formValue[field.key] ?? '-' }}</span>

            <ATag v-if="field.writeOnce && mode === 'edit'" color="orange" class="schema-form__write-once">
              写一次
            </ATag>
          </AFormItem>
        </ACol>
      </ARow>
    </template>

    <AFormItem v-if="!isView" :wrapper-col="{ offset: 0 }">
      <ASpace>
        <AButton type="primary" @click="handleSubmit">{{ submitText }}</AButton>
        <AButton @click="emit('cancel')">{{ cancelText }}</AButton>
      </ASpace>
    </AFormItem>
  </AForm>
</template>

<style scoped lang="scss">
.schema-form {
  &__sensitive-icon {
    margin-left: 4px;
    color: var(--ant-color-warning, #faad14);
    font-size: 12px;
  }

  &__write-once {
    margin-left: 8px;
  }
}
</style>

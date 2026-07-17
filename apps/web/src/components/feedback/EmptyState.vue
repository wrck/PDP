<script setup lang="ts">
/**
 * 空状态组件（T094）。
 *
 * 统一空数据、无权限、网络错误等场景的占位展示，
 * 提供图标、标题、描述和操作按钮。
 */
import { Button as AButton, Space as ASpace } from 'ant-design-vue'
import {
  InboxOutlined,
  FrownOutlined,
  DisconnectOutlined,
  LockOutlined,
} from '@ant-design/icons-vue'
import { computed } from 'vue'

type EmptyVariant = 'default' | 'no-data' | 'no-permission' | 'network-error' | 'error'

const props = withDefaults(
  defineProps<{
    /** 空状态类型。 */
    variant?: EmptyVariant
    /** 自定义标题。 */
    title?: string
    /** 自定义描述。 */
    description?: string
    /** 是否显示操作区。 */
    showAction?: boolean
    /** 操作按钮文本。 */
    actionText?: string
  }>(),
  {
    variant: 'default',
    showAction: false,
    actionText: '刷新',
  },
)

const emit = defineEmits<{
  (e: 'action'): void
}>()

const defaultConfig = computed(() => {
  switch (props.variant) {
    case 'no-data':
      return {
        icon: InboxOutlined,
        title: props.title ?? '暂无数据',
        description: props.description ?? '当前条件下没有匹配的数据',
      }
    case 'no-permission':
      return {
        icon: LockOutlined,
        title: props.title ?? '无访问权限',
        description: props.description ?? '您没有访问此资源的权限，请联系管理员',
      }
    case 'network-error':
      return {
        icon: DisconnectOutlined,
        title: props.title ?? '网络异常',
        description: props.description ?? '网络连接失败，请检查网络后重试',
      }
    case 'error':
      return {
        icon: FrownOutlined,
        title: props.title ?? '加载失败',
        description: props.description ?? '数据加载失败，请稍后重试',
      }
    default:
      return {
        icon: InboxOutlined,
        title: props.title ?? '暂无数据',
        description: props.description ?? '',
      }
  }
})

const showAction = computed(() => props.showAction || props.variant === 'network-error' || props.variant === 'error')
</script>

<template>
  <div class="empty-state">
    <component :is="defaultConfig.icon" class="empty-state__icon" />
    <div class="empty-state__title">{{ defaultConfig.title }}</div>
    <div v-if="defaultConfig.description" class="empty-state__description">
      {{ defaultConfig.description }}
    </div>
    <div v-if="showAction" class="empty-state__action">
      <ASpace>
        <slot name="action">
          <AButton type="primary" @click="emit('action')">{{ actionText }}</AButton>
        </slot>
      </ASpace>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as tokens;

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: tokens.$pdp-spacing-xxl tokens.$pdp-spacing-lg;
  text-align: center;

  &__icon {
    font-size: 48px;
    color: tokens.$pdp-color-text-quaternary;
    margin-bottom: tokens.$pdp-spacing-md;
  }

  &__title {
    font-size: tokens.$pdp-font-size-lg;
    color: tokens.$pdp-color-text;
    font-weight: 500;
    margin-bottom: tokens.$pdp-spacing-xs;
  }

  &__description {
    font-size: tokens.$pdp-font-size-sm;
    color: tokens.$pdp-color-text-secondary;
    max-width: 400px;
    line-height: tokens.$pdp-line-height-base;
  }

  &__action {
    margin-top: tokens.$pdp-spacing-lg;
  }
}
</style>

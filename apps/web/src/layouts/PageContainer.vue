<script setup lang="ts">
/**
 * 页面容器（T094）。
 *
 * 统一页面外壳：标题、描述、操作区、内容区。
 * 所有业务页面应使用此容器包装，保证视觉与交互一致性。
 */
import { computed } from 'vue'
import { Space as ASpace, Typography as ATypography, Divider as ADivider } from 'ant-design-vue'

const props = withDefaults(
  defineProps<{
    /** 页面标题。 */
    title?: string
    /** 页面描述。 */
    description?: string
    /** 是否显示标题区分隔线。 */
    divided?: boolean
    /** 内容区背景。 */
    background?: 'default' | 'transparent' | 'elevated'
    /** 内容区内边距。 */
    padding?: 'none' | 'small' | 'default' | 'large'
    /** 是否撑满高度。 */
    fullHeight?: boolean
  }>(),
  {
    divided: true,
    background: 'default',
    padding: 'default',
    fullHeight: false,
  },
)

const contentClass = computed(() => [
  'page-container__content',
  `page-container__content--bg-${props.background}`,
  `page-container__content--padding-${props.padding}`,
  { 'page-container__content--full': props.fullHeight },
])
</script>

<template>
  <div class="page-container">
    <div v-if="title || $slots.title || $slots.actions" class="page-container__header">
      <div class="page-container__header-text">
        <slot name="title">
          <ATypography v-if="title" class="page-container__title">{{ title }}</ATypography>
        </slot>
        <ATypography v-if="description" class="page-container__description">
          {{ description }}
        </ATypography>
      </div>
      <div v-if="$slots.actions" class="page-container__actions">
        <ASpace>
          <slot name="actions" />
        </ASpace>
      </div>
    </div>

    <ADivider v-if="divided && (title || $slots.title || $slots.actions)" class="page-container__divider" />

    <div :class="contentClass">
      <slot />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as tokens;

.page-container {
  &__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: tokens.$pdp-spacing-md;
    flex-wrap: wrap;
  }

  &__header-text {
    flex: 1;
    min-width: 0;
  }

  &__title {
    margin: 0 !important;
    font-size: tokens.$pdp-font-size-xl !important;
    font-weight: 600 !important;
    color: tokens.$pdp-color-text !important;
    line-height: tokens.$pdp-line-height-heading !important;
  }

  &__description {
    margin-top: tokens.$pdp-spacing-xs !important;
    margin-bottom: 0 !important;
    font-size: tokens.$pdp-font-size-sm !important;
    color: tokens.$pdp-color-text-secondary !important;
  }

  &__actions {
    flex-shrink: 0;
  }

  &__divider {
    margin: tokens.$pdp-spacing-md 0 !important;
  }

  &__content {
    border-radius: tokens.$pdp-radius-lg;
    transition: background-color 0.2s;

    &--bg-default {
      background: tokens.$pdp-color-bg-container;
    }

    &--bg-transparent {
      background: transparent;
    }

    &--bg-elevated {
      background: tokens.$pdp-color-bg-elevated;
      box-shadow: tokens.$pdp-shadow-sm;
    }

    &--padding-none {
      padding: 0;
    }

    &--padding-small {
      padding: tokens.$pdp-spacing-sm;
    }

    &--padding-default {
      padding: tokens.$pdp-spacing-lg;
    }

    &--padding-large {
      padding: tokens.$pdp-spacing-xl;
    }

    &--full {
      min-height: calc(100vh - #{tokens.$pdp-layout-header-height} - #{tokens.$pdp-spacing-lg * 2});
    }
  }
}
</style>

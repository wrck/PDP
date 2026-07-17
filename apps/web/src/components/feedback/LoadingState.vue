<script setup lang="ts">
/**
 * 加载状态组件（T094）。
 *
 * 提供统一的加载占位展示，支持三种形态：
 * - spinner：旋转图标（默认）；
 * - skeleton：骨架屏（用于表格、详情）；
 * - dots：三点动画（用于内联）。
 */
import { Skeleton as ASkeleton, Spin as ASpin } from 'ant-design-vue'

withDefaults(
  defineProps<{
    /** 加载形态。 */
    variant?: 'spinner' | 'skeleton' | 'dots'
    /** 提示文本（仅 spinner/dots）。 */
    tip?: string
    /** 是否占满父容器高度。 */
    fullscreen?: boolean
    /** 骨架屏行数（仅 skeleton）。 */
    rows?: number
    /** 骨架屏是否显示头像（仅 skeleton）。 */
    avatar?: boolean
  }>(),
  {
    variant: 'spinner',
    tip: '加载中...',
    fullscreen: false,
    rows: 5,
    avatar: false,
  },
)
</script>

<template>
  <div
    class="loading-state"
    :class="{
      'loading-state--fullscreen': fullscreen,
    }"
  >
    <template v-if="variant === 'spinner'">
      <ASpin :tip="tip" size="large" />
    </template>

    <template v-else-if="variant === 'skeleton'">
      <ASkeleton :active="true" :paragraph="{ rows }" :avatar="avatar" />
    </template>

    <template v-else-if="variant === 'dots'">
      <div class="loading-state__dots">
        <span class="loading-state__dot" />
        <span class="loading-state__dot" />
        <span class="loading-state__dot" />
        <span v-if="tip" class="loading-state__dots-tip">{{ tip }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as tokens;

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: tokens.$pdp-spacing-xl;

  &--fullscreen {
    position: fixed;
    inset: 0;
    background: rgba(255, 255, 255, 0.75);
    z-index: tokens.$pdp-z-modal;
  }

  &__dots {
    display: inline-flex;
    align-items: center;
    gap: tokens.$pdp-spacing-xs;
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: tokens.$pdp-color-primary;
    animation: loading-state-pulse 1.2s ease-in-out infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }

  &__dots-tip {
    margin-left: tokens.$pdp-spacing-xs;
    color: tokens.$pdp-color-text-secondary;
    font-size: tokens.$pdp-font-size-sm;
  }
}

@keyframes loading-state-pulse {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>

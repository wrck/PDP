<script setup lang="ts">
/**
 * 空白布局（T094）。
 *
 * 用于登录、404、错误页等独立场景，不渲染侧边栏和顶部导航。
 * 仅居中展示内容，并提供品牌标识。
 */
import { Layout as ALayout, LayoutContent as ALayoutContent } from 'ant-design-vue'

withDefaults(
  defineProps<{
    /** 是否显示品牌标识。 */
    brand?: boolean
    /** 品牌名称。 */
    brandName?: string
  }>(),
  {
    brand: false,
    brandName: 'PDP',
  },
)
</script>

<template>
  <ALayout class="blank-layout">
    <ALayoutContent class="blank-layout__content">
      <div v-if="brand" class="blank-layout__brand">{{ brandName }}</div>
      <div class="blank-layout__body">
        <slot />
      </div>
    </ALayoutContent>
  </ALayout>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as tokens;

.blank-layout {
  min-height: 100vh;
  background: linear-gradient(
    135deg,
    tokens.$pdp-color-primary-bg 0%,
    tokens.$pdp-color-bg-layout 100%
  );

  &__content {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    padding: tokens.$pdp-spacing-xl;
  }

  &__brand {
    margin-bottom: tokens.$pdp-spacing-xl;
    color: tokens.$pdp-color-primary;
    font-size: tokens.$pdp-font-size-3xl;
    font-weight: 700;
    letter-spacing: 2px;
  }

  &__body {
    width: 100%;
    max-width: 480px;
  }
}
</style>

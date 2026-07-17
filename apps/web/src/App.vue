<script setup lang="ts">
// PDP 根组件（T094 更新）：
// - 通过 a-config-provider 注入 Ant Design Vue 中文 locale 与主题令牌
// - 服务器事实由 TanStack Vue Query 管理；本组件不持有业务状态
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { themeConfig } from '@/styles/theme'
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

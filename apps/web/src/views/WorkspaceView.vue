<script setup lang="ts">
// WorkspaceView 占位骨架
// 后续按业务特性组织：widgets（页面组合）、entities（业务模型）、shared（跨特性运行时）
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
</script>

<template>
  <a-layout class="workspace-view">
    <a-layout-header>PDP 企业级项目交付管理平台</a-layout-header>
    <a-layout-content>
      <a-typography>
        <a-typography-title :level="2">工作空间</a-typography-title>
        <a-typography-paragraph>
          当前用户：{{ auth.user?.displayName ?? '未登录' }}
        </a-typography-paragraph>
      </a-typography>
    </a-layout-content>
  </a-layout>
</template>

<style scoped lang="scss">
.workspace-view {
  min-height: 100vh;
}
</style>

<script setup lang="ts">
// NotFoundView 占位骨架
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  router.push('/workspace')
}
</script>

<template>
  <a-result status="404" title="404" sub-title="抱歉，您访问的页面不存在。">
    <template #extra>
      <a-button type="primary" @click="goHome">返回工作空间</a-button>
    </template>
  </a-result>
</template>

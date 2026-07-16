<script setup lang="ts">
// LoginView 占位骨架
// 真实实现接入 OIDC（FR-063~FR-069）后替换
import { reactive } from 'vue'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()

const form = reactive({
  username: '',
  password: ''
})

function handleSubmit(): void {
  // 占位：仅以输入值生成模拟 token，演示 auth store 写入
  const mockUser = {
    id: form.username,
    name: form.username,
    displayName: form.username
  }
  auth.login(`mock-token:${form.username}`, mockUser)
}
</script>

<template>
  <div class="login-view">
    <a-card class="login-card" title="PDP 登录">
      <a-form layout="vertical" @submit.prevent="handleSubmit">
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="form.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="form.password" placeholder="请输入密码" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block>登录</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped lang="scss">
.login-view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f0f2f5;
}

.login-card {
  width: 360px;
}
</style>

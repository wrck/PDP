import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 已认证用户的最小客户端镜像
 * 服务器事实仍由 TanStack Vue Query 管理；此处仅保存跨页面所需的 token 与用户基本字段
 */
export interface AuthUser {
  id: string
  name: string
  displayName: string
}

/**
 * auth store - 占位实现
 * 真实登录由 OIDC 接入（FR-063~FR-069），这里仅提供最小骨架：
 * - token：用于在 axios 拦截器中附加 Authorization 头
 * - user：跨页面客户端状态，避免每个页面重复请求当前用户
 * - login/logout：由 identity 模块对接完成后填充
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<AuthUser | null>(null)

  const isAuthenticated = computed(() => token.value !== null && user.value !== null)

  function login(newToken: string, newUser: AuthUser): void {
    token.value = newToken
    user.value = newUser
  }

  function logout(): void {
    token.value = null
    user.value = null
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    logout
  }
})

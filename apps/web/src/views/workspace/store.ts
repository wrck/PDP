/**
 * 工作空间上下文 Pinia store（T108、FR-003）。
 *
 * 管理当前选中的工作空间 ID 与详情。当前工作空间 ID 会通过 localStorage
 * 持久化，并作为 `X-Workspace-Id` 头由全局 httpClient 拦截器自动注入
 * （见 `@/api/http.ts` 的 `currentWorkspaceId()`）。
 *
 * <p><strong>边界</strong>：
 * <ul>
 *   <li>服务器事实仍由 TanStack Vue Query 管理，本 store 仅持有跨页面共享的上下文；
 *   <li>切换工作空间后必须刷新路由或调用 `loadCurrent()` 以同步最新详情。
 * </ul>
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { workspaceApi } from './api'
import type { Workspace } from './types'

const STORAGE_KEY_ID = 'pdp.workspaceId'
const STORAGE_KEY_NAME = 'pdp.workspaceName'

/** 读取 localStorage 中的工作空间 ID。 */
function readStoredId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY_ID)
  } catch {
    return null
  }
}

/** 读取 localStorage 中的工作空间名称（仅用于标题展示）。 */
function readStoredName(): string {
  try {
    return localStorage.getItem(STORAGE_KEY_NAME) ?? ''
  } catch {
    return ''
  }
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const currentId = ref<string | null>(readStoredId())
  const current = ref<Workspace | null>(null)
  const currentName = ref<string>(readStoredName())
  const loading = ref(false)
  const loadError = ref<string | null>(null)

  const hasCurrent = computed(() => currentId.value !== null)

  /** 设置当前工作空间（切换/选中时调用）。 */
  function setCurrent(workspace: Workspace): void {
    currentId.value = workspace.id
    current.value = workspace
    currentName.value = workspace.name
    try {
      localStorage.setItem(STORAGE_KEY_ID, workspace.id)
      localStorage.setItem(STORAGE_KEY_NAME, workspace.name)
    } catch {
      // localStorage 不可用时静默降级（隐私模式等）
    }
  }

  /** 仅设置 ID（用于路由直达场景，详情需后续 ensureLoaded）。 */
  function setCurrentId(id: string, name?: string): void {
    currentId.value = id
    current.value = null
    if (name) {
      currentName.value = name
    }
    try {
      localStorage.setItem(STORAGE_KEY_ID, id)
      if (name) localStorage.setItem(STORAGE_KEY_NAME, name)
    } catch {
      // 静默降级
    }
  }

  /** 清除当前工作空间（登出时调用）。 */
  function clear(): void {
    currentId.value = null
    current.value = null
    currentName.value = ''
    try {
      localStorage.removeItem(STORAGE_KEY_ID)
      localStorage.removeItem(STORAGE_KEY_NAME)
    } catch {
      // 静默降级
    }
  }

  /** 从后端加载当前工作空间详情。 */
  async function loadCurrent(): Promise<void> {
    if (!currentId.value) return
    loading.value = true
    loadError.value = null
    try {
      const ws = await workspaceApi.getWorkspace(currentId.value)
      current.value = ws
      currentName.value = ws.name
      try {
        localStorage.setItem(STORAGE_KEY_NAME, ws.name)
      } catch {
        // 静默降级
      }
    } catch (err) {
      loadError.value = err instanceof Error ? err.message : '加载工作空间详情失败'
      // 404 视为跨工作空间访问或已被归档：清除当前选择
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const status = (err as any)?.status
      if (status === 404) {
        clear()
      }
    } finally {
      loading.value = false
    }
  }

  /** 确保当前工作空间详情已加载（避免重复请求）。 */
  async function ensureLoaded(): Promise<void> {
    if (!currentId.value) return
    if (current.value) return
    await loadCurrent()
  }

  return {
    currentId,
    current,
    currentName,
    loading,
    loadError,
    hasCurrent,
    setCurrent,
    setCurrentId,
    clear,
    loadCurrent,
    ensureLoaded,
  }
})

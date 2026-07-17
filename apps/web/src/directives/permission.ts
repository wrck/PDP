/**
 * 权限指令 v-permission（T092、FR-063、FR-067）。
 *
 * 用于在模板中声明式控制元素的显隐与禁用，依据当前操作者的权限快照。
 *
 * <p><strong>用法</strong>：
 * <ul>
 *   <li>单权限：{@code v-permission="'workflow.definition.deploy'"}；</li>
 *   <li>任一权限（OR）：{@code v-permission.any="['permission.a', 'permission.b']"}；</li>
 *   <li>全部权限（AND）：{@code v-permission.all="['permission.a', 'permission.b']"}；</li>
 *   <li>角色判断：{@code v-permission.role="'WORKSPACE_ADMIN'"}；</li>
 *   <li>禁用模式（保留 DOM，禁用交互）：
 *       {@code v-permission.disable="'permission.a'"}。</li>
 * </ul>
 *
 * <p><strong>安全边界</strong>：
 * 前端权限指令仅用于 UI 显隐与交互禁用，<strong>不作为安全边界</strong>。
 * 所有写操作 MUST 由后端二次复核权限，前端绕过不能导致越权操作。
 *
 * <p><strong>权限撤销实时性（FR-068）</strong>：
 * 指令通过响应式依赖 {@link usePermissionStore}，权限快照刷新后自动重新评估。
 * 快照过期（默认 60 秒）后下次访问会触发刷新。
 */
import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores/permission'
import type { PermissionKey, RoleKey } from '@/stores/permission'

/** 指令未通过时的默认处理：从 DOM 中移除元素。 */
function removeElement(el: HTMLElement): void {
  if (el.parentNode) {
    el.parentNode.removeChild(el)
  }
}

/** 指令未通过时的禁用处理：保留 DOM，禁用交互。 */
function disableElement(el: HTMLElement): void {
  el.setAttribute('disabled', 'true')
  el.setAttribute('aria-disabled', 'true')
  el.style.pointerEvents = 'none'
  el.style.opacity = '0.5'
  el.style.cursor = 'not-allowed'
}

/** 指令通过时的恢复处理：清除禁用样式。 */
function enableElement(el: HTMLElement): void {
  el.removeAttribute('disabled')
  el.removeAttribute('aria-disabled')
  el.style.pointerEvents = ''
  el.style.opacity = ''
  el.style.cursor = ''
}

/** 评估权限值（单权限或权限数组）。 */
function evaluatePermissions(
  binding: DirectiveBinding,
  mode: 'any' | 'all' = 'any',
): boolean {
  const store = usePermissionStore()
  const value = binding.value

  if (typeof value === 'string') {
    return store.hasPermission(value)
  }

  if (Array.isArray(value)) {
    const permissions = value as PermissionKey[]
    if (permissions.length === 0) return true
    return mode === 'all'
      ? store.hasAllPermissions(...permissions)
      : store.hasAnyPermission(...permissions)
  }

  // 非法值类型：默认拒绝
  // eslint-disable-next-line no-console
  console.warn('[v-permission] value must be string or string[]', binding.value)
  return false
}

/** 评估角色值。 */
function evaluateRole(binding: DirectiveBinding): boolean {
  const store = usePermissionStore()
  const value = binding.value

  if (typeof value === 'string') {
    return store.hasRole(value)
  }

  if (Array.isArray(value)) {
    const roles = value as RoleKey[]
    if (roles.length === 0) return true
    return store.hasAnyRole(...roles)
  }

  // eslint-disable-next-line no-console
  console.warn('[v-permission.role] value must be string or string[]', binding.value)
  return false
}

/** 主权限指令：默认移除元素模式。 */
export const vPermission: Directive<HTMLElement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const passed = evaluatePermissions(binding, 'any')
    if (!passed) {
      removeElement(el)
    }
  },
  // 注意：不实现 updated 钩子以避免元素被重新插入；
  // 权限变更通过刷新页面或路由守卫触发重新渲染。
}

/** v-permission.any：任一权限满足即显示。 */
export const vPermissionAny: Directive<HTMLElement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const passed = evaluatePermissions(binding, 'any')
    if (!passed) {
      removeElement(el)
    }
  },
}

/** v-permission.all：全部权限满足才显示。 */
export const vPermissionAll: Directive<HTMLElement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const passed = evaluatePermissions(binding, 'all')
    if (!passed) {
      removeElement(el)
    }
  },
}

/** v-permission.role：拥有指定角色（任一）即显示。 */
export const vPermissionRole: Directive<HTMLElement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const passed = evaluateRole(binding)
    if (!passed) {
      removeElement(el)
    }
  },
}

/** v-permission.disable：权限不满足时禁用元素（保留 DOM）。 */
export const vPermissionDisable: Directive<HTMLElement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const passed = evaluatePermissions(binding, 'any')
    if (!passed) {
      disableElement(el)
    } else {
      enableElement(el)
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    const passed = evaluatePermissions(binding, 'any')
    if (passed) {
      enableElement(el)
    } else {
      disableElement(el)
    }
  },
}

/** 权限指令注册表（在 main.ts 中通过 app.use(registry) 注册）。 */
export const permissionDirectives = {
  permission: vPermission,
  'permission-any': vPermissionAny,
  'permission-all': vPermissionAll,
  'permission-role': vPermissionRole,
  'permission-disable': vPermissionDisable,
}

/** Vue 插件：自动注册所有权限指令。 */
export const PermissionDirectivePlugin = {
  install(app: { directive: (name: string, directive: Directive) => void }) {
    for (const [name, directive] of Object.entries(permissionDirectives)) {
      app.directive(name, directive)
    }
  },
}

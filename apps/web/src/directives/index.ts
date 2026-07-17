/**
 * 全局指令 barrel 导出（T092）。
 *
 * 业务页面通过此 barrel 引入指令与插件：
 * @example
 * ```ts
 * // main.ts
 * import { PermissionDirectivePlugin } from '@/directives'
 * app.use(PermissionDirectivePlugin)
 *
 * // 业务页面模板
 * <a-button v-permission="'workflow.definition.deploy'">部署</a-button>
 * <a-button v-permission.role="'WORKSPACE_ADMIN'">管理</a-button>
 * ```
 */
export {
  vPermission,
  vPermissionAny,
  vPermissionAll,
  vPermissionRole,
  vPermissionDisable,
  permissionDirectives,
  PermissionDirectivePlugin,
} from './permission'

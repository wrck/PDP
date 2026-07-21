import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// PDP 路由骨架
// - / 重定向到默认工作空间
// - /workspace、/login、/404 使用懒加载，匹配 plan.md 中按业务特性组织 views 的约定
// - /admin/workflow/* 平台工作流管理页面（T090、FR-174）
// - /workspaces/* 工作空间治理页面（T108、FR-003 至 FR-006、FR-063 至 FR-068）
// - 未匹配路由回退到 /404
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/workspace'
  },
  {
    path: '/workspace',
    name: 'workspace',
    component: () => import('@/views/WorkspaceView.vue'),
    meta: { requiresAuth: true, title: '工作空间' }
  },
  {
    path: '/workspaces',
    name: 'workspace-selector',
    component: () => import('@/views/workspace/WorkspaceSelectorView.vue'),
    meta: { requiresAuth: true, title: '选择工作空间' }
  },
  {
    path: '/workspaces/:workspaceId',
    name: 'workspace-detail',
    component: () => import('@/views/workspace/WorkspaceDetailView.vue'),
    meta: { requiresAuth: true, title: '工作空间详情' },
    props: true
  },
  {
    path: '/workspaces/:workspaceId/organizations',
    name: 'workspace-organizations',
    component: () => import('@/views/workspace/OrganizationTreeView.vue'),
    meta: { requiresAuth: true, title: '组织树管理' },
    props: true
  },
  {
    path: '/workspaces/:workspaceId/members',
    name: 'workspace-members',
    component: () => import('@/views/workspace/WorkspaceMemberListView.vue'),
    meta: { requiresAuth: true, title: '成员管理' },
    props: true
  },
  {
    path: '/workspaces/:workspaceId/roles',
    name: 'workspace-roles',
    component: () => import('@/views/workspace/WorkspaceRoleListView.vue'),
    meta: { requiresAuth: true, title: '角色管理' },
    props: true
  },
  {
    path: '/workspaces/:workspaceId/data-scopes',
    name: 'workspace-data-scopes',
    component: () => import('@/views/workspace/DataScopeListView.vue'),
    meta: { requiresAuth: true, title: '数据范围管理' },
    props: true
  },
  {
    path: '/workspaces/:workspaceId/collaboration-grants',
    name: 'workspace-collaboration-grants',
    component: () => import('@/views/workspace/CollaborationGrantListView.vue'),
    meta: { requiresAuth: true, title: '协作授权管理' },
    props: true
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/admin/workflow/definitions',
    name: 'admin-workflow-definitions',
    component: () => import('@/views/admin/workflow/WorkflowDefinitionListView.vue'),
    meta: { requiresAuth: true, title: '平台流程定义' }
  },
  {
    path: '/admin/workflow/instances',
    name: 'admin-workflow-instances',
    component: () => import('@/views/admin/workflow/WorkflowInstanceListView.vue'),
    meta: { requiresAuth: true, title: '流程实例诊断' }
  },
  {
    path: '/admin/workflow/instances/:instanceId',
    name: 'admin-workflow-instance-detail',
    component: () => import('@/views/admin/workflow/WorkflowInstanceDetailView.vue'),
    meta: { requiresAuth: true, title: '流程实例诊断详情' },
    props: true
  },
  {
    path: '/404',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router

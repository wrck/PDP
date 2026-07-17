import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// PDP 路由骨架
// - / 重定向到默认工作空间
// - /workspace、/login、/404 使用懒加载，匹配 plan.md 中按业务特性组织 views 的约定
// - /admin/workflow/* 平台工作流管理页面（T090、FR-174）
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

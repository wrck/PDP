import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// PDP 路由骨架
// - / 重定向到默认工作空间
// - /workspace、/login、/404 使用懒加载，匹配 plan.md 中按业务特性组织 views 的约定
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

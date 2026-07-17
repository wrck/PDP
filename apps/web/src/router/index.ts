import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
    },
    {
      path: '/workspaces',
      name: 'workspace-governance',
      component: () => import('../views/workspace/WorkspaceGovernanceView.vue'),
    },
    {
      path: '/domain-packages',
      name: 'domain-package-designer',
      component: () =>
        import('../views/domain-package/DomainPackageDesignerView.vue'),
    },
    {
      path: '/admin/workflow',
      name: 'workflow-administration',
      component: () =>
        import('../views/admin/workflow/WorkflowAdministrationView.vue'),
    },
  ],
})

export default router

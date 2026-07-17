/**
 * 平台工作流管理页面 barrel 导出（T090、FR-174、ADR-0005）。
 *
 * 路由层通过此 barrel 引入页面组件，避免深路径引用。
 */
export { default as WorkflowDefinitionListView } from './WorkflowDefinitionListView.vue'
export { default as WorkflowInstanceListView } from './WorkflowInstanceListView.vue'
export { default as WorkflowInstanceDetailView } from './WorkflowInstanceDetailView.vue'
export { default as WorkflowDefinitionDeployDrawer } from './WorkflowDefinitionDeployDrawer.vue'
export { default as WorkflowTransitionModal } from './WorkflowTransitionModal.vue'
export { default as MigrationPreviewDrawer } from './MigrationPreviewDrawer.vue'
export { default as ApplyActionModal } from './ApplyActionModal.vue'
export { workflowApi } from './api'
export * from './types'
export { ApiError } from './http'

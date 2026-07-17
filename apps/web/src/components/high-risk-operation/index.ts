/**
 * 高风险操作组件 barrel 导出（FR-168、SC-039）。
 *
 * 业务页面通过此 barrel 导入组件与类型，避免深路径引用。
 */
export { default as ImpactPreviewPanel } from './ImpactPreviewPanel.vue'
export * from './types'

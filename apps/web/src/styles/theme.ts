/**
 * PDP Web Ant Design Vue 主题配置（T094）。
 *
 * 将 tokens.scss 中的设计令牌映射到 Ant Design Vue 4.x 的 ConfigProvider token，
 * 通过 a-config-provider 的 :theme="{ token }" 应用。
 * 同时提供响应式断点判定工具，用于运行时布局切换。
 */

/** Ant Design Vue 主题令牌（与 tokens.scss 对齐）。 */
export const themeTokens = {
  colorPrimary: '#1677ff',
  colorSuccess: '#52c41a',
  colorWarning: '#faad14',
  colorError: '#ff4d4f',
  colorInfo: '#1677ff',
  colorTextBase: '#000',
  colorBgLayout: '#f5f5f5',
  borderRadius: 6,
  borderRadiusLG: 8,
  borderRadiusSM: 4,
  fontSize: 14,
  fontFamily:
    "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
} as const

/** 完整主题配置（结构对齐 Ant Design Vue 4.x ConfigProvider theme 属性）。 */
export const themeConfig = {
  token: themeTokens,
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 56,
      siderBg: '#001529',
      bodyBg: '#f5f5f5',
    },
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17',
    },
    Card: {
      borderRadiusLG: 8,
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: 'rgba(0, 0, 0, 0.88)',
      rowHoverBg: '#f5f5f5',
    },
  },
} as const

/** 响应式断点（与 tokens.scss 对齐，运行时判定）。 */
export const breakpoints = {
  xs: 480,
  sm: 576,
  md: 768,
  lg: 992,
  xl: 1200,
  xxl: 1600,
} as const

/** 当前视口宽度。 */
export function getViewportWidth(): number {
  if (typeof window === 'undefined') return 1200
  return window.innerWidth
}

/** 判定是否为移动端视口（小于 md 断点）。 */
export function isMobile(): boolean {
  return getViewportWidth() < breakpoints.md
}

/** 判定是否为桌面端视口（大于等于 lg 断点）。 */
export function isDesktop(): boolean {
  return getViewportWidth() >= breakpoints.lg
}

/** 注册视口变化监听器，返回取消监听函数。 */
export function onViewportChange(callback: (width: number) => void): () => void {
  if (typeof window === 'undefined') return () => {}
  const handler = () => callback(getViewportWidth())
  window.addEventListener('resize', handler)
  return () => window.removeEventListener('resize', handler)
}

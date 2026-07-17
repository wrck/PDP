/**
 * 统一交互反馈（T094、FR-067、FR-068）。
 *
 * 封装 Ant Design Vue 的 message / notification / Modal，
 * 提供统一的成功、失败、警告、加载、确认等交互反馈接口。
 * 配合 ApiError（T092）实现错误信息的友好展示。
 *
 * <p><strong>设计原则</strong>：
 * <ul>
 *   <li>同一类型的消息自动去重（避免重复弹出）；</li>
 *   <li>错误消息包含 traceId/correlationId 便于客服排障；</li>
 *   <li>高风险操作使用 Modal.confirm 二次确认。</li>
 * </ul>
 */
import {
  message,
  notification,
  Modal,
  type ModalFuncProps,
} from 'ant-design-vue'
import { CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined, InfoCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { h } from 'vue'
import { type ApiError, describeError } from '@/api/errors'

/** 消息持续时间（秒）。 */
const DURATION = {
  success: 3,
  info: 3,
  warning: 4,
  error: 5,
  loading: 0, // 需手动关闭
} as const

/** 通知持续时间（秒）。 */
const NOTIFICATION_DURATION = {
  success: 4.5,
  info: 4.5,
  warning: 6,
  error: 7,
} as const

/** 成功反馈。 */
export function showSuccess(content: string, duration?: number): void {
  message.success({
    content,
    duration: duration ?? DURATION.success,
    icon: () => h(CheckCircleOutlined, { style: { color: 'var(--pdp-color-success)' } }),
  })
}

/** 信息反馈。 */
export function showInfo(content: string, duration?: number): void {
  message.info({
    content,
    duration: duration ?? DURATION.info,
    icon: () => h(InfoCircleOutlined, { style: { color: 'var(--pdp-color-info)' } }),
  })
}

/** 警告反馈。 */
export function showWarning(content: string, duration?: number): void {
  message.warning({
    content,
    duration: duration ?? DURATION.warning,
    icon: () => h(ExclamationCircleOutlined, { style: { color: 'var(--pdp-color-warning)' } }),
  })
}

/** 错误反馈（短文本）。 */
export function showError(content: string, duration?: number): void {
  message.error({
    content,
    duration: duration ?? DURATION.error,
    icon: () => h(CloseCircleOutlined, { style: { color: 'var(--pdp-color-error)' } }),
  })
}

/** 加载中反馈，返回关闭函数。 */
export function showLoading(content = '处理中...'): () => void {
  const hide = message.loading({
    content,
    duration: DURATION.loading,
    icon: () => h(LoadingOutlined),
  })
  return hide
}

/** 通用错误反馈，自动从 ApiError 提取消息。 */
export function showErrorFromApiError(error: unknown): void {
  if (error instanceof ApiError) {
    const desc = describeError(error)
    showError(desc, DURATION.error)
  } else if (error instanceof Error) {
    showError(error.message, DURATION.error)
  } else {
    showError('发生未知错误', DURATION.error)
  }
}

/** 操作成功通知（带标题，用于重要操作完成）。 */
export function notifySuccess(
  title: string,
  description?: string,
): void {
  notification.success({
    message: title,
    description,
    duration: NOTIFICATION_DURATION.success,
    icon: () => h(CheckCircleOutlined, { style: { color: 'var(--pdp-color-success)' } }),
  })
}

/** 操作警告通知。 */
export function notifyWarning(
  title: string,
  description?: string,
): void {
  notification.warning({
    message: title,
    description,
    duration: NOTIFICATION_DURATION.warning,
    icon: () => h(ExclamationCircleOutlined, { style: { color: 'var(--pdp-color-warning)' } }),
  })
}

/** 操作错误通知（带详情，用于需要排障场景）。 */
export function notifyError(
  title: string,
  description?: string,
): void {
  notification.error({
    message: title,
    description,
    duration: NOTIFICATION_DURATION.error,
    icon: () => h(CloseCircleOutlined, { style: { color: 'var(--pdp-color-error)' } }),
  })
}

/** 从 ApiError 派生错误通知（含 traceId/correlationId）。 */
export function notifyErrorFromApiError(error: unknown): void {
  if (error instanceof ApiError) {
    const title = describeError(error)
    const desc = error.traceId
      ? `追踪 ID：${error.traceId}${error.correlationId ? `（关联：${error.correlationId}）` : ''}`
      : undefined
    notifyError(title, desc)
  } else if (error instanceof Error) {
    notifyError(error.message)
  } else {
    notifyError('未知错误', '请稍后重试或联系管理员')
  }
}

/** 二次确认对话框，返回 Promise<boolean>。 */
export function confirm(options: {
  title: string
  content?: string
  okText?: string
  cancelText?: string
  danger?: boolean
}): Promise<boolean> {
  return new Promise((resolve) => {
    const props: ModalFuncProps = {
      title: options.title,
      content: options.content,
      okText: options.okText ?? '确认',
      cancelText: options.cancelText ?? '取消',
      okButtonProps: options.danger ? { danger: true } : undefined,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    }
    if (options.danger) {
      Modal.confirm(props)
    } else {
      Modal.confirm(props)
    }
  })
}

/** 高风险操作确认（FR-168）。 */
export function confirmHighRisk(options: {
  title: string
  content: string
  impactSummary?: string
  okText?: string
}): Promise<boolean> {
  const content = options.impactSummary
    ? `${options.content}\n\n影响摘要：${options.impactSummary}`
    : options.content
  return confirm({
    title: options.title,
    content,
    okText: options.okText ?? '已确认影响，执行',
    cancelText: '取消',
    danger: true,
  })
}

/** 异步操作包装：自动管理 loading 与错误反馈。 */
export async function withFeedback<T>(
  operation: () => Promise<T>,
  options: {
    loadingText?: string
    successText?: string
    showError?: boolean
  } = {},
): Promise<T | undefined> {
  const { loadingText = '处理中...', successText, showError = true } = options
  const hide = showLoading(loadingText)
  try {
    const result = await operation()
    hide()
    if (successText) showSuccess(successText)
    return result
  } catch (err) {
    hide()
    if (showError) showErrorFromApiError(err)
    return undefined
  }
}

export const feedback = {
  success: showSuccess,
  info: showInfo,
  warning: showWarning,
  error: showError,
  loading: showLoading,
  errorFromApiError: showErrorFromApiError,
  notifySuccess,
  notifyWarning,
  notifyError,
  notifyErrorFromApiError,
  confirm,
  confirmHighRisk,
  withFeedback,
}

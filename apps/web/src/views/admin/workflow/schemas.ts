import type { ObjectSchema } from '../../../components/schema'

export const incidentSchema: ObjectSchema = {
  type: 'object',
  properties: {
    code: { type: 'string', title: '异常代码' },
    message: { type: 'string', title: '诊断信息' },
    status: { type: 'string', title: '状态' },
    attempts: { type: 'integer', title: '尝试次数' },
    occurredAt: { type: 'string', title: '发生时间' },
  },
}

import type { ObjectSchema } from '../../components/schema'

export const organizationTableSchema: ObjectSchema = {
  type: 'object',
  properties: {
    code: { type: 'string', title: '组织编码' },
    name: { type: 'string', title: '组织名称' },
    type: { type: 'string', title: '类型' },
    path: { type: 'string', title: '组织路径' },
    status: { type: 'string', title: '状态' },
  },
}

export const organizationFormSchema: ObjectSchema = {
  type: 'object',
  title: '新增组织单元',
  required: ['code', 'name', 'type'],
  properties: {
    code: { type: 'string', title: '组织编码', description: '例如：EAST_REGION' },
    name: { type: 'string', title: '组织名称' },
    type: {
      type: 'string',
      title: '组织类型',
      enum: ['COMPANY', 'DEPARTMENT', 'TEAM', 'REGION', 'EXTERNAL'],
    },
    parentId: { type: 'string', title: '上级组织 ID' },
    regionCode: { type: 'string', title: '区域编码' },
  },
}

export const memberTableSchema: ObjectSchema = {
  type: 'object',
  properties: {
    userId: { type: 'string', title: '用户 ID' },
    membershipType: { type: 'string', title: '成员类型' },
    roleIds: { type: 'string', title: '角色' },
    validUntil: { type: 'string', title: '有效期' },
    status: { type: 'string', title: '状态' },
  },
}

export const memberFormSchema: ObjectSchema = {
  type: 'object',
  title: '添加成员',
  required: ['userId', 'roleIds', 'membershipType'],
  properties: {
    userId: { type: 'string', title: '用户 ID' },
    organizationUnitId: { type: 'string', title: '组织单元 ID' },
    roleIds: { type: 'string', title: '角色 ID', description: '多个角色以逗号分隔' },
    dataScopeIds: {
      type: 'string',
      title: '数据范围 ID',
      description: '多个数据范围以逗号分隔',
    },
    membershipType: {
      type: 'string',
      title: '成员类型',
      enum: ['INTERNAL', 'EXTERNAL'],
    },
    validUntil: { type: 'string', title: '有效期', description: 'ISO 8601 日期时间' },
  },
}

export const roleTableSchema: ObjectSchema = {
  type: 'object',
  properties: {
    stableKey: { type: 'string', title: '稳定键' },
    name: { type: 'string', title: '角色名称' },
    allowedActions: { type: 'string', title: '允许动作' },
    status: { type: 'string', title: '状态' },
  },
}

export const roleFormSchema: ObjectSchema = {
  type: 'object',
  title: '新增角色',
  required: ['stableKey', 'name', 'allowedActions'],
  properties: {
    stableKey: { type: 'string', title: '稳定键', description: '例如：workspace.manager' },
    name: { type: 'string', title: '角色名称' },
    allowedActions: {
      type: 'string',
      title: '允许动作',
      description: '多个动作以逗号分隔',
    },
  },
}

export const grantTableSchema: ObjectSchema = {
  type: 'object',
  properties: {
    collaboratorWorkspaceId: { type: 'string', title: '协作工作空间' },
    targetLabel: { type: 'string', title: '授权对象' },
    allowedActions: { type: 'string', title: '允许动作' },
    validUntil: { type: 'string', title: '有效期' },
    status: { type: 'string', title: '状态' },
  },
}

export const grantFormSchema: ObjectSchema = {
  type: 'object',
  title: '新增跨空间授权',
  required: [
    'collaboratorWorkspaceId',
    'objectType',
    'objectId',
    'roleId',
    'allowedActions',
    'validUntil',
  ],
  properties: {
    collaboratorWorkspaceId: { type: 'string', title: '协作工作空间 ID' },
    objectType: { type: 'string', title: '对象类型', description: '例如：project' },
    objectId: { type: 'string', title: '对象 ID' },
    roleId: { type: 'string', title: '角色 ID' },
    allowedActions: {
      type: 'string',
      title: '允许动作',
      description: '多个动作以逗号分隔',
    },
    validUntil: { type: 'string', title: '有效期', description: '必须为未来时间' },
    reason: { type: 'string', title: '授权原因' },
  },
}

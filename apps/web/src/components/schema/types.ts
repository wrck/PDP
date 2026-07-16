export interface SchemaProperty {
  type?: 'string' | 'number' | 'integer' | 'boolean'
  title?: string
  description?: string
  format?: string
  enum?: unknown[]
  readOnly?: boolean
}

export interface ObjectSchema {
  type: 'object'
  title?: string
  required?: string[]
  properties: Record<string, SchemaProperty>
}

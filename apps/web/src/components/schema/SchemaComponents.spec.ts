import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SchemaDetail from './SchemaDetail.vue'
import SchemaForm from './SchemaForm.vue'
import SchemaTable from './SchemaTable.vue'

const schema = {
  type: 'object' as const,
  required: ['name'],
  properties: {
    name: { type: 'string' as const, title: '名称' },
    status: { type: 'string' as const, title: '状态', enum: ['ACTIVE', 'DONE'] },
  },
}

describe('Schema 基础组件', () => {
  it('表单按属性渲染并更新模型', async () => {
    const wrapper = mount(SchemaForm, {
      props: { schema, modelValue: { name: 'PDP', status: 'ACTIVE' } },
    })

    await wrapper.get('input').setValue('PDP 2')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toMatchObject({
      name: 'PDP 2',
      status: 'ACTIVE',
    })
  })

  it('详情与表格使用相同字段标题', () => {
    expect(
      mount(SchemaDetail, {
        props: { schema, value: { name: 'PDP', status: 'ACTIVE' } },
      }).text(),
    ).toContain('名称')
    expect(
      mount(SchemaTable, {
        props: { schema, rows: [{ id: '1', name: 'PDP', status: 'ACTIVE' }] },
      }).text(),
    ).toContain('状态')
  })
})

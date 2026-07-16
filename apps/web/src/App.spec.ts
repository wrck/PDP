import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'

describe('App', () => {
  it('可以挂载平台根组件', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: { template: '<div data-test="router-view" />' },
        },
      },
    })

    expect(wrapper.find('[data-test="router-view"]').exists()).toBe(true)
  })
})

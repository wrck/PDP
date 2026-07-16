import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import WorkspaceGovernanceView from './WorkspaceGovernanceView.vue'
import type {
  WorkspaceGovernanceApi,
  WorkspaceGovernanceSnapshot,
} from './types'

const emptySnapshot: WorkspaceGovernanceSnapshot = {
  organizations: [],
  members: [],
  roles: [],
  grants: [],
}

function createApi(): WorkspaceGovernanceApi {
  return {
    listWorkspaces: vi.fn().mockResolvedValue([
      {
        id: 'workspace-a',
        code: 'EAST',
        name: '华东交付中心',
        ownerUserId: 'user-1',
        status: 'ACTIVE',
        revision: 1,
      },
      {
        id: 'workspace-b',
        code: 'NORTH',
        name: '北区交付中心',
        ownerUserId: 'user-2',
        status: 'ACTIVE',
        revision: 1,
      },
    ]),
    loadGovernance: vi.fn().mockResolvedValue(emptySnapshot),
    createOrganization: vi.fn(),
    addMember: vi.fn(),
    createRole: vi.fn().mockResolvedValue({
      id: 'role-1',
      workspaceId: 'workspace-a',
      stableKey: 'workspace.manager',
      name: '空间负责人',
      allowedActions: ['workspace.manage'],
      status: 'ACTIVE',
      revision: 0,
    }),
    createGrant: vi.fn(),
    revokeGrant: vi.fn(),
  }
}

describe('WorkspaceGovernanceView', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('加载可访问工作空间并在切换时刷新治理数据', async () => {
    const api = createApi()
    const wrapper = mount(WorkspaceGovernanceView, {
      props: { governanceApi: api },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('华东交付中心（EAST）')
    expect(api.loadGovernance).toHaveBeenCalledWith('workspace-a')

    await wrapper.get('[data-test="workspace-select"]').setValue('workspace-b')
    await flushPromises()

    expect(api.loadGovernance).toHaveBeenLastCalledWith('workspace-b')
  })

  it('通过角色页签提交稳定角色与动作集合', async () => {
    const api = createApi()
    const wrapper = mount(WorkspaceGovernanceView, {
      props: { governanceApi: api },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    const roleTab = wrapper
      .findAll('.workspace-navigation__tab')
      .find((button) => button.get('strong').text() === '角色')
    expect(roleTab).toBeDefined()
    await roleTab!.trigger('click')

    const inputs = wrapper.findAll('.workspace-panel__form input')
    await inputs[0]!.setValue('workspace.manager')
    await inputs[1]!.setValue('空间负责人')
    await inputs[2]!.setValue('workspace.read, workspace.manage')
    await wrapper.get('.workspace-panel__form').trigger('submit')
    await flushPromises()

    expect(api.createRole).toHaveBeenCalledWith('workspace-a', {
      stableKey: 'workspace.manager',
      name: '空间负责人',
      allowedActions: ['workspace.read', 'workspace.manage'],
    })
    expect(wrapper.text()).toContain('治理配置已保存并立即生效')
  })
})

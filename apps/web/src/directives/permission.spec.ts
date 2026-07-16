import { describe, expect, it } from 'vitest'

import { createPermissionDirective } from './permission'

describe('权限指令', () => {
  it('缺少任一要求权限时隐藏元素并阻止交互', () => {
    const directive = createPermissionDirective(() => new Set(['project.read']))
    const element = document.createElement('button')

    directive.mounted?.(
      element,
      { value: ['project.read', 'project.update'] } as never,
      {} as never,
      {} as never,
    )

    expect(element.hidden).toBe(true)
    expect(element.getAttribute('aria-disabled')).toBe('true')
  })

  it('拥有全部权限时展示元素', () => {
    const directive = createPermissionDirective(
      () => new Set(['project.read', 'project.update']),
    )
    const element = document.createElement('button')

    directive.mounted?.(
      element,
      { value: ['project.read', 'project.update'] } as never,
      {} as never,
      {} as never,
    )

    expect(element.hidden).toBe(false)
    expect(element.hasAttribute('aria-disabled')).toBe(false)
  })
})

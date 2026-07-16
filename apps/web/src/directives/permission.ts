import type { ObjectDirective } from 'vue'

export type PermissionRequirement = string | readonly string[]
export type PermissionSource = () => ReadonlySet<string>

function normalize(requirement: PermissionRequirement): readonly string[] {
  return typeof requirement === 'string' ? [requirement] : requirement
}

export function createPermissionDirective(
  permissions: PermissionSource,
): ObjectDirective<HTMLElement, PermissionRequirement> {
  const apply = (element: HTMLElement, requirement: PermissionRequirement) => {
    const granted = permissions()
    const allowed = normalize(requirement).every((permission) =>
      granted.has(permission),
    )

    element.hidden = !allowed
    if (allowed) {
      element.removeAttribute('aria-disabled')
    } else {
      element.setAttribute('aria-disabled', 'true')
    }
  }

  return {
    mounted(element, binding) {
      apply(element, binding.value)
    },
    updated(element, binding) {
      apply(element, binding.value)
    },
  }
}

const grantedPermissions = new Set<string>()

export function replaceGrantedPermissions(permissions: Iterable<string>): void {
  grantedPermissions.clear()
  for (const permission of permissions) grantedPermissions.add(permission)
}

export const permissionDirective = createPermissionDirective(
  () => grantedPermissions,
)

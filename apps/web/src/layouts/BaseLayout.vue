<script setup lang="ts">
/**
 * 主应用布局（T094）。
 *
 * 提供平台主框架：顶部导航栏 + 侧边菜单 + 内容区域。
 * - 顶部栏：品牌 logo、工作空间切换、用户菜单；
 * - 侧边栏：根据当前路由生成菜单，支持折叠；
 * - 内容区：通过 router-view 渲染子路由，提供 PageContainer 包装。
 *
 * 响应式策略：
 * - 桌面端（≥ md）：侧边栏固定，可折叠；
 * - 移动端（< md）：侧边栏抽屉模式，点击遮罩关闭。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Layout as ALayout,
  LayoutHeader as ALayoutHeader,
  LayoutSider as ALayoutSider,
  LayoutContent as ALayoutContent,
  Menu as AMenu,
  MenuItem as AMenuItem,
  SubMenu as ASubMenu,
  Button as AButton,
  Avatar as AAvatar,
  Dropdown as ADropdown,
  Space as ASpace,
  Drawer as ADrawer,
  Breadcrumb as ABreadcrumb,
  BreadcrumbItem as ABreadcrumbItem,
  Typography as ATypography,
} from 'ant-design-vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  AppstoreOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  DashboardOutlined,
  ProjectOutlined,
  PartitionOutlined,
  AuditOutlined,
  ApartmentOutlined,
} from '@ant-design/icons-vue'
import { isMobile, onViewportChange } from '@/styles/theme'

interface MenuItemDef {
  key: string
  label: string
  icon?: unknown
  path?: string
  children?: MenuItemDef[]
}

const props = withDefaults(
  defineProps<{
    /** 顶部品牌名称。 */
    brand?: string
    /** 当前工作空间名称。 */
    workspaceName?: string
    /** 当前用户显示名。 */
    userName?: string
    /** 当前用户头像 URL。 */
    avatarUrl?: string
  }>(),
  {
    brand: 'PDP',
    workspaceName: '默认工作空间',
    userName: '未登录',
  },
)

const emit = defineEmits<{
  (e: 'workspace-switch'): void
  (e: 'user-action', action: 'profile' | 'settings' | 'logout'): void
}>()

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)
const mobileOpen = ref(false)
const mobile = ref(isMobile())

let unsubscribe: (() => void) | null = null

onMounted(() => {
  unsubscribe = onViewportChange(() => {
    const wasMobile = mobile.value
    mobile.value = isMobile()
    // 桌面端打开抽屉时切回固定侧边栏
    if (wasMobile && !mobile.value) {
      mobileOpen.value = false
    }
  })
})

onBeforeUnmount(() => {
  if (unsubscribe) unsubscribe()
})

/** 主菜单定义。 */
const menuItems = computed<MenuItemDef[]>(() => [
  {
    key: 'dashboard',
    label: '工作台',
    icon: DashboardOutlined,
    path: '/workspace',
  },
  {
    key: 'projects',
    label: '项目',
    icon: ProjectOutlined,
    path: '/projects',
  },
  {
    key: 'tasks',
    label: '任务',
    icon: AppstoreOutlined,
    path: '/tasks',
  },
  {
    key: 'plans',
    label: '计划基线',
    icon: PartitionOutlined,
    path: '/plans',
  },
  {
    key: 'deliverables',
    label: '交付件',
    icon: AuditOutlined,
    path: '/deliverables',
  },
  {
    key: 'approvals',
    label: '审批',
    icon: ApartmentOutlined,
    path: '/approvals',
  },
  {
    key: 'admin',
    label: '平台管理',
    icon: SettingOutlined,
    children: [
      {
        key: 'admin-workflow-definitions',
        label: '流程定义',
        path: '/admin/workflow/definitions',
      },
      {
        key: 'admin-workflow-instances',
        label: '流程实例诊断',
        path: '/admin/workflow/instances',
      },
    ],
  },
])

/** 当前选中的菜单项（基于路由）。 */
const selectedKeys = computed<string[]>(() => {
  const name = route.name?.toString() ?? ''
  return name ? [name] : []
})

/** 当前展开的子菜单。 */
const openKeys = ref<string[]>(['admin'])

watch(
  () => route.name,
  (name) => {
    if (name?.toString().startsWith('admin')) {
      openKeys.value = ['admin']
    }
  },
  { immediate: true },
)

/** 点击菜单项。 */
function onMenuClick(item: { key: string }) {
  const find = (items: MenuItemDef[]): MenuItemDef | undefined => {
    for (const i of items) {
      if (i.key === item.key) return i
      if (i.children) {
        const found = find(i.children)
        if (found) return found
      }
    }
    return undefined
  }
  const target = find(menuItems.value)
  if (target?.path) {
    router.push(target.path)
    if (mobile.value) mobileOpen.value = false
  }
}

/** 切换折叠。 */
function toggleCollapsed() {
  if (mobile.value) {
    mobileOpen.value = !mobileOpen.value
  } else {
    collapsed.value = !collapsed.value
  }
}

/** 用户菜单。 */
const userMenuItems = [
  { key: 'profile', label: '个人资料', icon: UserOutlined },
  { key: 'settings', label: '设置', icon: SettingOutlined },
  { key: 'logout', label: '退出登录', icon: LogoutOutlined, danger: true },
]

function onUserMenuClick({ key }: { key: string }) {
  if (key === 'profile') emit('user-action', 'profile')
  else if (key === 'settings') emit('user-action', 'settings')
  else if (key === 'logout') emit('user-action', 'logout')
}

/** 面包屑。 */
const breadcrumbs = computed(() => {
  const items: Array<{ label: string; path?: string }> = [
    { label: props.workspaceName, path: '/workspace' },
  ]
  const matched = route.matched.filter((r) => r.meta?.title)
  for (const r of matched) {
    items.push({ label: r.meta!.title as string, path: r.path })
  }
  return items
})

function onBreadcrumbClick(item: { path?: string }) {
  if (item.path) router.push(item.path)
}
</script>

<template>
  <ALayout class="base-layout">
    <!-- 桌面端侧边栏 -->
    <ALayoutSider
      v-if="!mobile"
      v-model:collapsed="collapsed"
      collapsible
      :trigger="null"
      :width="220"
      :collapsed-width="64"
      class="base-layout__sider"
    >
      <div class="base-layout__brand">
        <span v-if="!collapsed" class="base-layout__brand-text">{{ brand }}</span>
        <span v-else class="base-layout__brand-icon">{{ brand.charAt(0) }}</span>
      </div>
      <AMenu
        v-model:selected-keys="selectedKeys"
        v-model:open-keys="openKeys"
        mode="inline"
        theme="dark"
        @click="onMenuClick"
      >
        <AMenuItem key="dashboard">
          <DashboardOutlined />
          <span>工作台</span>
        </AMenuItem>
        <AMenuItem key="projects">
          <ProjectOutlined />
          <span>项目</span>
        </AMenuItem>
        <AMenuItem key="tasks">
          <AppstoreOutlined />
          <span>任务</span>
        </AMenuItem>
        <AMenuItem key="plans">
          <PartitionOutlined />
          <span>计划基线</span>
        </AMenuItem>
        <AMenuItem key="deliverables">
          <AuditOutlined />
          <span>交付件</span>
        </AMenuItem>
        <AMenuItem key="approvals">
          <ApartmentOutlined />
          <span>审批</span>
        </AMenuItem>
        <ASubMenu key="admin">
          <template #title>
            <SettingOutlined />
            <span>平台管理</span>
          </template>
          <AMenuItem key="admin-workflow-definitions">流程定义</AMenuItem>
          <AMenuItem key="admin-workflow-instances">流程实例诊断</AMenuItem>
        </ASubMenu>
      </AMenu>
    </ALayoutSider>

    <!-- 移动端抽屉 -->
    <ADrawer
      v-if="mobile"
      v-model:open="mobileOpen"
      placement="left"
      :width="220"
      :body-style="{ padding: 0, background: '#001529' }"
      :header-style="{ display: 'none' }"
    >
      <div class="base-layout__brand base-layout__brand--mobile">
        <span class="base-layout__brand-text">{{ brand }}</span>
      </div>
      <AMenu
        v-model:selected-keys="selectedKeys"
        v-model:open-keys="openKeys"
        mode="inline"
        theme="dark"
        @click="onMenuClick"
      >
        <AMenuItem key="dashboard">
          <DashboardOutlined />
          <span>工作台</span>
        </AMenuItem>
        <AMenuItem key="projects">
          <ProjectOutlined />
          <span>项目</span>
        </AMenuItem>
        <AMenuItem key="tasks">
          <AppstoreOutlined />
          <span>任务</span>
        </AMenuItem>
        <AMenuItem key="plans">
          <PartitionOutlined />
          <span>计划基线</span>
        </AMenuItem>
        <AMenuItem key="deliverables">
          <AuditOutlined />
          <span>交付件</span>
        </AMenuItem>
        <AMenuItem key="approvals">
          <ApartmentOutlined />
          <span>审批</span>
        </AMenuItem>
        <ASubMenu key="admin">
          <template #title>
            <SettingOutlined />
            <span>平台管理</span>
          </template>
          <AMenuItem key="admin-workflow-definitions">流程定义</AMenuItem>
          <AMenuItem key="admin-workflow-instances">流程实例诊断</AMenuItem>
        </ASubMenu>
      </AMenu>
    </ADrawer>

    <ALayout>
      <!-- 顶部栏 -->
      <ALayoutHeader class="base-layout__header">
        <AButton
          type="text"
          class="base-layout__trigger"
          @click="toggleCollapsed"
        >
          <component :is="collapsed || mobileOpen ? MenuUnfoldOutlined : MenuFoldOutlined" />
        </AButton>

        <ABreadcrumb class="base-layout__breadcrumb">
          <ABreadcrumbItem
            v-for="(item, idx) in breadcrumbs"
            :key="idx"
            @click="onBreadcrumbClick(item)"
          >
            {{ item.label }}
          </ABreadcrumbItem>
        </ABreadcrumb>

        <ASpace class="base-layout__actions" size="middle">
          <ATypography class="base-layout__workspace" @click="emit('workspace-switch')">
            {{ workspaceName }}
          </ATypography>
          <ADropdown :menu="{ items: userMenuItems, onClick: onUserMenuClick }" placement="bottomRight">
            <ASpace class="base-layout__user" align="center">
              <AAvatar :src="avatarUrl" size="small">
                <template #icon><UserOutlined /></template>
              </AAvatar>
              <span class="base-layout__user-name">{{ userName }}</span>
            </ASpace>
          </ADropdown>
        </ASpace>
      </ALayoutHeader>

      <!-- 内容区 -->
      <ALayoutContent class="base-layout__content">
        <slot />
      </ALayoutContent>
    </ALayout>
  </ALayout>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as tokens;

.base-layout {
  min-height: 100vh;

  &__sider {
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
    z-index: tokens.$pdp-z-fixed;
  }

  &__brand {
    height: tokens.$pdp-layout-header-height;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: tokens.$pdp-font-size-lg;
    font-weight: 600;
    background: rgba(255, 255, 255, 0.04);
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    &--mobile {
      justify-content: flex-start;
      padding-left: tokens.$pdp-spacing-lg;
    }
  }

  &__brand-text {
    letter-spacing: 1px;
  }

  &__brand-icon {
    font-size: tokens.$pdp-font-size-xl;
  }

  &__header {
    display: flex;
    align-items: center;
    height: tokens.$pdp-layout-header-height;
    padding: 0 tokens.$pdp-spacing-lg;
    background: tokens.$pdp-color-bg-container;
    border-bottom: 1px solid tokens.$pdp-color-border-secondary;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    z-index: tokens.$pdp-z-sticky;
  }

  &__trigger {
    font-size: tokens.$pdp-font-size-lg;
    flex-shrink: 0;
  }

  &__breadcrumb {
    margin-left: tokens.$pdp-spacing-md;
    flex: 1;
    min-width: 0;
  }

  &__actions {
    flex-shrink: 0;
  }

  &__workspace {
    cursor: pointer;
    color: tokens.$pdp-color-text-secondary;

    &:hover {
      color: tokens.$pdp-color-primary;
    }
  }

  &__user {
    cursor: pointer;

    &-name {
      color: tokens.$pdp-color-text-secondary;
      font-size: tokens.$pdp-font-size-sm;
    }
  }

  &__content {
    padding: tokens.$pdp-layout-content-padding;
    overflow-x: hidden;
  }
}
</style>

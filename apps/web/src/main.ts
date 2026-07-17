import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

import App from './App.vue'
import router from './router'
import { PermissionDirectivePlugin } from './directives'
import './styles/main.scss'

const app = createApp(App)

// Pinia 仅保存跨页面客户端状态；服务器事实由 TanStack Vue Query 管理
app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin)
// Ant Design Vue 全量注册；后续可按需调整为 unplugin-vue-components 按需引入
app.use(Antd)
// 全局权限指令 v-permission / v-permission.any / v-permission.all /
// v-permission.role / v-permission.disable（T092、FR-063、FR-067）
app.use(PermissionDirectivePlugin)

app.mount('#app')

import 'ant-design-vue/dist/reset.css'
import './styles/main.css'

import { VueQueryPlugin } from '@tanstack/vue-query'
import Antd from 'ant-design-vue'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { permissionDirective } from './directives/permission'
import router from './router'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(VueQueryPlugin)
  .use(Antd)
  .directive('permission', permissionDirective)
  .mount('#app')

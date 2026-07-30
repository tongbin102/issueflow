import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { useThemeStore } from '@/store/theme'
import { useUserStore } from '@/store/user'
import permissionDirective, { vPermDirective } from '@/utils/permission'

import './styles/variables.css'
import './styles/theme.css'
import './styles/index.css'
import './styles/admin-style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册角色权限指令 v-permission="['ADMIN']"
app.directive('permission', permissionDirective)
// 注册细粒度权限指令 v-perm="'issue:create'"
app.directive('perm', vPermDirective)

// 初始化主题：从 localStorage / 默认值注入 CSS 变量（--theme-color 等）
useThemeStore().init()

// 若已存在 token 但角色未加载（持久化兜底），异步补全用户信息
const userStore = useUserStore()
if (userStore.isLoggedIn && (!userStore.roles || userStore.roles.length === 0)) {
  userStore.getInfo().catch(() => {})
}

app.mount('#app')

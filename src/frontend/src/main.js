import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import i18n from '@/locales'
import { useThemeStore } from '@/store/theme'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useLocaleStore } from '@/store/locale'
import permissionDirective, { vPermDirective } from '@/utils/permission'

import './styles/variables.css'
import './styles/theme.css'
/* Phase6：前台 4 套主题变量包（body[data-if-theme]），必须排在 theme.css 之后以保证覆盖优先级 */
import './styles/themes.css'
import './styles/index.css'
import './styles/admin-style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)
// Phase6：vue-i18n（legacy:false Composition 模式 + globalInjection）
app.use(i18n)

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册角色权限指令 v-permission="['ADMIN']"
app.directive('permission', permissionDirective)
// 注册细粒度权限指令 v-perm="'issue:create'"
app.directive('perm', vPermDirective)

// 初始化主题：从 localStorage / 默认值注入 CSS 变量（--theme-color 等，后台配色沿用旧机制）
useThemeStore().init()

// 若已存在 token 但角色未加载（持久化兜底），异步补全用户信息
const userStore = useUserStore()
if (userStore.isLoggedIn && (!userStore.roles || userStore.roles.length === 0)) {
  userStore.getInfo().catch(() => {})
}

/**
 * Phase6 启动流程：先拉取网站设置（公开接口 /api/site/config），
 * 再用其中的默认语言 / 默认主题初始化（仅当用户从未手动选择过时生效），最后挂载。
 * 拉取失败时静默降级为前端硬编码默认值，绝不阻塞挂载（ARCH T0 要点 6）。
 */
async function bootstrap() {
  const appStore = useAppStore()
  try {
    await appStore.fetchSiteConfig()
  } catch (e) {
    // 静默降级：离线 / 后端未启动时仍可正常进入前端
  }
  useLocaleStore().initFromSiteConfig(appStore.siteDefaultLocale)
  useThemeStore().initFrontThemeFromSite(appStore.siteDefaultTheme)
  app.mount('#app')
}

bootstrap()

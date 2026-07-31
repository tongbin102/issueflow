<template>
  <div class="if-layout if-layout--user">
    <!-- 侧边栏：桌面常驻 / 移动端抽屉 -->
    <aside
      class="if-sidebar"
      :class="{
        'is-collapsed': appStore.sidebarCollapsed && !appStore.isMobile,
        'is-mobile-open': appStore.isMobile && drawerOpen
      }"
    >
      <div class="if-logo">
        <span v-if="!collapsed">{{ appStore.siteName }}</span>
        <span v-else>{{ appStore.siteShortName }}</span>
      </div>
      <SideMenu :type="1" />
      <!-- 侧栏底部「切换区域」次级入口（flex 吸底，折叠态降级为纯图标） -->
      <LayoutSwitchEntry variant="sidebar" />
    </aside>

    <div
      v-if="appStore.isMobile && drawerOpen"
      class="if-mobile-mask"
      @click="drawerOpen = false"
    />

    <!-- 主区 -->
    <div class="if-main">
      <header class="if-topbar">
        <div class="topbar-left">
          <el-icon class="hamburger" @click="toggleMenu"><Menu /></el-icon>
          <span class="topbar-title">{{ pageTitle }}</span>
        </div>
        <div class="topbar-right">
          <!-- Phase6：前台主题切换（4 主题）+ 语言切换 -->
          <ThemeSwitch />
          <LocaleSwitch />
          <el-dropdown @command="onCommand">
            <span class="user-dropdown">
              <el-avatar :size="28">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="clearCache">
                <el-icon><Refresh /></el-icon><span class="dd-text">{{ t('layout.topbar.clearCache') }}</span>
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon><span class="dd-text">{{ t('layout.topbar.logout') }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="if-content">
        <div class="if-content__inner">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Menu, ArrowDown, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useThemeStore } from '@/store/theme'
import LayoutSwitchEntry from '@/components/LayoutSwitchEntry.vue'
import SideMenu from '@/components/SideMenu.vue'
import LocaleSwitch from '@/components/LocaleSwitch.vue'
import ThemeSwitch from '@/components/ThemeSwitch.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const themeStore = useThemeStore()
const { t, te } = useI18n()

const drawerOpen = ref(false)

const collapsed = computed(() => appStore.sidebarCollapsed && !appStore.isMobile)

/** 顶栏标题：meta.title 存 i18n key，命中翻译 / 未命中回退原值 */
const pageTitle = computed(() => {
  const key = route.meta.title
  if (key && te(key)) return t(key)
  return key || appStore.siteName
})
const realName = computed(() => userStore.realName)
const avatarText = computed(() => (realName.value || 'U').charAt(0).toUpperCase())

// ===== Phase6 前台主题挂载（ARCH §七.3）=====
// 仅写 body[data-if-theme]，严禁写 document.documentElement；
// 离开前台布局时移除属性，与后台天然互斥。
onMounted(() => {
  themeStore.applyFrontTheme()
})
onBeforeUnmount(() => {
  themeStore.removeFrontTheme()
})
// 防御：主题值被外部（如网站设置默认值）变更时同步到 body
watch(
  () => themeStore.frontTheme,
  () => themeStore.applyFrontTheme()
)

function toggleMenu() {
  if (appStore.isMobile) {
    drawerOpen.value = !drawerOpen.value
  } else {
    appStore.toggleSidebar()
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm(t('layout.msg.logoutConfirm'), t('common.msg.tip'), { type: 'warning' })
      .then(() => {
        userStore.logout()
        router.replace('/login')
        ElMessage.success(t('layout.msg.logoutSuccess'))
      })
      .catch(() => {})
  } else if (cmd === 'clearCache') {
    localStorage.clear()
    ElMessage.success(t('layout.msg.cacheCleared'))
    setTimeout(() => window.location.reload(), 600)
  }
}

function handleResize() {
  appStore.setDevice(window.innerWidth <= 768 ? 'mobile' : 'desktop')
  if (!appStore.isMobile) drawerOpen.value = false
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* 普通端：浅色侧栏 / 内容居中定宽 / 大圆角柔和风格
   Phase6：--if-sidebar-bg 不再硬编码，跟随 themes.css 的主题变量 */
.if-layout--user {
  --if-content-max: 1200px;
  --if-radius: 16px;
}

/* 侧栏 flex 列布局 + 100vh，底部入口 margin-top:auto 吸底（T7） */
.if-layout--user .if-sidebar {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.dd-text {
  margin-left: 6px;
}
</style>

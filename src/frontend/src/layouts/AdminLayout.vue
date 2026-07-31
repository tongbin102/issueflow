<template>
  <div class="if-layout if-layout--admin">
    <!-- 侧边栏：桌面常驻 / 移动端抽屉；T7：100vh flex 列布局，切换入口 margin-top:auto 吸底 -->
    <aside
      class="if-sidebar"
      :class="{
        'is-collapsed': appStore.sidebarCollapsed && !appStore.isMobile,
        'is-mobile-open': appStore.isMobile && drawerOpen
      }"
    >
      <div class="if-logo">
        <!-- Phase8 W1 #10：展开态跟随「网站名称」配置（appStore.siteName 自带 'issueFlow' 兜底） -->
        <span v-if="!collapsed">{{ appStore.siteName }}</span>
        <span v-else>{{ appStore.siteShortName }}</span>
      </div>
      <!-- R1：菜单内滚动容器（flex:1 + overflow-y:auto） -->
      <div class="if-sidebar__menu">
        <SideMenu :type="2" />
      </div>
      <!-- 侧栏底部「返回前台」入口：普通文档流 + margin-top:auto 吸底（T7，不再 fixed） -->
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
          <!-- Phase6：后台语言切换（后台不提供前台主题切换） -->
          <LocaleSwitch />
          <el-dropdown @command="onCommand">
            <span class="user-dropdown">
              <el-avatar :size="28">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="styleSettings">
                  <el-icon><Brush /></el-icon><span class="dd-text">{{ t('layout.topbar.styleSettings') }}</span>
                </el-dropdown-item>
                <el-dropdown-item command="clearCache">
                  <el-icon><Refresh /></el-icon><span class="dd-text">{{ t('layout.topbar.clearCache') }}</span>
                </el-dropdown-item>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon><span class="dd-text">{{ t('layout.topbar.profile') }}</span>
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

    <!-- 个人设置：只读信息（T7：弹窗 → 统一 FormDrawer 抽屉） -->
    <FormDrawer
      v-model="profileVisible"
      :title="t('layout.topbar.profile')"
      size="sm"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('layout.profile.realName')">{{ realName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('layout.profile.username')">{{ userStore.userInfo.username || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('layout.profile.role')">{{ roleText }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="profile-footer">
          <el-button @click="profileVisible = false">{{ t('common.action.close') }}</el-button>
        </div>
      </template>
    </FormDrawer>

    <!-- R7 整体风格设置抽屉（仅作用于后台） -->
    <AdminStyleDrawer
      v-model="styleDrawerVisible"
      :state="styleState"
      @change="onStyleChange"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Menu, ArrowDown, User, Refresh, SwitchButton, Brush } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useThemeStore } from '@/store/theme'
import { roleLabelI18n } from '@/utils/i18nEnum'
import { applyAdminStyleVars } from '@/utils/theme'
import { loadAdminStyle } from '@/utils/adminStyle'
import LayoutSwitchEntry from '@/components/LayoutSwitchEntry.vue'
import SideMenu from '@/components/SideMenu.vue'
import AdminStyleDrawer from '@/components/AdminStyleDrawer.vue'
import FormDrawer from '@/components/FormDrawer.vue'
import LocaleSwitch from '@/components/LocaleSwitch.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const themeStore = useThemeStore()
const { t, te } = useI18n()

const drawerOpen = ref(false)
const profileVisible = ref(false)
const styleDrawerVisible = ref(false)
const styleState = ref(loadAdminStyle())

const collapsed = computed(() => appStore.sidebarCollapsed && !appStore.isMobile)

/** 将当前后台风格应用到 AdminLayout 根元素（仅作用域，不污染前台）。 */
function applyStyle() {
  const rootEl = document.querySelector('.if-layout--admin')
  if (rootEl) applyAdminStyleVars(styleState.value, rootEl)
}
function onStyleChange(next) {
  styleState.value = next
  applyStyle()
}

/** 顶栏标题：meta.title 存 i18n key，命中翻译 / 未命中回退原值 */
const pageTitle = computed(() => {
  const key = route.meta.title
  if (key && te(key)) return t(key)
  // Phase8 W1 #10：兜底改为站点名称配置，与 UserLayout 保持一致
  return key || appStore.siteName
})
const realName = computed(() => userStore.realName)
const avatarText = computed(() => (realName.value || 'A').charAt(0).toUpperCase())
const roleText = computed(() => {
  const roles = userStore.roles || []
  return roles.length ? roles.map((r) => roleLabelI18n(r)).join(' / ') : '-'
})

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
  } else if (cmd === 'styleSettings') {
    styleDrawerVisible.value = true
  } else if (cmd === 'clearCache') {
    localStorage.clear()
    ElMessage.success(t('layout.msg.cacheCleared'))
    setTimeout(() => window.location.reload(), 600)
  } else if (cmd === 'profile') {
    profileVisible.value = true
  }
}

function handleResize() {
  appStore.setDevice(window.innerWidth <= 768 ? 'mobile' : 'desktop')
  if (!appStore.isMobile) drawerOpen.value = false
}

onMounted(() => {
  // Phase6：后台强制移除前台主题属性（ARCH §七.3，双保险；UserLayout 卸载时也会移除）
  themeStore.removeFrontTheme()
  handleResize()
  window.addEventListener('resize', handleResize)
  applyStyle()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* 后台：紧凑 / 内容满宽 / 小圆角；侧栏类型由 R7 风格变量驱动 */
.if-layout--admin {
  --admin-sidebar-bg: #1f2d3d;
  --admin-sidebar-text: #c0c4cc;
  --admin-content-max: none;
  --if-radius: 4px;
}

/* T7：侧栏 100vh flex 列布局，切换入口靠 margin-top:auto 吸底，
   移除旧 fixed 方案的 padding-bottom 预留 */
.if-layout--admin .if-sidebar {
  display: flex;
  flex-direction: column;
  height: 100vh;
  /* 背景跟随 R7 侧栏类型变量（深色/浅色），不污染全局 :root */
  background: var(--admin-sidebar-bg);
  /* 仅在本作用域内覆盖 Element Plus 菜单变量，不污染全局 */
  --el-menu-text-color: var(--admin-sidebar-text);
  --el-menu-hover-bg-color: #263445;
  --el-menu-active-color: var(--theme-color);
  --el-menu-border-color: transparent;
}

/* 深色底上 logo 反白 */
.if-layout--admin .if-logo {
  color: #ffffff;
}

/* 激活项背景高亮（EP 默认仅改文字色，这里补背景保证对比度） */
.if-layout--admin .if-menu .el-menu-item.is-active {
  background-color: color-mix(in srgb, var(--theme-color) 16%, transparent);
}

/* 高密度：压缩内容区内边距 */
.if-layout--admin .if-content {
  padding: 12px;
}

/* 下拉项图标与文字间距 */
.dd-text {
  margin-left: 6px;
}

.profile-footer {
  display: flex;
  justify-content: flex-end;
}
</style>

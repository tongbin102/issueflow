<template>
  <div class="if-layout if-layout--admin">
    <!-- 侧边栏：桌面常驻 / 移动端抽屉 -->
    <aside
      class="if-sidebar"
      :class="{
        'is-collapsed': appStore.sidebarCollapsed && !appStore.isMobile,
        'is-mobile-open': appStore.isMobile && drawerOpen
      }"
    >
      <div class="if-logo">
        <span v-if="!collapsed">issueFlow 后台</span>
        <span v-else>IF</span>
      </div>
      <SideMenu :type="2" />
      <!-- 侧栏底部「切换区域」次级入口（折叠态降级为纯图标） -->
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
          <el-dropdown @command="onCommand">
            <span class="user-dropdown">
              <el-avatar :size="28">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="styleSettings">
                  <el-icon><Brush /></el-icon><span class="dd-text">整体风格设置</span>
                </el-dropdown-item>
                <el-dropdown-item command="clearCache">
                  <el-icon><Refresh /></el-icon><span class="dd-text">清理缓存</span>
                </el-dropdown-item>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon><span class="dd-text">个人设置</span>
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon><span class="dd-text">退出登录</span>
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

    <!-- 个人设置：只读信息 -->
    <el-dialog v-model="profileVisible" title="个人设置" width="420px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="姓名">{{ realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ userStore.userInfo.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleText }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="profileVisible = false">关闭</el-button>
      </template>
    </el-dialog>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Menu, ArrowDown, User, Refresh, SwitchButton, Brush } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { roleLabel } from '@/utils/format'
import { applyAdminStyleVars } from '@/utils/theme'
import { loadAdminStyle } from '@/utils/adminStyle'
import LayoutSwitchEntry from '@/components/LayoutSwitchEntry.vue'
import SideMenu from '@/components/SideMenu.vue'
import AdminStyleDrawer from '@/components/AdminStyleDrawer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const drawerOpen = ref(false)
const profileVisible = ref(false)
const styleDrawerVisible = ref(false)
const styleState = ref(loadAdminStyle())

/** 将当前后台风格应用到 AdminLayout 根元素（仅作用域，不污染前台）。 */
function applyStyle() {
  const rootEl = document.querySelector('.if-layout--admin')
  if (rootEl) applyAdminStyleVars(styleState.value, rootEl)
}
function onStyleChange(next) {
  styleState.value = next
  applyStyle()
}

const pageTitle = computed(() => route.meta.title || '管理后台')
const realName = computed(() => userStore.realName)
const avatarText = computed(() => (realName.value || 'A').charAt(0).toUpperCase())
const roleText = computed(() => {
  const roles = userStore.roles || []
  return roles.length ? roles.map((r) => roleLabel(r)).join('、') : '-'
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
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout()
        router.replace('/login')
        ElMessage.success('已退出登录')
      })
      .catch(() => {})
  } else if (cmd === 'styleSettings') {
    styleDrawerVisible.value = true
  } else if (cmd === 'clearCache') {
    localStorage.clear()
    ElMessage.success('缓存已清理，即将刷新页面')
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

.if-layout--admin .if-sidebar {
  display: flex;
  flex-direction: column;
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
</style>

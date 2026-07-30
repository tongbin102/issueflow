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
        <span v-if="!collapsed">issueFlow</span>
        <span v-else>IF</span>
      </div>
      <el-menu
        class="if-menu"
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        background-color="transparent"
      >
        <el-menu-item index="/user">
          <el-icon><HomeFilled /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item index="/user/my-issues">
          <el-icon><Tickets /></el-icon>
          <template #title>我的问题</template>
        </el-menu-item>
        <el-menu-item index="/user/submit-issue">
          <el-icon><EditPen /></el-icon>
          <template #title>提交问题</template>
        </el-menu-item>
        <el-menu-item index="/user/stats">
          <el-icon><DataLine /></el-icon>
          <template #title>个人看板</template>
        </el-menu-item>
      </el-menu>
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
          <!-- 顶栏「切换区域」入口（含 ADMIN 显隐），作为第一个子元素 -->
          <LayoutSwitchEntry variant="topbar" />
          <el-color-picker
            v-model="themeColor"
            size="small"
            @change="onThemeChange"
          />
          <el-dropdown @command="onCommand">
            <span class="user-dropdown">
              <el-avatar :size="28">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { HomeFilled, Menu, ArrowDown, Tickets, EditPen, DataLine } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useThemeStore } from '@/store/theme'
import LayoutSwitchEntry from '@/components/LayoutSwitchEntry.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const themeStore = useThemeStore()

const drawerOpen = ref(false)
const themeColor = ref(themeStore.themeColor)

const collapsed = computed(() => appStore.sidebarCollapsed && !appStore.isMobile)
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || 'issueFlow')
const realName = computed(() => userStore.realName)
const avatarText = computed(() => (realName.value || 'U').charAt(0).toUpperCase())

function toggleMenu() {
  if (appStore.isMobile) {
    drawerOpen.value = !drawerOpen.value
  } else {
    appStore.toggleSidebar()
  }
}

function onThemeChange(color) {
  if (!color) return
  themeStore.setThemeColor(color)
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
/* 普通端：浅色侧栏 / 内容居中定宽 / 大圆角柔和风格 */
.if-layout--user {
  --if-sidebar-bg: #ffffff;
  --if-content-max: 1200px;
  --if-radius: 16px;
}

/* 侧栏改为 flex 列布局，便于底部入口 margin-top:auto 推到底 */
.if-layout--user .if-sidebar {
  display: flex;
  flex-direction: column;
}
</style>

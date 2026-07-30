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
      <el-menu
        class="if-menu"
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        background-color="transparent"
      >
        <el-menu-item index="/admin/index">
          <el-icon><DataLine /></el-icon>
          <template #title>概览</template>
        </el-menu-item>
        <el-menu-item index="/admin/issues">
          <el-icon><Tickets /></el-icon>
          <template #title>问题管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/projects">
          <el-icon><Folder /></el-icon>
          <template #title>项目管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/flow-monitor">
          <el-icon><Switch /></el-icon>
          <template #title>流程监控</template>
        </el-menu-item>
        <el-sub-menu index="/admin/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/admin/system/users">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/system/organizations">
            <el-icon><OfficeBuilding /></el-icon>
            <template #title>组织管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/system/menus">
            <el-icon><Grid /></el-icon>
            <template #title>菜单管理</template>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/admin/flow-config">
          <el-icon><Tools /></el-icon>
          <template #title>流程配置</template>
        </el-menu-item>
        <el-menu-item index="/admin/settings">
          <el-icon><Brush /></el-icon>
          <template #title>系统设置</template>
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
          <!-- 顶栏「切换区域」入口（所有已登录用户可见），作为第一个子元素 -->
          <LayoutSwitchEntry variant="topbar" />
          <el-dropdown @command="onCommand">
            <span class="user-dropdown">
              <el-avatar :size="28">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DataLine,
  Menu,
  ArrowDown,
  Tickets,
  Switch,
  User,
  Setting,
  Brush,
  Tools,
  Folder,
  OfficeBuilding,
  Grid,
  Refresh,
  SwitchButton
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { roleLabel } from '@/utils/format'
import LayoutSwitchEntry from '@/components/LayoutSwitchEntry.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const drawerOpen = ref(false)
const profileVisible = ref(false)

const collapsed = computed(() => appStore.sidebarCollapsed && !appStore.isMobile)
const activeMenu = computed(() => route.path)
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
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* 后台：紧凑 / 内容满宽 / 小圆角；侧栏固定深蓝灰（不随 themeColor 变化） */
.if-layout--admin {
  --if-sidebar-bg: #1f2d3d;
  --if-content-max: none;
  --if-radius: 4px;
}

.if-layout--admin .if-sidebar {
  display: flex;
  flex-direction: column;
  /* 固定深蓝灰背景，与全局 --theme-color 解耦 */
  background: var(--if-sidebar-bg);
  /* 仅在本作用域内覆盖 Element Plus 菜单变量，不污染全局 */
  --el-menu-text-color: #c0c4cc;
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

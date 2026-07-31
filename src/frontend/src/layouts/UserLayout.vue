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
          <el-dropdown popper-class="if-user-topbar-dropdown" @command="onCommand">
            <span class="user-dropdown">
              <!-- Phase7 T5：头像改用 UserAvatar（有图显示图 / 无图首字母 + 稳定色） -->
              <UserAvatar
                :user-id="userStore.userId"
                :avatar="userStore.avatar"
                :name="displayName"
                :size="28"
                :version="userStore.avatarVersion"
              />
              <span class="user-name">{{ displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
            <el-dropdown-menu>
              <!-- R1：个人中心入口，位于「退出登录」之上、divided 之前 -->
              <el-dropdown-item command="profileCenter">
                <el-icon><User /></el-icon><span class="dd-text">{{ t('layout.topbar.profileCenter') }}</span>
              </el-dropdown-item>
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
      <!-- Phase8 W1 #4：前台页脚（版权 / 备案号，二者皆空则整体不渲染；后台不加） -->
      <footer v-if="showFooter" class="if-footer">
        <span v-if="appStore.siteCopyright" class="if-footer__item">{{ appStore.siteCopyright }}</span>
        <span v-if="appStore.siteIcp" class="if-footer__item">{{ appStore.siteIcp }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Menu, ArrowDown, Refresh, SwitchButton, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useThemeStore } from '@/store/theme'
import UserAvatar from '@/components/UserAvatar.vue'
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
/** 顶栏展示名：昵称优先 → 姓名 → 账号（Phase7 T5 起 userInfo 带 nickname） */
const displayName = computed(() => userStore.displayName || userStore.realName)
/** Phase8 W1 #4：版权与备案号任一非空才渲染页脚 */
const showFooter = computed(() => !!(appStore.siteCopyright || appStore.siteIcp))

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
  } else if (cmd === 'profileCenter') {
    // R1：前台头像下拉 →「个人中心」
    router.push('/user/profile')
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

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

/* Phase8 W1 #4：前台页脚。位于 .if-main（flex 列）底部，
   .if-content 保持 flex:1 + overflow:auto，故页脚不参与滚动、也不遮挡内容 */
.if-layout--user .if-footer {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 16px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>

<!-- 下拉菜单被 teleport 到 body，scoped 选择器命中不到，故单独用非 scoped 块 + popper-class 限定作用域 -->
<style>
.if-user-topbar-dropdown .el-dropdown-menu__item {
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  /* 移动端下拉项触控热区 ≥44px（ARCH §2.5-114） */
  .if-user-topbar-dropdown .el-dropdown-menu__item {
    min-height: 44px;
    line-height: 44px;
    font-size: 15px;
  }
}
</style>

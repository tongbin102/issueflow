<template>
  <!-- 双向跳转入口：根据当前路由前缀推断所属端，统一处理显隐与跳转 -->
  <div v-if="visible" class="if-switch-entry" :class="`if-switch-entry--${variant}`">
    <!-- 顶栏形态：文字 + 图标；始终保留 -->
    <el-button
      v-if="variant === 'topbar'"
      text
      bg
      size="small"
      class="if-switch-entry__btn"
      @click="handleClick"
    >
      <el-icon><component :is="entryIcon" /></el-icon>
      <span class="if-switch-entry__label">{{ label }}</span>
    </el-button>

    <!-- 侧栏底部形态：占满侧栏底部宽度；折叠态降级为纯图标 -->
    <el-button
      v-else
      class="if-switch-entry__btn if-switch-entry__btn--sidebar"
      :class="{ 'is-icon-only': sidebarCollapsed }"
      @click="handleClick"
    >
      <el-icon><component :is="entryIcon" /></el-icon>
      <span v-if="!sidebarCollapsed" class="if-switch-entry__label">{{ label }}</span>
    </el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Setting, HomeFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'

const props = defineProps({
  /** 'topbar' 顶栏入口；'sidebar' 侧栏底部入口 */
  variant: {
    type: String,
    default: 'topbar',
    validator: (value) => ['topbar', 'sidebar'].includes(value)
  }
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const { t } = useI18n()

/**
 * 当前是否处于后台上下文：以路由前缀 /admin 推断，
 * 不新增 route.meta，避免改动路由配置。
 */
const isAdminContext = computed(() => route.path.startsWith('/admin'))

/** 当前用户是否含 ADMIN 角色。 */
const isAdmin = computed(() => (userStore.roles || []).includes('ADMIN'))

/**
 * 显隐规则：
 * - 后台侧（/admin）：所有已登录用户都可见「返回前台」；
 * - 普通端：仅 ADMIN 角色可见「管理后台」。
 */
const visible = computed(() =>
  isAdminContext.value ? !!userStore.isLoggedIn : isAdmin.value
)

/** 跳转目标：后台→普通端 '/user'，普通端→后台 '/admin/index'。 */
const target = computed(() => (isAdminContext.value ? '/user' : '/admin/index'))

/** 按钮文案（i18n，随语言切换响应式更新）。 */
const label = computed(() =>
  isAdminContext.value ? t('layout.switch.toUser') : t('layout.switch.toAdmin')
)

/** 图标：普通端用设置图标，后台用首页实心图标。 */
const entryIcon = computed(() => (isAdminContext.value ? HomeFilled : Setting))

/** 侧栏折叠态（桌面 220px→64px）：用于 sidebar 形态降级为纯图标。 */
const sidebarCollapsed = computed(
  () => appStore.sidebarCollapsed && !appStore.isMobile
)

function handleClick() {
  router.push(target.value)
}
</script>

<style scoped>
.if-switch-entry--topbar {
  display: inline-flex;
  align-items: center;
}

/* T7 侧栏底部形态：改为普通文档流 + margin-top:auto，
   依赖父级侧栏的 flex 纵向布局（100vh）自然吸底；
   相比旧 position:fixed 方案：不再遮挡菜单末项、宽度天然跟随侧栏折叠、移动端抽屉内随动。 */
.if-switch-entry--sidebar {
  position: static;
  margin-top: auto;
  width: 100%;
  padding: 12px;
  flex-shrink: 0;
  background: var(--admin-sidebar-bg, var(--if-sidebar-bg, var(--bg-container)));
}

.if-switch-entry__btn--sidebar {
  width: 100%;
  margin: 0;
  justify-content: center;
}

/* 折叠态：纯图标，去掉左右内边距与文字 */
.if-switch-entry__btn--sidebar.is-icon-only {
  padding-left: 0;
  padding-right: 0;
}

.if-switch-entry__label {
  margin-left: 4px;
}
</style>

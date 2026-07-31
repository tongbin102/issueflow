import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import routes from './routes'
import { t } from '@/locales'
import { useUserStore } from '@/store/user'

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫：
 * 1. 公开路由（/login、/403、404）直接放行；已登录访问 /login 跳首页。
 * 2. 未登录访问受保护路由 → /login。
 * 3. 已登录但角色未加载 → 先 getInfo() 兜底。
 * 4. 路由 meta.roles 与 user.roles 校验，不匹配 → /403。
 */
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const isPublic = to.meta && to.meta.public

  if (isPublic) {
    if (to.name === 'login' && userStore.isLoggedIn) {
      next(userStore.defaultHomePath())
      return
    }
    next()
    return
  }

  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 角色未加载（本地态失效）时尝试拉取
  if (!userStore.roles || userStore.roles.length === 0) {
    try {
      await userStore.getInfo()
    } catch (e) {
      userStore.logout()
      next({ path: '/login' })
      return
    }
  }

  const requiredRoles = to.meta && to.meta.roles
  if (Array.isArray(requiredRoles) && requiredRoles.length > 0) {
    const ok = (userStore.roles || []).some((r) => requiredRoles.includes(r))
    if (!ok) {
      ElMessage.error(t('error.msg.noPermission'))
      next({ path: '/403' })
      return
    }
  }

  next()
})

export default router

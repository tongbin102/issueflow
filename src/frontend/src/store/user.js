import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, getInfo as apiGetInfo } from '@/api/auth'
import { getRolePermissions } from '@/api/role'
import { getToken, setToken, removeToken } from '@/utils/auth'

const USER_KEY = 'if_user'

function loadUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null') || null
  } catch (e) {
    return null
  }
}

const persisted = loadUser()

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: (persisted && persisted.userInfo) || {},
    roles: (persisted && persisted.roles) || [],
    /** 当前角色拥有的权限码集合（来自 GET /roles/{id}/permissions） */
    permissions: (persisted && persisted.permissions) || [],
    /**
     * 头像版本号（Phase7 T5）：个人中心上传头像后自增，
     * UserAvatar 监听到变化即重新拉流，顶栏头像无需刷新页面即可同步。
     * 仅内存态，不持久化。
     */
    avatarVersion: 0
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    // 主角色（用于快捷判断 / 默认首页）
    role: (state) => (state.roles && state.roles.length ? state.roles[0] : ''),
    isAdmin: (state) => (state.roles || []).includes('ADMIN'),
    isDeveloper: (state) => (state.roles || []).includes('DEVELOPER'),
    isTester: (state) => (state.roles || []).includes('TESTER'),
    isSubmitter: (state) => (state.roles || []).includes('SUBMITTER'),
    realName: (state) => {
      const info = state.userInfo || {}
      return info.realName || info.username || ''
    },
    /** 当前用户 id（头像端点 /api/profile/avatar/{userId} 需要） */
    userId: (state) => (state.userInfo && state.userInfo.id) || null,
    /** 头像相对路径，为空表示未设置（UserAvatar 退化为首字母） */
    avatar: (state) => (state.userInfo && state.userInfo.avatar) || '',
    /** 顶栏展示名：昵称优先 → 姓名 → 账号（Phase7 T5 起消费 nickname） */
    displayName: (state) => {
      const info = state.userInfo || {}
      return info.nickname || info.realName || info.username || ''
    },
    /**
     * 细粒度权限判定（按钮级）。
     * @param {string|string[]} code 权限码（如 'issue:create' 或 ['issue:create','issue:update']）
     * @returns {boolean}
     */
    hasPerm: (state) => (code) => {
      const perms = state.permissions || []
      if (!code) return true
      const list = Array.isArray(code) ? code : [code]
      return list.some((c) => perms.includes(c))
    }
  },
  actions: {
    persist() {
      localStorage.setItem(
        USER_KEY,
        JSON.stringify({
          userInfo: this.userInfo,
          roles: this.roles,
          permissions: this.permissions
        })
      )
    },
    /**
     * 拉取当前角色权限码集合并写入 state。
     * 权限与单角色模型绑定：userInfo.roleId → GET /roles/{id}/permissions。
     */
    async loadPermissions() {
      const roleId = this.userInfo && this.userInfo.roleId
      if (!roleId) {
        this.permissions = []
        return
      }
      try {
        this.permissions = (await getRolePermissions(roleId)) || []
      } catch (e) {
        this.permissions = []
      }
    },
    /**
     * 登录：调用 api/auth.login，返回 LoginVO{token,userInfo,roles}。
     * api 层已解包到 data，这里直接消费。
     */
    async login(username, password) {
      const data = await apiLogin({ username, password })
      this.token = data.token || ''
      this.userInfo = data.userInfo || {}
      this.roles = Array.isArray(data.roles)
        ? data.roles
        : data.role
          ? [data.role]
          : []
      setToken(this.token)
      await this.loadPermissions()
      this.persist()
      return data
    },
    /** 拉取当前用户信息（用于刷新态兜底 / 角色补全）。 */
    async getInfo() {
      const data = await apiGetInfo()
      this.userInfo = data.userInfo || this.userInfo
      this.roles = Array.isArray(data.roles)
        ? data.roles
        : data.role
          ? [data.role]
          : this.roles
      if (this.token) setToken(this.token)
      await this.loadPermissions()
      this.persist()
      return data
    },
    /**
     * 本地写入新头像路径并自增版本号（Phase7 T5）。
     * 用于个人中心上传成功后即时刷新顶栏头像，避免多一次 /auth/info 往返。
     * @param {string} path 头像相对路径
     */
    setAvatar(path) {
      this.userInfo = { ...(this.userInfo || {}), avatar: path || '' }
      this.avatarVersion += 1
      this.persist()
    },
    /**
     * 重新拉取当前用户信息并刷新顶栏展示（Phase7 T5）。
     * 失败静默降级：个人中心保存本身已成功，不应因刷新失败弹二次错误。
     * @returns {Promise<boolean>} 是否刷新成功
     */
    async refreshUserInfo() {
      try {
        await this.getInfo()
        this.avatarVersion += 1
        return true
      } catch (e) {
        return false
      }
    },
    /** 登出：调用后端写 Redis 黑名单（失败忽略），再清本地态。 */
    logout() {
      try {
        apiLogout().catch(() => {})
      } catch (e) {
        /* ignore offline */
      }
      this.token = ''
      this.userInfo = {}
      this.roles = []
      this.permissions = []
      this.avatarVersion = 0
      removeToken()
      localStorage.removeItem(USER_KEY)
    },
    /** 按角色返回默认首页路径。 */
    defaultHomePath() {
      return this.isAdmin ? '/admin' : '/user'
    }
  }
})

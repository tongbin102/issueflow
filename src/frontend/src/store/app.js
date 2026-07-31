import { defineStore } from 'pinia'
import { getSiteConfig } from '@/api/site'

const APP_KEY = 'if_app'

function loadApp() {
  try {
    return JSON.parse(localStorage.getItem(APP_KEY) || 'null') || {}
  } catch (e) {
    return {}
  }
}

const persisted = loadApp()

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: !!(persisted && persisted.sidebarCollapsed),
    device: 'desktop', // desktop | mobile
    /**
     * Phase6：网站设置（GET /api/site/config 返回的 site.* 七键扁平 Map）。
     * null 表示未加载 / 加载失败，此时各 getter 回落前端硬编码默认值。
     */
    siteConfig: null
  }),
  getters: {
    isMobile: (state) => state.device === 'mobile',
    /** 网站名称（登录页大标题 / 浏览器标题） */
    siteName: (state) => (state.siteConfig && state.siteConfig['site.name']) || 'issueFlow',
    /** 网站简称（侧栏折叠态 Logo） */
    siteShortName: (state) =>
      (state.siteConfig && state.siteConfig['site.short_name']) || 'IF',
    /** 副标题（登录页 / 浏览器标题后缀） */
    siteSubtitle: (state) =>
      state.siteConfig ? state.siteConfig['site.subtitle'] || '' : '问题跟踪与流程管理平台',
    /** 后台配置的前台默认主题 */
    siteDefaultTheme: (state) =>
      (state.siteConfig && state.siteConfig['site.default_theme']) || 'light',
    /** 后台配置的默认语言 */
    siteDefaultLocale: (state) =>
      (state.siteConfig && state.siteConfig['site.default_locale']) || 'zh-CN',
    /** 版权信息（页脚） */
    siteCopyright: (state) =>
      (state.siteConfig && state.siteConfig['site.copyright']) || '(c) 2026 issueFlow',
    /** 备案号（页脚，可为空） */
    siteIcp: (state) => (state.siteConfig && state.siteConfig['site.icp']) || ''
  },
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      this.persist()
    },
    /** 直接写入站点配置（保存成功后本地同步用，避免重复请求）。 */
    setSiteConfig(config) {
      this.siteConfig = config || null
    },
    /**
     * 拉取网站设置（公开接口，无需登录）。
     * @returns {Promise<Object>} site.* 七键 Map
     */
    async fetchSiteConfig() {
      const data = await getSiteConfig()
      this.setSiteConfig(data)
      return data
    },
    setSidebarCollapsed(value) {
      this.sidebarCollapsed = !!value
      this.persist()
    },
    setDevice(device) {
      this.device = device
    },
    persist() {
      localStorage.setItem(
        APP_KEY,
        JSON.stringify({ sidebarCollapsed: this.sidebarCollapsed })
      )
    }
  }
})

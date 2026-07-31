import { defineStore } from 'pinia'
import { applyThemeVars } from '@/utils/theme'

const THEME_KEY = 'if_theme'

function loadTheme() {
  try {
    return JSON.parse(localStorage.getItem(THEME_KEY) || 'null') || {}
  } catch (e) {
    return {}
  }
}

const persisted = loadTheme()

/** Phase6 前台主题合法集合（Q2 决策：4 种） */
export const FRONT_THEMES = ['light', 'dark', 'blue', 'green']

export const useThemeStore = defineStore('theme', {
  state: () => ({
    themeColor: (persisted && persisted.themeColor) || '#409EFF',
    layout: (persisted && persisted.layout) || 'side', // side | top | mix
    menuConfig: (persisted && persisted.menuConfig) || {},
    /** Phase6：前台主题 key（light/dark/blue/green），仅作用于 body[data-if-theme] */
    frontTheme: FRONT_THEMES.includes(persisted && persisted.frontTheme)
      ? persisted.frontTheme
      : 'light',
    /** 用户是否手动切换过前台主题（true 则不再跟随后台默认值） */
    frontThemeTouched: !!(persisted && persisted.frontThemeTouched)
  }),
  getters: {
    currentThemeColor: (state) => state.themeColor
  },
  actions: {
    init() {
      applyThemeVars({ themeColor: this.themeColor, layout: this.layout })
    },
    /** 整体设置（主题色 / 布局 / 菜单配置）。 */
    setTheme(partial) {
      if (partial && partial.themeColor) this.themeColor = partial.themeColor
      if (partial && partial.layout) this.layout = partial.layout
      if (partial && partial.menuConfig) this.menuConfig = partial.menuConfig
      applyThemeVars({ themeColor: this.themeColor, layout: this.layout })
      this.persist()
    },
    /** 仅切换主题色（登录后即可用）。 */
    setThemeColor(color) {
      if (!color) return
      this.themeColor = color
      applyThemeVars({ themeColor: this.themeColor })
      this.persist()
    },
    /**
     * Phase6：切换前台主题（用户主动操作，标记 touched 并持久化）。
     * @param {string} key light | dark | blue | green
     */
    setFrontTheme(key) {
      if (!FRONT_THEMES.includes(key)) return
      this.frontTheme = key
      this.frontThemeTouched = true
      this.applyFrontTheme()
      this.persist()
    },
    /**
     * 将当前前台主题写入 body[data-if-theme]。
     * 仅允许 UserLayout 挂载期间调用；严禁写 document.documentElement（ARCH §七.3）。
     */
    applyFrontTheme() {
      document.body.setAttribute('data-if-theme', this.frontTheme)
    },
    /** 移除 body 上的前台主题属性（AdminLayout 挂载时调用，后台回落默认变量）。 */
    removeFrontTheme() {
      document.body.removeAttribute('data-if-theme')
    },
    /**
     * 用网站设置的默认主题初始化（仅当用户从未手动切换过前台主题时生效）。
     * 不落 localStorage 的 touched 标记，保持继续跟随后台默认值的语义。
     * @param {string} siteDefault light | dark | blue | green
     */
    initFrontThemeFromSite(siteDefault) {
      if (this.frontThemeTouched) return
      if (FRONT_THEMES.includes(siteDefault)) {
        this.frontTheme = siteDefault
      }
    },
    persist() {
      localStorage.setItem(
        THEME_KEY,
        JSON.stringify({
          themeColor: this.themeColor,
          layout: this.layout,
          menuConfig: this.menuConfig,
          frontTheme: this.frontTheme,
          frontThemeTouched: this.frontThemeTouched
        })
      )
    }
  }
})

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

export const useThemeStore = defineStore('theme', {
  state: () => ({
    themeColor: (persisted && persisted.themeColor) || '#409EFF',
    layout: (persisted && persisted.layout) || 'side', // side | top | mix
    menuConfig: (persisted && persisted.menuConfig) || {}
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
    persist() {
      localStorage.setItem(
        THEME_KEY,
        JSON.stringify({
          themeColor: this.themeColor,
          layout: this.layout,
          menuConfig: this.menuConfig
        })
      )
    }
  }
})

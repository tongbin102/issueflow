import { defineStore } from 'pinia'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import {
  i18n,
  setLocale as applyI18nLocale,
  LOCALE_KEY,
  SUPPORTED_LOCALES,
  resolveInitialLocale
} from '@/locales'

/** Element Plus 语言包映射 */
const EL_LOCALES = {
  'zh-CN': zhCn,
  'en-US': en
}

export const useLocaleStore = defineStore('locale', {
  state: () => ({
    /** 当前语言，初始值已按 localStorage → 默认 zh-CN 解析 */
    locale: resolveInitialLocale()
  }),
  getters: {
    /** 供 <el-config-provider :locale> 使用 */
    elLocale: (state) => EL_LOCALES[state.locale] || zhCn,
    isZh: (state) => state.locale === 'zh-CN'
  },
  actions: {
    /**
     * 切换语言：更新 i18n 实例 + 持久化。
     * @param {string} key 'zh-CN' | 'en-US'
     */
    setLocale(key) {
      if (!SUPPORTED_LOCALES.includes(key)) return
      this.locale = key
      applyI18nLocale(key)
      this.persist()
    },
    /**
     * 用网站设置的默认语言初始化（仅当用户从未手动选择过语言时生效）。
     * @param {string} siteDefault 'zh-CN' | 'en-US'
     */
    initFromSiteConfig(siteDefault) {
      const stored = localStorage.getItem(LOCALE_KEY)
      if (stored && SUPPORTED_LOCALES.includes(stored)) return
      if (siteDefault && SUPPORTED_LOCALES.includes(siteDefault) && siteDefault !== this.locale) {
        this.locale = siteDefault
        applyI18nLocale(siteDefault)
        // 不写 localStorage：保持「未手动选择」语义，继续跟随后台默认值
      }
    },
    persist() {
      localStorage.setItem(LOCALE_KEY, this.locale)
    },
    /** 同步 i18n 实例（防御：确保 store 与 i18n 一致） */
    sync() {
      if (i18n.global.locale.value !== this.locale) {
        applyI18nLocale(this.locale)
      }
    }
  }
})

import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

/** localStorage 语言持久化 key（ARCH §七.2） */
export const LOCALE_KEY = 'if_locale'

/** 合法语言集合 */
export const SUPPORTED_LOCALES = ['zh-CN', 'en-US']

/** 默认语言（后台默认值缺失时的最终兜底） */
export const DEFAULT_LOCALE = 'zh-CN'

/**
 * 解析初始语言。
 * 优先级：localStorage.if_locale → siteConfig['site.default_locale'] → 'zh-CN'
 * @param {string} [siteDefault] 网站设置的默认语言
 * @returns {string}
 */
export function resolveInitialLocale(siteDefault) {
  const stored = localStorage.getItem(LOCALE_KEY)
  if (stored && SUPPORTED_LOCALES.includes(stored)) return stored
  if (siteDefault && SUPPORTED_LOCALES.includes(siteDefault)) return siteDefault
  return DEFAULT_LOCALE
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: resolveInitialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  },
  missing(locale, key) {
    // 缺 key 时控制台告警并回退 fallbackLocale（页面不空白）
    console.warn('[i18n] missing key:', locale, key)
  }
})

/**
 * 切换全局语言（仅改 i18n 实例，不负责持久化；持久化由 store/locale.js 承担）。
 * @param {string} key 'zh-CN' | 'en-US'
 */
export function setLocale(key) {
  if (!SUPPORTED_LOCALES.includes(key)) return
  i18n.global.locale.value = key
}

/** 组件外使用的翻译函数（守卫 / 工具模块） */
export const t = (...args) => i18n.global.t(...args)

export default i18n

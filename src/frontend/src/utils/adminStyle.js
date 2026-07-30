/**
 * 后台整体风格（R7）：仅作用于 AdminLayout，存 localStorage，不进 Pinia。
 */

/** localStorage 键 */
export const ADMIN_STYLE_KEY = 'if_admin_style'

/**
 * 默认后台风格：
 * - 主题模式：亮色
 * - 主题色：Element Plus 主蓝
 * - 侧边菜单类型：深色
 * - 内容区域宽度：流式
 * - 固定 Header / 侧边菜单：开
 * - 色弱模式：关
 */
export const DEFAULT_ADMIN_STYLE = Object.freeze({
  themeMode: 'light', // light | dark
  themeColor: '#409EFF',
  sidebarType: 'dark', // dark | light
  contentWidth: 'fluid', // fluid | fixed
  fixedHeader: true,
  fixedSidebar: true,
  colorWeak: false
})

/**
 * 主题色预设（10 色，沿用设计默认）。
 */
export const ADMIN_THEME_COLORS = Object.freeze([
  { label: '蓝', value: '#409EFF' },
  { label: '浅蓝', value: '#69B1FF' },
  { label: '红', value: '#F56C6C' },
  { label: '橙', value: '#E6A23C' },
  { label: '黄', value: '#FADB14' },
  { label: '绿', value: '#67C23A' },
  { label: '青', value: '#13C2C2' },
  { label: '紫', value: '#722ED1' },
  { label: '紫红', value: '#EB2F96' },
  { label: '粉灰', value: '#D597B9' }
])

/**
 * 读取后台风格（localStorage），失败回退默认。
 * @returns {Object} 合并默认后的风格对象
 */
export function loadAdminStyle() {
  try {
    const raw = localStorage.getItem(ADMIN_STYLE_KEY)
    if (!raw) return { ...DEFAULT_ADMIN_STYLE }
    const parsed = JSON.parse(raw)
    return { ...DEFAULT_ADMIN_STYLE, ...parsed }
  } catch (e) {
    return { ...DEFAULT_ADMIN_STYLE }
  }
}

/**
 * 持久化后台风格到 localStorage。
 * @param {Object} state 风格对象
 */
export function saveAdminStyle(state) {
  try {
    localStorage.setItem(ADMIN_STYLE_KEY, JSON.stringify(state))
  } catch (e) {
    /* 存储异常忽略 */
  }
}

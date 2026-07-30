/**
 * 将主题变量写入 document.documentElement 的 CSS 变量。
 * 与 styles/variables.css 中的默认值对应；同时驱动 Element Plus 主色。
 * 注意：此函数供前台 UserLayout 顶栏 color-picker 使用，写入全局 :root。
 *
 * @param {{themeColor?:string, layout?:string}} theme
 */
export function applyThemeVars(theme = {}) {
  const root = document.documentElement
  if (!root) return
  const { themeColor, layout } = theme

  if (themeColor) {
    root.style.setProperty('--theme-color', themeColor)
    root.style.setProperty('--el-color-primary', themeColor)
    // 生成主色阶梯（hover / active 等）
    for (let i = 1; i <= 5; i++) {
      root.style.setProperty(
        `--el-color-primary-light-${i}`,
        mixColor(themeColor, '#ffffff', i * 0.1)
      )
    }
    root.style.setProperty('--el-color-primary-dark-2', mixColor(themeColor, '#000000', 0.2))
  }

  if (layout) {
    root.style.setProperty('--layout-mode', layout)
  }
}

/**
 * 将后台整体风格变量写入「指定的根元素」（仅 AdminLayout 根 .if-layout--admin），
 * 严禁写入 document.documentElement，以免污染前台 UserLayout。
 *
 * 落点必须是 AdminLayout 根元素；同时设置 data-if-admin-* 属性供 admin-style.css 选择。
 *
 * @param {Object} state 后台风格对象（见 utils/adminStyle.js 的 DEFAULT_ADMIN_STYLE）
 * @param {HTMLElement} rootEl AdminLayout 根元素（.if-layout--admin）
 */
export function applyAdminStyleVars(state, rootEl) {
  if (!rootEl) return
  const style = state || {}

  // 主题色 + Element Plus 主色阶梯（仅作用于后台根子树）
  if (style.themeColor) {
    rootEl.style.setProperty('--theme-color', style.themeColor)
    rootEl.style.setProperty('--el-color-primary', style.themeColor)
    for (let i = 1; i <= 5; i++) {
      rootEl.style.setProperty(
        `--el-color-primary-light-${i}`,
        mixColor(style.themeColor, '#ffffff', i * 0.1)
      )
    }
    rootEl.style.setProperty('--el-color-primary-dark-2', mixColor(style.themeColor, '#000000', 0.2))
  }

  // 侧边菜单类型：深 / 浅
  const sidebarType = style.sidebarType === 'light' ? 'light' : 'dark'
  rootEl.setAttribute('data-if-admin-sidebar', sidebarType)
  rootEl.style.setProperty('--admin-sidebar-bg', sidebarType === 'light' ? '#ffffff' : '#1f2d3d')
  rootEl.style.setProperty('--admin-sidebar-text', sidebarType === 'light' ? '#303133' : '#c0c4cc')

  // 内容区域宽度：流式 / 固定
  const fixed = style.contentWidth === 'fixed'
  rootEl.setAttribute('data-if-admin-content', fixed ? 'fixed' : 'fluid')
  rootEl.style.setProperty('--admin-content-max', fixed ? '1200px' : 'none')

  // 固定 Header / 侧边菜单
  rootEl.style.setProperty('--if-topbar-position', style.fixedHeader === false ? 'static' : 'sticky')
  rootEl.style.setProperty('--if-sidebar-position', style.fixedSidebar === false ? 'static' : 'sticky')

  // 色弱模式
  rootEl.setAttribute('data-if-admin-colorweak', style.colorWeak ? 'true' : 'false')
  rootEl.style.setProperty('--if-color-weak-filter', style.colorWeak ? 'saturate(0.7)' : 'none')

  // 主题模式（亮 / 暗）
  rootEl.setAttribute('data-if-admin-theme', style.themeMode === 'dark' ? 'dark' : 'light')
}

/**
 * 颜色混合（overlay 覆盖到 base）。
 */
function mixColor(base, overlay, weight) {
  const c1 = hexToRgb(base)
  const c2 = hexToRgb(overlay)
  const w = Math.max(0, Math.min(1, weight))
  const r = Math.round(c1.r + (c2.r - c1.r) * w)
  const g = Math.round(c1.g + (c2.g - c1.g) * w)
  const b = Math.round(c1.b + (c2.b - c1.b) * w)
  return rgbToHex(r, g, b)
}

function hexToRgb(hex) {
  let h = (hex || '').replace('#', '')
  if (h.length === 3) h = h.split('').map((x) => x + x).join('')
  const num = parseInt(h, 16)
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255
  }
}

function rgbToHex(r, g, b) {
  const to = (v) => Math.max(0, Math.min(255, v)).toString(16).padStart(2, '0')
  return `#${to(r)}${to(g)}${to(b)}`
}

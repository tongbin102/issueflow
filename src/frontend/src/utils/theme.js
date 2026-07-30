/**
 * 将主题变量写入 document.documentElement 的 CSS 变量。
 * 与 styles/variables.css 中的默认值对应；同时驱动 Element Plus 主色。
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

/**
 * ECharts 主题适配工具（Phase9 T15 / ARCH R4）。
 *
 * 背景：
 *   ECharts 渲染到 canvas，无法消费 CSS 变量。前台切到 dark 主题后，
 *   图表默认的深色文字 + 浅色分割线会与深色卡片背景撞色（黑底黑字）。
 *
 * 方案：
 *   渲染前用 getComputedStyle(document.body) 读取当前生效的主题变量，
 *   转成 ECharts 可用的字面量色值。这样：
 *     - 前台：body[data-if-theme=...] 生效 → 拿到对应主题色；
 *     - 后台：无 data-if-theme → 自动回落 :root 默认值；
 *   无需为图表单独维护一份主题映射表。
 *
 * 注意：语义色（状态 / 严重等级 / 优先级）仍由 utils/format.js 提供，
 *       固定不随主题变化，本文件只处理「中性色」（文字 / 轴线 / 分割线 / 提示框）。
 */
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useThemeStore } from '@/store/theme'

/** 兜底调色板（SSR / 变量缺失时使用，取 :root 浅色默认值）。 */
const FALLBACK_PALETTE = {
  text: '#303133',
  sub: '#909399',
  border: '#e4e7ed',
  container: '#ffffff',
  primary: '#409eff'
}

/**
 * 读取当前生效主题的中性色。
 * @returns {{text:string, sub:string, border:string, container:string, primary:string}}
 */
export function readChartPalette() {
  if (typeof window === 'undefined' || typeof document === 'undefined' || !document.body) {
    return { ...FALLBACK_PALETTE }
  }
  const cs = window.getComputedStyle(document.body)
  const pick = (name, fallback) => {
    const val = cs.getPropertyValue(name)
    return val && val.trim() ? val.trim() : fallback
  }
  return {
    text: pick('--text-primary', FALLBACK_PALETTE.text),
    sub: pick('--text-secondary', FALLBACK_PALETTE.sub),
    border: pick('--border-color', FALLBACK_PALETTE.border),
    container: pick('--bg-container', FALLBACK_PALETTE.container),
    primary: pick('--theme-color', FALLBACK_PALETTE.primary)
  }
}

/**
 * 生成随主题变化的 ECharts 公共配置片段（标题 / 图例 / 提示框 / 坐标轴）。
 * @param {{text:string, sub:string, border:string, container:string}} palette 调色板
 * @returns {Object} 可展开合并进 option 的公共片段
 */
export function chartCommonOption(palette) {
  const p = palette || readChartPalette()
  return {
    textStyle: { color: p.sub },
    titleTextStyle: { fontSize: 14, color: p.text, fontWeight: 600 },
    legendStyle: { textStyle: { color: p.sub } },
    tooltipStyle: {
      backgroundColor: p.container,
      borderColor: p.border,
      textStyle: { color: p.text }
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: p.border } },
      axisTick: { lineStyle: { color: p.border } },
      axisLabel: { color: p.sub },
      splitLine: { show: false }
    },
    valueAxis: {
      axisLine: { show: false },
      axisLabel: { color: p.sub },
      splitLine: { lineStyle: { color: p.border, type: 'dashed' } }
    }
  }
}

/**
 * 主题变化时自动重绘图表。
 *
 * 同时监听两条通道：
 *   1) themeStore.frontTheme —— 前台 4 主题切换；
 *   2) body[data-if-theme] 属性变化 —— 布局切换（前台 ↔ 后台）导致的变量回落。
 *
 * @param {Function} render 重绘回调（通常是组件内的 render()）
 * @returns {void}
 */
export function useChartTheme(render) {
  const themeStore = useThemeStore()
  let observer = null

  watch(
    () => themeStore.frontTheme,
    () => {
      if (typeof render === 'function') render()
    }
  )

  onMounted(() => {
    if (typeof MutationObserver === 'undefined' || !document.body) return
    observer = new MutationObserver(() => {
      if (typeof render === 'function') render()
    })
    observer.observe(document.body, { attributes: true, attributeFilter: ['data-if-theme'] })
  })

  onBeforeUnmount(() => {
    if (observer) {
      observer.disconnect()
      observer = null
    }
  })
}

import { saveAs } from 'file-saver'

/**
 * 统一下载触发（T9/T10 问题导出 / 看板导出复用）。
 * @param {Blob|string} content Blob 或已签名的远程 URL
 * @param {string} filename 下载文件名
 */
export function downloadFile(content, filename) {
  if (!content) return
  if (typeof content === 'string') {
    const a = document.createElement('a')
    a.href = content
    a.download = filename || ''
    a.target = '_blank'
    a.rel = 'noopener'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    return
  }
  saveAs(content, filename)
}

/**
 * 由后端返回的文件流（Blob）下载。
 */
export function downloadBlob(blob, filename) {
  saveAs(blob, filename)
}

/**
 * 由前端 ECharts 实例导出 PNG（看板图表导出）。
 * @param {import('echarts').ECharts} chart ECharts 实例
 * @param {string} filename 文件名
 */
export function exportChartPng(chart, filename = 'chart.png') {
  if (!chart) return
  const url = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' })
  downloadFile(url, filename)
}

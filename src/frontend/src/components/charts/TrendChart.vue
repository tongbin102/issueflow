<template>
  <div ref="chartRef" class="trend-chart" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { useI18n } from 'vue-i18n'
import { exportChartPng } from '@/utils/exportUtil'
import { readChartPalette, chartCommonOption, useChartTheme } from '@/utils/chartTheme'

const { t } = useI18n()

const props = defineProps({
  // 趋势数据：[{ date, count }] 或 [{ day, count }]
  data: { type: Array, default: () => [] },
  height: { type: String, default: '320px' },
  // 标题缺省走 i18n（chart.issue.trend），不硬编码中文
  title: { type: String, default: '' }
})

const chartRef = ref(null)
let chart = null

function buildOption() {
  const list = props.data || []
  const x = list.map((d) => d.date || d.day || '')
  const y = list.map((d) => Number(d.count) || 0)
  // Phase9 T15：中性色随主题读取，避免 dark 主题下黑底黑字
  const palette = readChartPalette()
  const common = chartCommonOption(palette)
  return {
    title: {
      text: props.title || t('chart.issue.trend'),
      left: 'center',
      textStyle: common.titleTextStyle
    },
    textStyle: common.textStyle,
    tooltip: { trigger: 'axis', ...common.tooltipStyle },
    grid: { left: 48, right: 24, top: 48, bottom: 32 },
    xAxis: { type: 'category', data: x, boundaryGap: false, ...common.categoryAxis },
    yAxis: { type: 'value', minInterval: 1, ...common.valueAxis },
    series: [
      {
        name: t('chart.series.count'),
        type: 'line',
        smooth: true,
        data: y,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: palette.primary },
        lineStyle: { width: 2 }
      }
    ]
  }
}

function render() {
  if (chart) chart.setOption(buildOption(), true)
}

// 允许父组件直接传入完整 option（覆盖默认渲染）
function setOption(option) {
  if (chart) chart.setOption(option, true)
}

function exportPng(filename = 'trend.png') {
  if (chart) exportChartPng(chart, filename)
}

function getInstance() {
  return chart
}

function resize() {
  if (chart) chart.resize()
}

defineExpose({ setOption, exportPng, getInstance })

// Phase9 T15：主题切换（4 主题 / 前后台切换）时自动重绘
useChartTheme(render)

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  window.addEventListener('resize', resize)
})

watch(
  () => props.data,
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.trend-chart {
  width: 100%;
}
</style>

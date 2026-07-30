<template>
  <div ref="chartRef" class="trend-chart" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { exportChartPng } from '@/utils/exportUtil'

const props = defineProps({
  // 趋势数据：[{ date, count }] 或 [{ day, count }]
  data: { type: Array, default: () => [] },
  height: { type: String, default: '320px' },
  title: { type: String, default: '趋势' }
})

const chartRef = ref(null)
let chart = null

function buildOption() {
  const list = props.data || []
  const x = list.map((d) => d.date || d.day || '')
  const y = list.map((d) => Number(d.count) || 0)
  return {
    title: { text: props.title, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 48, bottom: 32 },
    xAxis: { type: 'category', data: x, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '数量',
        type: 'line',
        smooth: true,
        data: y,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#409EFF' },
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

<template>
  <div class="dist-chart">
    <div ref="pieRef" class="dist-item" :style="{ height: height }"></div>
    <div ref="barRef" class="dist-item" :style="{ height: height }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { useI18n } from 'vue-i18n'
import { exportChartPng } from '@/utils/exportUtil'
import { statusColor, severityColor } from '@/utils/format'
import { statusLabelI18n, severityLabelI18n } from '@/utils/i18nEnum'
import { readChartPalette, chartCommonOption, useChartTheme } from '@/utils/chartTheme'

const { t } = useI18n()

const props = defineProps({
  // { statusDistribution:[{status,count}], severityRatio:[{severity,count}] }
  data: { type: Object, default: () => ({}) },
  height: { type: String, default: '300px' }
})

const pieRef = ref(null)
const barRef = ref(null)
let pie = null
let bar = null

function buildPie() {
  const list = (props.data && props.data.statusDistribution) || []
  // Phase9 T15：标签走 i18n（原 statusLabel 为 zh 硬编码），色值仍用固定语义色
  const data = list.map((d) => ({
    name: statusLabelI18n(d.status),
    value: Number(d.count) || 0,
    itemStyle: { color: statusColor(d.status) }
  }))
  const palette = readChartPalette()
  const common = chartCommonOption(palette)
  const title = t('chart.issue.byStatus')
  return {
    title: { text: title, left: 'center', textStyle: common.titleTextStyle },
    textStyle: common.textStyle,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', ...common.tooltipStyle },
    legend: { bottom: 0, type: 'scroll', ...common.legendStyle },
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['50%', '46%'],
        data,
        label: { formatter: '{b}\n{c}', color: palette.sub }
      }
    ]
  }
}

function buildBar() {
  const list = (props.data && props.data.severityRatio) || []
  const x = list.map((d) => severityLabelI18n(d.severity))
  const y = list.map((d) => Number(d.count) || 0)
  const colors = list.map((d) => severityColor(d.severity))
  const palette = readChartPalette()
  const common = chartCommonOption(palette)
  return {
    title: { text: t('chart.issue.bySeverity'), left: 'center', textStyle: common.titleTextStyle },
    textStyle: common.textStyle,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...common.tooltipStyle },
    grid: { left: 48, right: 20, top: 48, bottom: 28 },
    xAxis: { type: 'category', data: x, ...common.categoryAxis },
    yAxis: { type: 'value', minInterval: 1, ...common.valueAxis },
    series: [
      {
        name: t('chart.series.count'),
        type: 'bar',
        data: y.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
        barWidth: '46%'
      }
    ]
  }
}

function render() {
  if (pie) pie.setOption(buildPie(), true)
  if (bar) bar.setOption(buildBar(), true)
}

function setOption(data) {
  if (data && data.pie && pie) pie.setOption(data.pie, true)
  if (data && data.bar && bar) bar.setOption(data.bar, true)
}

function exportPng(prefix = 'distribution') {
  if (pie) exportChartPng(pie, `${prefix}-status.png`)
  if (bar) exportChartPng(bar, `${prefix}-severity.png`)
}

function getInstance() {
  return { pie, bar }
}

function resize() {
  if (pie) pie.resize()
  if (bar) bar.resize()
}

defineExpose({ setOption, exportPng, getInstance })

// Phase9 T15：主题切换（4 主题 / 前后台切换）时自动重绘
useChartTheme(render)

onMounted(() => {
  pie = echarts.init(pieRef.value)
  bar = echarts.init(barRef.value)
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
  if (pie) {
    pie.dispose()
    pie = null
  }
  if (bar) {
    bar.dispose()
    bar = null
  }
})
</script>

<style scoped>
.dist-chart {
  display: flex;
  flex-wrap: wrap;
  gap: var(--if-space-sm);
}
.dist-item {
  flex: 1;
  min-width: 280px;
}
</style>

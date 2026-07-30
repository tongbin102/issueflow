<template>
  <div class="dist-chart">
    <div ref="pieRef" class="dist-item" :style="{ height: height }"></div>
    <div ref="barRef" class="dist-item" :style="{ height: height }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { exportChartPng } from '@/utils/exportUtil'
import {
  statusLabel,
  severityLabel,
  statusColor,
  severityColor
} from '@/utils/format'

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
  const data = list.map((d) => ({
    name: statusLabel(d.status),
    value: Number(d.count) || 0,
    itemStyle: { color: statusColor(d.status) }
  }))
  return {
    title: { text: '状态分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        name: '状态分布',
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['50%', '46%'],
        data,
        label: { formatter: '{b}\n{c}' }
      }
    ]
  }
}

function buildBar() {
  const list = (props.data && props.data.severityRatio) || []
  const x = list.map((d) => severityLabel(d.severity))
  const y = list.map((d) => Number(d.count) || 0)
  const colors = list.map((d) => severityColor(d.severity))
  return {
    title: { text: '严重等级占比', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 48, right: 20, top: 48, bottom: 28 },
    xAxis: { type: 'category', data: x },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '数量',
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
  gap: 12px;
}
.dist-item {
  flex: 1;
  min-width: 280px;
}
</style>

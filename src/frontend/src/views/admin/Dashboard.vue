<template>
  <div class="admin-dashboard">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>全局看板</span>
          <DashboardFilters :versions="versions" @search="load" />
        </div>
      </template>

      <el-row :gutter="16" class="kpi-row">
        <el-col :span="8">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div class="kpi-label">平均解决周期(小时)</div>
            <div class="kpi-value">{{ avgCycle }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div class="kpi-label">解决率</div>
            <div class="kpi-value">{{ resolveRateText }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div class="kpi-label">问题总数</div>
            <div class="kpi-value">{{ total }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <el-card shadow="never" header="趋势">
            <TrendChart ref="trendRef" :data="trend" title="提交/解决趋势" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" header="分布">
            <DistributionChart ref="distRef" :data="distribution" />
          </el-card>
        </el-col>
      </el-row>

      <div class="export-row">
        <el-button :icon="Picture" @click="exportPng">导出 PNG</el-button>
        <el-button :icon="Download" :loading="excelLoading" @click="exportExcelFile"
          >导出 Excel</el-button
        >
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Download } from '@element-plus/icons-vue'
import DashboardFilters from '@/components/DashboardFilters.vue'
import TrendChart from '@/components/charts/TrendChart.vue'
import DistributionChart from '@/components/charts/DistributionChart.vue'
import { overview, exportExcel } from '@/api/dashboard'
import { downloadBlob } from '@/utils/exportUtil'

const versions = ref([])
const avgCycle = ref('-')
const resolveRate = ref(0)
const resolveRateText = ref('-')
const total = ref(0)
const trend = ref([])
const distribution = ref({ statusDistribution: [], severityRatio: [] })
const trendRef = ref(null)
const distRef = ref(null)
const excelLoading = ref(false)

async function load(query = {}) {
  try {
    const data = await overview(query)
    avgCycle.value =
      data && data.avgResolveCycle != null ? data.avgResolveCycle : '-'
    resolveRate.value = data && data.resolveRate != null ? data.resolveRate : 0
    const rr = Number(resolveRate.value) || 0
    resolveRateText.value = rr > 1 ? `${rr}%` : `${(rr * 100).toFixed(1)}%`
    trend.value = (data && (data.trendByDay || data.trend)) || []
    distribution.value = {
      statusDistribution: (data && data.statusDistribution) || [],
      severityRatio: (data && data.severityRatio) || []
    }
    total.value =
      (distribution.value.statusDistribution || []).reduce(
        (a, b) => a + (Number(b.count) || 0),
        0
      ) || 0
    if (data && data.versions) versions.value = data.versions
  } catch (e) {
    ElMessage.error('看板加载失败')
  }
}

function exportPng() {
  if (trendRef.value) trendRef.value.exportPng('dashboard-trend.png')
  if (distRef.value) distRef.value.exportPng('dashboard-distribution')
}
function exportExcelFile() {
  excelLoading.value = true
  exportExcel({})
    .then((blob) => downloadBlob(blob, 'dashboard.xlsx'))
    .catch(() => {})
    .finally(() => {
      excelLoading.value = false
    })
}

load()
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.kpi-label {
  font-size: 13px;
  color: var(--text-secondary);
}
.kpi-value {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
  color: var(--theme-color);
}
.export-row {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>

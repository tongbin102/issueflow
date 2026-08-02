<template>
  <div class="user-stats">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('dashboard.user.statsTitle') }}</span>
          <DashboardFilters :versions="versions" @search="load" />
        </div>
      </template>

      <el-row :gutter="16" class="kpi-row">
        <el-col :span="8">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div class="kpi-label">{{ t('dashboard.admin.avgCycle') }}</div>
            <div class="kpi-value">{{ avgCycle }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div class="kpi-label">{{ t('dashboard.admin.resolveRate') }}</div>
            <div class="kpi-value">{{ resolveRateText }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div class="kpi-label">{{ t('dashboard.card.total') }}</div>
            <div class="kpi-value">{{ total }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <el-card shadow="never" :header="t('dashboard.admin.trend')">
            <TrendChart ref="trendRef" :data="trend" :title="t('dashboard.admin.trendTitle')" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" :header="t('dashboard.admin.distribution')">
            <DistributionChart ref="distRef" :data="distribution" />
          </el-card>
        </el-col>
      </el-row>

      <div class="export-row">
        <el-button :icon="Picture" @click="exportPng">{{ t('dashboard.admin.exportPng') }}</el-button>
        <el-button :icon="Download" :loading="excelLoading" @click="exportExcelFile"
          >{{ t('dashboard.admin.exportExcel') }}</el-button
        >
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Download } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import DashboardFilters from '@/components/DashboardFilters.vue'
import TrendChart from '@/components/charts/TrendChart.vue'
import DistributionChart from '@/components/charts/DistributionChart.vue'
import { overview, exportExcel } from '@/api/dashboard'
import { downloadBlob } from '@/utils/exportUtil'

const { t } = useI18n()

const versions = ref([])
const avgCycle = ref('-')
const resolveRate = ref(0)
const total = ref(0)
const trend = ref([])
const distribution = ref({ statusDistribution: [], severityRatio: [] })
const trendRef = ref(null)
const distRef = ref(null)
const excelLoading = ref(false)

const resolveRateText = ref('-')

async function load(query = {}) {
  try {
    const data = await overview(query)
    avgCycle.value = data && data.avgResolveCycle != null ? data.avgResolveCycle : '-'
    resolveRate.value = data && data.resolveRate != null ? data.resolveRate : 0
    // resolveRate 可能为 0~1 或百分比
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
    const code = e && e.code
    // 401/403 由 request.js 统一跳转登录页 / 403 页，这里不再重复提示
    if (code === 401 || code === 403) return
    console.error('[UserStats] load failed:', e)
    // request.js 已就网络错误/业务错误弹过消息（shown=true）时不再重复弹，
    // 避免部署后后端未就绪时出现两条红色提示
    if (e && e.shown) return
    ElMessage.error((e && e.message) || t('dashboard.admin.loadFailed'))
  }
}

function exportPng() {
  if (trendRef.value) trendRef.value.exportPng('my-trend.png')
  if (distRef.value) distRef.value.exportPng('my-distribution')
}
function exportExcelFile() {
  excelLoading.value = true
  exportExcel({})
    .then((blob) => downloadBlob(blob, 'my-stats.xlsx'))
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

<template>
  <div class="user-dashboard">
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.status" :xs="12" :sm="8" :md="4">
        <el-card class="stat-card" shadow="hover" :body-style="{ padding: '16px' }">
          <div class="stat-label">
            <span class="dot" :style="{ background: statusColor(card.status) }"></span>
            {{ card.label }}
          </div>
          <div class="stat-value" :style="{ color: statusColor(card.status) }">
            {{ card.count }}
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card class="stat-card total" shadow="hover" :body-style="{ padding: '16px' }">
          <div class="stat-label">{{ t('dashboard.user.submittedTotal') }}</div>
          <div class="stat-value">{{ total }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="page-card" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-head">
          <span>{{ t('dashboard.user.myTrend') }}</span>
          <el-button text type="primary" @click="goList">{{ t('dashboard.user.viewMy') }}</el-button>
        </div>
      </template>
      <TrendChart :data="trend" :title="t('dashboard.user.submitTrend')" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { statusColor } from '@/utils/format'
import { useStatusOptions } from '@/utils/i18nEnum'
import { overview } from '@/api/dashboard'
import TrendChart from '@/components/charts/TrendChart.vue'

const { t } = useI18n()
const router = useRouter()
const statusOptions = useStatusOptions()

const countMap = ref({})
const cards = computed(() =>
  statusOptions.value.map((s) => ({
    status: s.value,
    label: s.label,
    count: countMap.value[s.value] || 0
  }))
)
const total = ref(0)
const trend = ref([])

function goList() {
  router.push('/user/my-issues')
}

async function load() {
  try {
    const data = await overview({})
    const dist = (data && data.statusDistribution) || []
    const map = {}
    dist.forEach((d) => {
      map[Number(d.status)] = Number(d.count) || 0
    })
    countMap.value = map
    total.value = Object.values(map).reduce((a, b) => a + b, 0)
    trend.value = (data && (data.trendByDay || data.trend)) || []
  } catch (e) {
    ElMessage.error(t('dashboard.admin.loadFailed'))
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card {
  margin-bottom: 12px;
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 8px;
}
.stat-card.total .stat-value {
  color: var(--theme-color);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

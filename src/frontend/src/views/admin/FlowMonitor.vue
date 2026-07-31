<template>
  <div class="flow-monitor">
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
    </el-row>

    <el-card class="page-card" shadow="never" style="margin-top: 16px">
      <template #header>
        <span>{{ t('flow.monitor.recent') }}</span>
      </template>
      <el-table v-loading="loading" :data="recent" border stripe>
        <el-table-column prop="issueNo" :label="t('issue.list.col.issueNo')" width="150" />
        <el-table-column prop="title" :label="t('issue.list.col.title')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('issue.list.col.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ statusLabelI18n(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" :label="t('issue.list.col.reporter')" width="110" />
        <el-table-column prop="assigneeName" :label="t('issue.list.col.assignee')" width="110" />
        <el-table-column :label="t('common.field.updatedAt')" width="170">
          <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.action.operation')" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)"
              >{{ t('common.action.detail') }}</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <IssueDetailDrawer
      v-model="drawerVisible"
      :issue-id="currentId"
      :flow-config="flowConfig"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { statusColor, statusTagType, formatDate } from '@/utils/format'
import { statusLabelI18n, useStatusOptions } from '@/utils/i18nEnum'
import { overview } from '@/api/dashboard'
import { pageIssues } from '@/api/issue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'

const { t } = useI18n()
const statusOptions = useStatusOptions()

const countMap = ref({})
const cards = computed(() =>
  statusOptions.value.map((s) => ({
    status: s.value,
    label: s.label,
    count: countMap.value[s.value] || 0
  }))
)
const recent = ref([])
const loading = ref(false)
const drawerVisible = ref(false)
const currentId = ref(null)
const flowConfig = ref({ rejectEnabled: true, reopenEnabled: true })

async function load() {
  try {
    const data = await overview({})
    const dist = (data && data.statusDistribution) || []
    const map = {}
    dist.forEach((d) => {
      map[Number(d.status)] = Number(d.count) || 0
    })
    countMap.value = map
  } catch (e) {
    countMap.value = {}
  }
  loading.value = true
  try {
    const res = await pageIssues({ page: 1, size: 15 })
    recent.value = (res && res.list) || []
  } catch (e) {
    recent.value = []
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  currentId.value = row.id
  drawerVisible.value = true
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
</style>

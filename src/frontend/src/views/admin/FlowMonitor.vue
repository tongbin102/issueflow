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
        <span>最近流转（按更新时间）</span>
      </template>
      <el-table v-loading="loading" :data="recent" border stripe>
        <el-table-column prop="issueNo" label="编号" width="150" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="提交人" width="110" />
        <el-table-column prop="assigneeName" label="处理人" width="110" />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)"
              >详情</el-button
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  STATUS_OPTIONS,
  statusColor,
  statusLabel,
  statusTagType,
  formatDate
} from '@/utils/format'
import { overview } from '@/api/dashboard'
import { pageIssues } from '@/api/issue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'

const cards = ref(STATUS_OPTIONS.map((s) => ({ status: s.value, label: s.label, count: 0 })))
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
    cards.value = STATUS_OPTIONS.map((s) => ({
      status: s.value,
      label: s.label,
      count: map[s.value] || 0
    }))
  } catch (e) {}
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

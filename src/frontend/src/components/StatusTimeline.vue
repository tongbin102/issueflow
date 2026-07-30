<template>
  <div class="status-timeline">
    <el-timeline v-if="history.length">
      <el-timeline-item
        v-for="(item, idx) in history"
        :key="item.id || idx"
        :timestamp="formatDate(item.createdAt)"
        placement="top"
        :color="timelineColor(item)"
      >
        <div class="tl-row">
          <span class="tl-action">{{ actionLabel(item.action) }}</span>
          <span class="tl-operator">{{ item.operatorName || '系统' }}</span>
        </div>
        <div v-if="showTransition(item)" class="tl-transition text-muted">
          {{ statusLabel(item.fromStatus) }} → {{ statusLabel(item.toStatus) }}
        </div>
        <div v-if="item.remark" class="tl-remark">备注：{{ item.remark }}</div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无操作记录" :image-size="48" />
  </div>
</template>

<script setup>
import { formatDate, actionLabel, statusLabel, statusColor } from '@/utils/format'

const props = defineProps({
  history: { type: Array, default: () => [] }
})

function showTransition(item) {
  return (
    item.fromStatus !== null &&
    item.fromStatus !== undefined &&
    item.toStatus !== null &&
    item.toStatus !== undefined &&
    item.fromStatus !== item.toStatus
  )
}

function timelineColor(item) {
  if (item.toStatus !== null && item.toStatus !== undefined) {
    return statusColor(item.toStatus)
  }
  return '#409EFF'
}
</script>

<style scoped>
.status-timeline {
  padding: 4px 0;
}
.tl-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tl-action {
  font-weight: 600;
  color: var(--text-primary);
}
.tl-operator {
  font-size: 12px;
  color: var(--text-secondary);
}
.tl-transition {
  font-size: 12px;
  margin-top: 2px;
}
.tl-remark {
  font-size: 12px;
  margin-top: 2px;
  color: var(--text-regular);
}
</style>

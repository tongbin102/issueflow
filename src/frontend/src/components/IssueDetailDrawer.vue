<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail ? `问题详情 ${detail.issueNo || ''}` : '问题详情'"
    size="560px"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">{{ detail.issueNo }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="严重等级">
            <el-tag :type="severityTagType(detail.severity)" effect="light">
              {{ severityLabel(detail.severity) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)" effect="light">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交人">{{ detail.reporterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.assigneeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ detail.tags || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">详细描述</el-divider>
        <div class="block-text">{{ detail.description || '无' }}</div>

        <el-divider content-position="left">环境信息</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="操作系统">{{ detail.envOs || '-' }}</el-descriptions-item>
          <el-descriptions-item label="浏览器">{{ detail.envBrowser || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应用版本">{{ detail.envAppVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备型号">{{ detail.envDevice || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">复现步骤</el-divider>
        <div class="block-text">{{ detail.reproduceSteps || '无' }}</div>

        <el-divider content-position="left">附件</el-divider>
        <AttachmentUploader
          :issue-id="detail.id"
          :attachments="attachments"
          @uploaded="onUploaded"
          @removed="onRemoved"
        />

        <IssueRelationPanel
          :issue-id="detail.id"
          :can-edit="canEditRelation"
          @updated="onFlowChanged"
        />

        <el-divider content-position="left">流转操作</el-divider>
        <StatusFlowButtons
          :status="detail.status"
          :issue-id="detail.id"
          :flow-config="flowConfig"
          @changed="onFlowChanged"
        />

        <el-divider content-position="left">操作历史</el-divider>
        <StatusTimeline :history="history" />
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  statusLabel,
  severityLabel,
  statusTagType,
  severityTagType,
  formatDate
} from '@/utils/format'
import { getIssue, getHistory } from '@/api/issue'
import { useUserStore } from '@/store/user'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import StatusFlowButtons from '@/components/StatusFlowButtons.vue'
import StatusTimeline from '@/components/StatusTimeline.vue'
import IssueRelationPanel from '@/components/IssueRelationPanel.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  issueId: { type: [Number, String], default: null },
  flowConfig: { type: Object, default: () => ({ rejectEnabled: true, reopenEnabled: true }) }
})
const emit = defineEmits(['update:modelValue', 'updated'])

const loading = ref(false)
const detail = ref(null)
const history = ref([])
const attachments = ref([])

const userStore = useUserStore()
/** 关联编辑权限：ADMIN 或提交人本人 */
const canEditRelation = computed(() => {
  if (userStore.isAdmin) return true
  const info = userStore.userInfo || {}
  return !!(detail.value && detail.value.reporterId != null && detail.value.reporterId === info.id)
})

async function loadDetail() {
  if (!props.issueId) return
  loading.value = true
  try {
    const res = await getIssue(props.issueId)
    detail.value = res || null
    attachments.value = (res && res.attachments) || []
    const his = await getHistory(props.issueId, { page: 1, size: 50 })
    history.value = (his && his.list) || []
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

function onOpen() {
  loadDetail()
}

function onUploaded(att) {
  attachments.value = [...attachments.value, att]
}
function onRemoved(id) {
  attachments.value = attachments.value.filter((a) => a.id !== id)
}

function onFlowChanged() {
  loadDetail()
  emit('updated')
}

watch(
  () => props.issueId,
  (val) => {
    if (val && props.modelValue) loadDetail()
  }
)
</script>

<style scoped>
.detail-body {
  padding-bottom: 24px;
}
.block-text {
  white-space: pre-wrap;
  line-height: 1.6;
  color: var(--text-regular);
  font-size: 13px;
}
</style>

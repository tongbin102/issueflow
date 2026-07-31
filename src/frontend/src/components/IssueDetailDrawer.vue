<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail ? `${t('issue.detail.title')} ${detail.issueNo || ''}` : t('issue.detail.title')"
    size="560px"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="t('issue.list.col.issueNo')">{{ detail.issueNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.title')">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.type')">{{ detail.typeName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.severity')">
            <el-tag :type="severityTagType(detail.severity)" effect="light">
              {{ severityLabelI18n(detail.severity) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.status')">
            <el-tag :type="statusTagType(detail.status)" effect="light">
              {{ statusLabelI18n(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.reporter')">{{ detail.reporterName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.assignee')">{{ detail.assigneeName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.list.col.tags')">{{ detail.tags || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.field.createdAt')">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">{{ t('issue.section.detail') }}</el-divider>
        <div class="block-text">{{ detail.description || t('issue.detail.none') }}</div>

        <el-divider content-position="left">{{ t('issue.form.section.env') }}</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="t('issue.form.envOs')">{{ detail.envOs || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.form.envBrowser')">{{ detail.envBrowser || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.form.envAppVersion')">{{ detail.envAppVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('issue.form.envDevice')">{{ detail.envDevice || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">{{ t('issue.form.steps') }}</el-divider>
        <div class="block-text">{{ detail.reproduceSteps || t('issue.detail.none') }}</div>

        <el-divider content-position="left">{{ t('issue.detail.section.attachment') }}</el-divider>
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

        <el-divider content-position="left">{{ t('issue.detail.section.action') }}</el-divider>
        <StatusFlowButtons
          :status="detail.status"
          :issue-id="detail.id"
          :flow-config="flowConfig"
          @changed="onFlowChanged"
        />

        <el-divider content-position="left">{{ t('issue.detail.section.history') }}</el-divider>
        <StatusTimeline :history="history" />
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { statusTagType, severityTagType, formatDate } from '@/utils/format'
import { statusLabelI18n, severityLabelI18n } from '@/utils/i18nEnum'
import { getIssue, getHistory } from '@/api/issue'
import { useUserStore } from '@/store/user'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import StatusFlowButtons from '@/components/StatusFlowButtons.vue'
import StatusTimeline from '@/components/StatusTimeline.vue'
import IssueRelationPanel from '@/components/IssueRelationPanel.vue'

const { t } = useI18n()

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

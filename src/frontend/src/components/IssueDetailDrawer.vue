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
        <!-- 流转操作常驻在标签页之上：任意标签下都能直接执行流转，不被标签切换挡住 -->
        <div class="flow-bar">
          <div class="flow-bar-title">{{ t('issue.detail.section.action') }}</div>
          <StatusFlowButtons
            :status="detail.status"
            :issue-id="detail.id"
            :flow-config="flowConfig"
            @changed="onFlowChanged"
          />
        </div>

        <!-- Phase8 W2 #12：查看态与提交/编辑态共用同一套左侧竖形标签页容器 -->
        <IssueFormSections>
          <!-- ===== 标签 1：基本信息 ===== -->
          <template #basic>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item :label="t('issue.list.col.issueNo')">{{ detail.issueNo }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.title')">{{ detail.title }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.type')">{{ detail.typeName || '-' }}</el-descriptions-item>
              <!-- Phase7 T3：来源（字典项名，i18n 优先，回退后端 sourceDesc） -->
              <el-descriptions-item :label="t('issue.list.col.source')">{{ sourceText }}</el-descriptions-item>
              <!-- Phase7 T3：优先级（与严重等级同款 tag 渲染） -->
              <el-descriptions-item :label="t('issue.list.col.priority')">
                <el-tag
                  v-if="detail.priority !== null && detail.priority !== undefined"
                  :type="priorityTagType(detail.priority)"
                  effect="light"
                >
                  {{ priorityLabelI18n(detail.priority) }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
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
          </template>

          <!-- ===== 标签 2：问题描述（描述 + 复现步骤 + 环境信息）===== -->
          <template #detail>
            <el-divider content-position="left">{{ t('issue.form.description') }}</el-divider>
            <div class="block-text">{{ detail.description || t('issue.detail.none') }}</div>

            <el-divider content-position="left">{{ t('issue.form.steps') }}</el-divider>
            <div class="block-text">{{ detail.reproduceSteps || t('issue.detail.none') }}</div>

            <el-divider content-position="left">{{ t('issue.form.section.env') }}</el-divider>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item :label="t('issue.form.envOs')">{{ detail.envOs || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.form.envBrowser')">{{ detail.envBrowser || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.form.envAppVersion')">{{ detail.envAppVersion || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.form.envDevice')">{{ detail.envDevice || '-' }}</el-descriptions-item>
            </el-descriptions>
          </template>

          <!-- ===== 标签 3：附件上传 ===== -->
          <template #attachment>
            <AttachmentUploader
              :issue-id="detail.id"
              :attachments="attachments"
              @uploaded="onUploaded"
              @removed="onRemoved"
            />
          </template>

          <!-- ===== 标签 4：关联信息 ===== -->
          <template #relation>
            <IssueRelationPanel
              :issue-id="detail.id"
              :can-edit="canEditRelation"
              @updated="onFlowChanged"
            />
          </template>

          <!-- ===== 标签 5：操作历史 ===== -->
          <template #history>
            <StatusTimeline :history="history" />
          </template>
        </IssueFormSections>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { statusTagType, severityTagType, priorityTagType, formatDate } from '@/utils/format'
import {
  statusLabelI18n,
  severityLabelI18n,
  priorityLabelI18n,
  dictCodeLabelI18n,
  DICT_TYPE
} from '@/utils/i18nEnum'
import { getIssue, getHistory } from '@/api/issue'
import { useUserStore } from '@/store/user'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import StatusFlowButtons from '@/components/StatusFlowButtons.vue'
import StatusTimeline from '@/components/StatusTimeline.vue'
import IssueRelationPanel from '@/components/IssueRelationPanel.vue'
import IssueFormSections from '@/components/IssueFormSections.vue'

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

/**
 * 来源展示文案：i18n（dict.value.ISSUE_SOURCE.{code}）优先，
 * 回退后端 IssueDetailVO.sourceDesc，再回退 '-'。
 */
const sourceText = computed(() => {
  const d = detail.value
  if (!d) return '-'
  if (!d.source) return d.sourceDesc || '-'
  return dictCodeLabelI18n(DICT_TYPE.ISSUE_SOURCE, d.source, d.sourceDesc) || '-'
})

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
/* 常驻流转操作条：与下方标签页做视觉分隔 */
.flow-bar {
  padding: 0 0 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.flow-bar-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-regular);
  margin-bottom: 8px;
}
.block-text {
  white-space: pre-wrap;
  line-height: 1.6;
  color: var(--text-regular);
  font-size: 13px;
}
</style>

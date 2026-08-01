<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail ? `${t('issue.detail.title')} ${detail.issueNo || ''}` : t('issue.detail.title')"
    :size="drawerSize"
    :class="{ 'if-detail-drawer--mobile': appStore.isMobile }"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div class="detail-body">
      <!-- Phase9 T13：加载态改用骨架屏，避免暗色主题下白色遮罩突兀 -->
      <IfLoading :loading="loading" :rows="6" min-height="240px">
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
              <!-- Phase9 T13：语义标签统一走 IfTag（色值固定，四主题一致） -->
              <el-descriptions-item :label="t('issue.list.col.priority')">
                <IfTag
                  v-if="detail.priority !== null && detail.priority !== undefined"
                  :semantic="prioritySemantic(detail.priority)"
                  :label="priorityLabelI18n(detail.priority)"
                />
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.severity')">
                <IfTag
                  :semantic="severitySemantic(detail.severity)"
                  :label="severityLabelI18n(detail.severity)"
                />
              </el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.status')">
                <IfTag
                  :semantic="statusSemantic(detail.status)"
                  :label="statusLabelI18n(detail.status)"
                  dot
                />
              </el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.reporter')">{{ detail.reporterName || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.assignee')">{{ detail.assigneeName || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('issue.list.col.tags')">{{ detail.tags || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('common.field.createdAt')">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            </el-descriptions>
          </template>

          <!-- ===== 标签 2：问题描述（描述 + 复现步骤 + 环境信息）===== -->
          <template #detail>
            <h4 class="if-section-title detail-sub">{{ t('issue.form.description') }}</h4>
            <div class="block-text">{{ detail.description || t('issue.detail.none') }}</div>

            <h4 class="if-section-title detail-sub">{{ t('issue.form.steps') }}</h4>
            <div class="block-text">{{ detail.reproduceSteps || t('issue.detail.none') }}</div>

            <h4 class="if-section-title detail-sub">{{ t('issue.detail.section.env') }}</h4>
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
      </IfLoading>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { statusSemantic, severitySemantic, prioritySemantic, formatDate } from '@/utils/format'
import {
  statusLabelI18n,
  severityLabelI18n,
  priorityLabelI18n,
  dictCodeLabelI18n,
  DICT_TYPE
} from '@/utils/i18nEnum'
import { getIssue, getHistory } from '@/api/issue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import IfTag from '@/components/base/IfTag.vue'
import IfLoading from '@/components/base/IfLoading.vue'
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

const appStore = useAppStore()
/**
 * 抽屉宽度：桌面 / 平板保持 560px（ARCH 约定），移动端（<768px）全屏，
 * 避免 560px 固定宽在小屏被截断。
 */
const drawerSize = computed(() => (appStore.isMobile ? '100%' : '560px'))

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
  padding-bottom: var(--if-space-lg);
}
/* 常驻流转操作条：与下方标签页做视觉分隔（Phase9 T13 令牌化 + 轻量底色强化「常驻可操作」） */
.flow-bar {
  padding: var(--if-space-sm) var(--if-space-sm) var(--if-space-sm);
  margin-bottom: var(--if-space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--if-radius-sm);
  background: var(--if-hover-bg);
}
.flow-bar-title {
  font-size: var(--if-font-sm);
  font-weight: var(--if-weight-bold);
  color: var(--text-regular);
  margin-bottom: var(--if-space-sm);
}
.block-text {
  white-space: pre-wrap;
  line-height: var(--if-line-base);
  color: var(--text-regular);
  font-size: var(--if-font-sm);
}
/* 详情内分节标题：替换 el-divider，视觉与页面分区标题一致 */
.detail-sub {
  margin: var(--if-space-md) 0 var(--if-space-sm);
  font-size: var(--if-font-sm);
}
.detail-sub:first-child {
  margin-top: 0;
}
</style>

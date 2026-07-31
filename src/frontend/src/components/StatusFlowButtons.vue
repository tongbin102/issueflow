<template>
  <div class="status-flow-buttons">
    <el-button
      v-for="btn in buttons"
      :key="btn.key"
      :type="btn.type"
      :plain="btn.plain"
      @click="onClick(btn)"
    >
      {{ btn.label }}
    </el-button>
    <el-empty
      v-if="!buttons.length"
      :description="t('issue.flowBtn.noAction')"
      :image-size="40"
    />

    <!-- 备注抽屉（T7：el-dialog → 统一 FormDrawer） -->
    <FormDrawer
      v-model="remarkVisible"
      :title="`${activeBtn ? activeBtn.label : ''} - ${t('issue.action.remark')}`"
      size="sm"
      :loading="submitting"
      :confirm-text="t('common.action.confirm')"
      @confirm="confirm"
      @closed="remark = ''"
    >
      <el-input
        v-model="remark"
        type="textarea"
        :rows="4"
        :placeholder="
          remarkRequired ? t('issue.flowBtn.remarkRequiredPh') : t('issue.flowBtn.remarkOptional')
        "
      />
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { changeStatus, reopenIssue } from '@/api/issue'
import FormDrawer from '@/components/FormDrawer.vue'

const props = defineProps({
  // 当前状态数值
  status: { type: [Number, String], default: 0 },
  issueId: { type: [Number, String], default: null },
  // 流程开关 {rejectEnabled, reopenEnabled}
  flowConfig: { type: Object, default: () => ({ rejectEnabled: true, reopenEnabled: true }) }
})
const emit = defineEmits(['changed'])

const { t } = useI18n()
const userStore = useUserStore()
const remarkVisible = ref(false)
const activeBtn = ref(null)
const remark = ref('')
const submitting = ref(false)

const roles = computed(() => userStore.roles || [])
const isDev = computed(() => roles.value.includes('DEVELOPER'))
const isTester = computed(() => roles.value.includes('TESTER'))
const isAdmin = computed(() => roles.value.includes('ADMIN'))

const remarkRequired = computed(() => !!(activeBtn.value && activeBtn.value.requireRemark))

// 依据 当前状态 + 角色 + 流程开关 计算可用按钮（label 走 i18n，语言切换响应式更新）
const buttons = computed(() => {
  const s = Number(props.status)
  const list = []
  if (s === 0) {
    // OPEN → IN_PROGRESS（D/A）
    if (isDev.value || isAdmin.value) {
      list.push({
        key: 'claim',
        label: t('issue.flowBtn.claim'),
        type: 'primary',
        toStatus: 1,
        requireRemark: false,
        openId: false
      })
    }
  } else if (s === 1) {
    // IN_PROGRESS → PENDING_VERIFY（D/A）
    if (isDev.value || isAdmin.value) {
      list.push({
        key: 'submitFix',
        label: t('issue.flowBtn.submitFix'),
        type: 'primary',
        toStatus: 2,
        requireRemark: false,
        openId: false
      })
    }
  } else if (s === 2) {
    // PENDING_VERIFY → VERIFIED（T/A）
    if (isTester.value || isAdmin.value) {
      list.push({
        key: 'verifyPass',
        label: t('issue.flowBtn.verifyPass'),
        type: 'success',
        toStatus: 3,
        requireRemark: false,
        openId: false
      })
    }
    // PENDING_VERIFY → IN_PROGRESS 回退（T/A 且 rejectEnabled）
    if ((isTester.value || isAdmin.value) && props.flowConfig.rejectEnabled) {
      list.push({
        key: 'reject',
        label: t('issue.flowBtn.reject'),
        type: 'warning',
        plain: true,
        toStatus: 1,
        requireRemark: true,
        openId: false
      })
    }
  } else if (s === 3) {
    // VERIFIED → CLOSED（T/A）
    if (isTester.value || isAdmin.value) {
      list.push({
        key: 'close',
        label: t('issue.flowBtn.close'),
        type: 'info',
        toStatus: 4,
        requireRemark: true,
        openId: false
      })
    }
  } else if (s === 4) {
    // CLOSED → OPEN 重开（A 且 reopenEnabled）
    if (isAdmin.value && props.flowConfig.reopenEnabled) {
      list.push({
        key: 'reopen',
        label: t('issue.flowBtn.reopen'),
        type: 'warning',
        toStatus: 0,
        requireRemark: false,
        openId: true
      })
    }
  }
  return list
})

function onClick(btn) {
  activeBtn.value = btn
  remark.value = ''
  if (btn.requireRemark) {
    remarkVisible.value = true
  } else {
    confirm()
  }
}

function confirm() {
  const btn = activeBtn.value
  if (!btn) return
  if (btn.requireRemark && !remark.value.trim()) {
    ElMessage.warning(t('issue.flowBtn.remarkWarn'))
    return
  }
  submitting.value = true
  const run = btn.openId
    ? reopenIssue(props.issueId, remark.value)
    : changeStatus(props.issueId, { toStatus: btn.toStatus, remark: remark.value })
  run
    .then(() => {
      ElMessage.success(t('issue.flowBtn.success'))
      remarkVisible.value = false
      emit('changed')
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false
    })
}
</script>

<style scoped>
.status-flow-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>

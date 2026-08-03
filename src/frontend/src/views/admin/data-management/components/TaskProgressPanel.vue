<template>
  <!--
    备份 / 恢复任务进度弹窗（Phase10 需求三）。

    交互约束：
      · 任务未进入终态时禁止关闭（无关闭按钮、点遮罩与 ESC 均无效），
        避免用户在恢复途中关掉弹窗后误以为任务已取消；
      · 恢复任务额外提示「系统处于只读」，让其他成员的写操作失败有据可循；
      · 轮询断线（lost）只提示「可能仍在后台运行」，绝不谎报失败。
  -->
  <el-dialog
    v-model="visible"
    :title="title"
    width="520px"
    :close-on-click-modal="finished"
    :close-on-press-escape="finished"
    :show-close="finished"
    :destroy-on-close="false"
    class="dm-progress-dialog"
  >
    <div class="dm-progress">
      <el-progress
        :percentage="safePercent"
        :status="barStatus"
        :stroke-width="14"
        :text-inside="true"
      />

      <div class="dm-progress__row">
        <span class="dm-progress__label">{{ t('dataManagement.progress.phaseLabel') }}</span>
        <span class="dm-progress__value">{{ phaseText }}</span>
      </div>

      <div v-if="message" class="dm-progress__msg">{{ message }}</div>

      <el-alert
        v-if="isRestore && !finished"
        type="warning"
        :closable="false"
        show-icon
        :title="t('dataManagement.restore.readonlyNotice')"
        class="dm-progress__alert"
      />

      <el-alert
        v-if="!finished"
        type="info"
        :closable="false"
        show-icon
        :title="t('dataManagement.progress.keepOpen')"
        class="dm-progress__alert"
      />

      <el-alert
        v-if="lost"
        type="warning"
        :closable="false"
        show-icon
        :title="t('dataManagement.progress.lost')"
        :description="t('dataManagement.progress.lostTip')"
        class="dm-progress__alert"
      />

      <el-alert
        v-if="succeeded"
        type="success"
        :closable="false"
        show-icon
        :title="successTitle"
        class="dm-progress__alert"
      />

      <el-alert
        v-if="failed"
        type="error"
        :closable="false"
        show-icon
        :title="failedTitle"
        :description="errorMsg || ''"
        class="dm-progress__alert"
      />
    </div>

    <template #footer>
      <el-button type="primary" :disabled="!finished" @click="handleClose">
        {{ t('dataManagement.action.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  /** 弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 任务类型：BACKUP / RESTORE */
  taskType: { type: String, default: 'BACKUP' },
  /** 进度百分比 0-100 */
  percent: { type: Number, default: 0 },
  /** 当前阶段码（TaskPhaseEnum） */
  phase: { type: String, default: '' },
  /** 后端下发的阶段描述 */
  message: { type: String, default: '' },
  /** 失败原因 */
  errorMsg: { type: String, default: '' },
  /** 是否终态 */
  finished: { type: Boolean, default: false },
  /** 是否成功 */
  succeeded: { type: Boolean, default: false },
  /** 是否失败 / 取消 */
  failed: { type: Boolean, default: false },
  /** 轮询是否已判定断线 */
  lost: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'close'])

const { t, te } = useI18n()

/** 双向绑定的显隐 */
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/** 是否恢复任务 */
const isRestore = computed(() => props.taskType === 'RESTORE')

/** 百分比兜底到 0-100，避免后端异常值把进度条撑坏 */
const safePercent = computed(() => {
  const value = Number(props.percent)
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, Math.round(value)))
})

/** 弹窗标题 */
const title = computed(() =>
  isRestore.value
    ? t('dataManagement.progress.titleRestore')
    : t('dataManagement.progress.titleBackup')
)

/** 进度条状态 */
const barStatus = computed(() => {
  if (props.failed) return 'exception'
  if (props.succeeded) return 'success'
  return ''
})

/**
 * 阶段文案：后端阶段码可能随版本新增，
 * te() 判存后再取，未收录的阶段码退回「未知」而不是把 key 直接显示给用户。
 */
const phaseText = computed(() => {
  const code = props.phase || ''
  if (!code) return t('dataManagement.unknown')
  const key = `dataManagement.phase.${code}`
  return te(key) ? t(key) : t('dataManagement.unknown')
})

/** 成功提示语 */
const successTitle = computed(() =>
  isRestore.value
    ? t('dataManagement.progress.successRestore')
    : t('dataManagement.progress.successBackup')
)

/** 失败提示语 */
const failedTitle = computed(() =>
  isRestore.value
    ? t('dataManagement.progress.failedRestore')
    : t('dataManagement.progress.failedBackup')
)

/** 关闭弹窗（仅终态可用） */
function handleClose() {
  if (!props.finished) return
  visible.value = false
  emit('close')
}
</script>

<style scoped>
.dm-progress {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dm-progress__row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.dm-progress__label {
  color: var(--el-text-color-secondary);
}

.dm-progress__value {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.dm-progress__msg {
  font-size: 13px;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.dm-progress__alert {
  margin: 0;
}
</style>

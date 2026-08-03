<template>
  <!--
    恢复确认对话框（Phase10 需求三）—— 全系统风险最高的一个弹窗。

    三道防线，缺一不可：
      1. 红色危险横幅讲清后果（整库覆盖、不可撤销、期间只读）；
      2. 恢复前安全备份强制开启（后端 resolvePreBackup 同样强制，
         这里禁用开关而不是假装可关，免得用户以为关掉就能更快）；
      3. 手动输入 RESTORE 才放行提交，杜绝「手滑点确定」。
  -->
  <el-dialog
    v-model="visible"
    :title="t('dataManagement.restore.title')"
    width="560px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-alert
      type="error"
      :closable="false"
      show-icon
      :title="t('dataManagement.restore.danger')"
      :description="t('dataManagement.restore.dangerDetail')"
      class="dm-restore__alert"
    />

    <el-descriptions
      v-if="backup"
      :column="1"
      border
      size="small"
      class="dm-restore__desc"
    >
      <el-descriptions-item :label="t('dataManagement.restore.backupInfo')">
        {{ backup.name || backup.fileName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.type')">
        {{ typeText }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.size')">
        {{ sizeText }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.createTime')">
        {{ backup.createTime || '-' }}
      </el-descriptions-item>
    </el-descriptions>

    <el-form label-width="130px" class="dm-restore__form" @submit.prevent>
      <el-form-item :label="t('dataManagement.restore.preBackup')">
        <!-- 强制开启：后端同样强制，这里禁用以如实反映系统行为 -->
        <el-switch :model-value="true" disabled />
        <div class="dm-form-tip">{{ t('dataManagement.restore.preBackupTip') }}</div>
        <div class="dm-form-tip dm-form-tip--strong">
          {{ t('dataManagement.restore.preBackupForced') }}
        </div>
      </el-form-item>

      <el-form-item :label="t('dataManagement.restore.confirmLabel')" required>
        <el-input
          v-model="confirmText"
          clearable
          :placeholder="t('dataManagement.restore.confirmPlaceholder')"
        />
        <div class="dm-form-tip">{{ t('dataManagement.restore.confirmTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('dataManagement.restore.remark')">
        <el-input
          v-model="remark"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          :placeholder="t('dataManagement.restore.remarkPlaceholder')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ t('dataManagement.action.cancel') }}</el-button>
      <el-button
        type="danger"
        :loading="submitting"
        :disabled="!confirmMatched"
        @click="handleSubmit"
      >
        {{ t('dataManagement.action.restore') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatSize } from '@/views/admin/data-management/format'

const props = defineProps({
  /** 弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 待恢复的备份记录（BackupListVO / BackupDetailVO） */
  backup: { type: Object, default: null },
  /** 提交中 */
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const { t, te } = useI18n()

/** 用户输入的确认文本 */
const confirmText = ref('')
/** 恢复备注 */
const remark = ref('')

/** 双向绑定的显隐 */
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/** 备份类型文案（未知码回退到「未知」） */
const typeText = computed(() => {
  const code = (props.backup && props.backup.backupType) || ''
  const key = `dataManagement.type.${code}`
  return code && te(key) ? t(key) : t('dataManagement.unknown')
})

/** 备份体积文案 */
const sizeText = computed(() => formatSize(props.backup && props.backup.size))

/** 确认文本是否匹配（大小写敏感，避免「restore」也放行） */
const confirmMatched = computed(
  () => confirmText.value.trim() === t('dataManagement.restore.confirmWord')
)

/** 关闭后清空，避免下次打开还残留上次输入的 RESTORE */
function handleClosed() {
  confirmText.value = ''
  remark.value = ''
}

/** 提交恢复请求；preBackup 恒为 true */
function handleSubmit() {
  if (!confirmMatched.value) return
  emit('submit', {
    preBackup: true,
    remark: remark.value.trim()
  })
}
</script>

<style scoped>
.dm-restore__alert {
  margin-bottom: 12px;
}

.dm-restore__desc {
  margin-bottom: 12px;
}

.dm-restore__form {
  margin-top: 4px;
}

.dm-form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.dm-form-tip--strong {
  color: var(--el-color-warning);
}
</style>

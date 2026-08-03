<template>
  <!--
    上传备份包恢复对话框（Phase10 需求三）。

    前端做「格式 + 体积」两道前置校验，不是为了替代后端校验
    （后端 uploadAndRestore 仍会完整校验 zip 结构与校验和），
    而是为了让用户在传完 500MB 之后才被告知「格式不对」这种事不再发生。

    「上传后立即恢复」默认开启但可关闭：关闭时仅登记入库，
    用户可以先上传、稍后在列表里挑时间恢复。
  -->
  <el-dialog
    v-model="visible"
    :title="t('dataManagement.upload.title')"
    width="560px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-form label-width="130px" @submit.prevent>
      <el-form-item :label="t('dataManagement.upload.file')" required>
        <el-upload
          ref="uploadRef"
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".zip"
          :show-file-list="true"
          :file-list="fileList"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :on-exceed="handleExceed"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">{{ t('dataManagement.upload.dropTip') }}</div>
          <template #tip>
            <div class="dm-form-tip">{{ t('dataManagement.upload.formatLimit') }}</div>
            <div class="dm-form-tip">
              {{ t('dataManagement.upload.sizeLimit', { size: sizeLimitMB }) }}
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <el-form-item :label="t('dataManagement.upload.name')">
        <el-input
          v-model="form.name"
          maxlength="64"
          show-word-limit
          clearable
          :placeholder="t('dataManagement.upload.namePlaceholder')"
        />
      </el-form-item>

      <el-form-item :label="t('dataManagement.upload.restoreNow')">
        <el-switch v-model="form.restoreNow" />
        <div class="dm-form-tip">{{ t('dataManagement.upload.restoreNowTip') }}</div>
      </el-form-item>

      <!-- 立即恢复 = 整库覆盖，必须复用与恢复弹窗同等强度的警告与确认 -->
      <template v-if="form.restoreNow">
        <el-alert
          type="error"
          :closable="false"
          show-icon
          :title="t('dataManagement.restore.danger')"
          :description="t('dataManagement.restore.dangerDetail')"
          class="dm-upload__alert"
        />

        <el-form-item :label="t('dataManagement.restore.preBackup')">
          <el-switch :model-value="true" disabled />
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
      </template>

      <el-form-item :label="t('dataManagement.restore.remark')">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          :placeholder="t('dataManagement.restore.remarkPlaceholder')"
        />
      </el-form-item>

      <!-- 上传进度：文件传输阶段的百分比，与后端任务进度是两回事，分开展示 -->
      <el-form-item v-if="uploading" :label="t('dataManagement.column.status')">
        <div class="dm-upload__progress">
          <el-progress :percentage="uploadPercent" :stroke-width="12" text-inside />
          <div class="dm-form-tip">
            {{
              uploadPercent >= 99
                ? t('dataManagement.upload.validating')
                : t('dataManagement.upload.uploading', { percent: uploadPercent })
            }}
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="uploading" @click="visible = false">
        {{ t('dataManagement.action.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="!submitEnabled"
        @click="handleSubmit"
      >
        {{ t('dataManagement.action.upload') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** 弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 上传体积上限（MB），来自后端保留策略配置 */
  sizeLimitMB: { type: Number, default: 512 },
  /** 上传中 */
  uploading: { type: Boolean, default: false },
  /** 上传进度 0-100 */
  uploadPercent: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const { t } = useI18n()

const uploadRef = ref(null)
/** el-upload 的受控文件列表 */
const fileList = ref([])
/** 真正要提交的原生 File 对象 */
const selectedFile = ref(null)
/** 立即恢复时的确认文本 */
const confirmText = ref('')

const form = ref({
  name: '',
  restoreNow: true,
  remark: ''
})

/** 双向绑定的显隐 */
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/** 立即恢复时必须输对确认文本；仅登记则只要选了文件即可 */
const submitEnabled = computed(() => {
  if (!selectedFile.value) return false
  if (!form.value.restoreNow) return true
  return confirmText.value.trim() === t('dataManagement.restore.confirmWord')
})

/**
 * 选择文件：就地校验扩展名与体积，不合格直接清空选择。
 *
 * @param {Object} file el-upload 的文件包装对象
 */
function handleFileChange(file) {
  const raw = file && file.raw
  if (!raw) return

  const isZip = /\.zip$/i.test(raw.name || '')
  if (!isZip) {
    ElMessage.error(t('dataManagement.msg.fileTypeInvalid'))
    clearFile()
    return
  }

  const limitBytes = Number(props.sizeLimitMB) * 1024 * 1024
  if (Number.isFinite(limitBytes) && limitBytes > 0 && raw.size > limitBytes) {
    ElMessage.error(t('dataManagement.msg.fileTooLarge', { size: props.sizeLimitMB }))
    clearFile()
    return
  }

  selectedFile.value = raw
  fileList.value = [file]
  // 未命名时用文件名兜底，省得列表里一排「未命名备份」
  if (!form.value.name) {
    form.value.name = (raw.name || '').replace(/\.zip$/i, '').slice(0, 64)
  }
}

/** 移除文件 */
function handleFileRemove() {
  clearFile()
}

/** 超出 1 个文件限制时的提示（覆盖式选择更符合直觉，这里直接替换） */
function handleExceed(files) {
  const next = files && files[0]
  if (!next) return
  handleFileChange({ raw: next, name: next.name, uid: Date.now() })
}

/** 清空已选文件 */
function clearFile() {
  selectedFile.value = null
  fileList.value = []
  if (uploadRef.value && typeof uploadRef.value.clearFiles === 'function') {
    uploadRef.value.clearFiles()
  }
}

/** 关闭后彻底重置 */
function handleClosed() {
  clearFile()
  confirmText.value = ''
  form.value = { name: '', restoreNow: true, remark: '' }
}

/** 提交上传 */
function handleSubmit() {
  if (!selectedFile.value) {
    ElMessage.warning(t('dataManagement.msg.selectFile'))
    return
  }
  if (!submitEnabled.value) {
    ElMessage.warning(t('dataManagement.msg.confirmMismatch'))
    return
  }

  emit('submit', {
    file: selectedFile.value,
    meta: {
      name: form.value.name.trim(),
      preBackup: true,
      restoreNow: form.value.restoreNow,
      remark: form.value.remark.trim()
    }
  })
}
</script>

<style scoped>
.dm-form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.dm-form-tip--strong {
  color: var(--el-color-warning);
}

.dm-upload__alert {
  margin-bottom: 12px;
}

.dm-upload__progress {
  width: 100%;
}
</style>

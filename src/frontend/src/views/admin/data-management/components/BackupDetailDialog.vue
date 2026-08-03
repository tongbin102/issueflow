<template>
  <!--
    备份详情对话框（Phase10 需求三）。

    只读展示 BackupDetailVO，重点是校验和与失败原因：
    前者用于人工核对备份包是否被篡改 / 传输损坏，
    后者让失败的备份不至于只留下一个「失败」二字。
  -->
  <el-dialog
    v-model="visible"
    :title="t('dataManagement.detail.title')"
    width="600px"
  >
    <el-skeleton v-if="loading" :rows="6" animated />

    <el-descriptions v-else-if="detail" :column="1" border size="small">
      <el-descriptions-item :label="t('dataManagement.column.name')">
        {{ detail.name || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.fileName')">
        {{ detail.fileName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.type')">
        {{ enumText('type', detail.backupType) }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.source')">
        {{ enumText('source', detail.source) }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.status')">
        {{ enumText('status', detail.status) }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.size')">
        {{ formatSize(detail.size) }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.operator')">
        {{ detail.operatorName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.detail.dbName')">
        {{ detail.dbName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.detail.appVersion')">
        {{ detail.appVersion || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.detail.tableCount')">
        {{ detail.tableCount != null ? detail.tableCount : '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.detail.startedAt')">
        {{ detail.startedAt || detail.createTime || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.detail.finishedAt')">
        {{ detail.finishedAt || '-' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.column.duration')">
        {{ formatDuration(detail.durationMs) }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('dataManagement.detail.checksum')">
        <span class="dm-detail__checksum">{{ detail.checksum || '-' }}</span>
      </el-descriptions-item>
      <el-descriptions-item
        v-if="detail.errorMsg"
        :label="t('dataManagement.detail.errorMsg')"
      >
        <span class="dm-detail__error">{{ detail.errorMsg }}</span>
      </el-descriptions-item>
    </el-descriptions>

    <el-empty v-else :description="t('dataManagement.empty')" />

    <template #footer>
      <el-button type="primary" @click="visible = false">
        {{ t('dataManagement.action.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatSize, formatDuration } from '@/views/admin/data-management/format'

const props = defineProps({
  /** 弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 详情数据（BackupDetailVO） */
  detail: { type: Object, default: null },
  /** 加载中 */
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const { t, te } = useI18n()

/** 双向绑定的显隐 */
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/**
 * 枚举码转文案；未收录的码回退到「未知」而不是直接暴露 key。
 *
 * @param {string} group 枚举分组：type / source / status
 * @param {string} code 枚举码
 * @returns {string} 文案
 */
function enumText(group, code) {
  if (!code) return t('dataManagement.unknown')
  const key = `dataManagement.${group}.${code}`
  return te(key) ? t(key) : t('dataManagement.unknown')
}
</script>

<style scoped>
.dm-detail__checksum {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  word-break: break-all;
}

.dm-detail__error {
  color: var(--el-color-danger);
  word-break: break-all;
}
</style>

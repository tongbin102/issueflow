<template>
  <!-- Phase7 T8：备份确认抽屉（FormDrawer sm）
       范围/格式单选 + 实时预估 + blob 下载触发 + 面板内错误条。
       响应式：FormDrawer 在 ≤768px 自动满宽；明细表格外层横向可滚动。 -->
  <FormDrawer
    :model-value="modelValue"
    :title="t('backup.drawer.title')"
    size="sm"
    :loading="exporting"
    :confirm-text="exporting ? t('backup.action.exporting') : t('backup.action.confirm')"
    @update:model-value="onVisibleChange"
    @confirm="doExport"
    @closed="onClosed"
  >
    <div v-loading="estimating" class="backup-drawer">
      <!-- 失败原因：面板内红色错误条（读后端 message），可重试 -->
      <el-alert
        v-if="errorMessage"
        class="backup-drawer__alert"
        type="error"
        show-icon
        :title="t('backup.msg.failed')"
        :description="errorMessage"
        @close="errorMessage = ''"
      />

      <el-form label-position="top" class="backup-drawer__form">
        <el-form-item :label="t('backup.form.scope')">
          <el-radio-group v-model="scope" :disabled="exporting" class="backup-drawer__radios">
            <el-radio-button value="ALL">{{ t('backup.scope.ALL') }}</el-radio-button>
            <el-radio-button value="CORE">{{ t('backup.scope.CORE') }}</el-radio-button>
          </el-radio-group>
          <div class="backup-drawer__tip">{{ scopeTip }}</div>
        </el-form-item>

        <el-form-item :label="t('backup.form.format')">
          <el-radio-group v-model="format" :disabled="exporting" class="backup-drawer__radios">
            <el-radio-button value="JSON">{{ t('backup.format.JSON') }}</el-radio-button>
            <el-radio-button value="SQL">{{ t('backup.format.SQL') }}</el-radio-button>
          </el-radio-group>
          <div class="backup-drawer__tip">{{ formatTip }}</div>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">{{ t('backup.estimate.title') }}</el-divider>

      <el-descriptions :column="1" border size="small" class="backup-drawer__desc">
        <el-descriptions-item :label="t('backup.estimate.tableCount')">
          {{ tableCountText }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('backup.estimate.totalRows')">
          {{ totalRowsText }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('backup.estimate.fileName')">
          <span class="backup-drawer__filename">{{ previewFileName }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 后端超限告警（数据量较大提示） -->
      <el-alert
        v-if="estimate.warning"
        class="backup-drawer__alert"
        type="warning"
        show-icon
        :closable="false"
        :description="estimate.warning"
      />

      <el-collapse v-if="estimate.tables.length" v-model="activePanels" class="backup-drawer__collapse">
        <el-collapse-item name="tables" :title="t('backup.estimate.detail')">
          <div class="backup-drawer__table-wrap">
            <el-table :data="estimate.tables" size="small" max-height="240" class="backup-drawer__table">
              <el-table-column prop="name" :label="t('backup.col.table')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="rows" :label="t('backup.col.rows')" width="100" align="right">
                <template #default="{ row }">{{ formatNumber(row.rows) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-collapse-item>
      </el-collapse>
      <el-empty
        v-else-if="!estimating && loaded"
        :description="t('backup.estimate.empty')"
        :image-size="60"
      />

      <ul class="backup-drawer__notes">
        <li>{{ t('backup.tip.noBinary') }}</li>
        <li>{{ t('backup.tip.passwordMasked') }}</li>
        <li v-if="excludedText">{{ t('backup.tip.excluded', { tables: excludedText }) }}</li>
        <li>{{ t('backup.tip.noRepeat') }}</li>
      </ul>
    </div>
  </FormDrawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import FormDrawer from '@/components/FormDrawer.vue'
import {
  buildBackupFileName,
  estimateBackup,
  exportBackup,
  isBackupErrorResponse,
  parseBackupErrorMessage,
  resolveBackupFileName
} from '@/api/backup'
import { downloadBlob } from '@/utils/exportUtil'
import { formatNumber } from '@/utils/format'

const props = defineProps({
  /** v-model 显隐 */
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'exported'])

const { t } = useI18n()

/** 导出范围：ALL 全量 / CORE 仅核心配置（默认 ALL，与 ARCH §4.6 时序一致） */
const scope = ref('ALL')

/** 导出格式：JSON / SQL */
const format = ref('JSON')

/** 预估请求中 */
const estimating = ref(false)

/** 导出请求中（按钮 loading + 禁用，防重复点击） */
const exporting = ref(false)

/** 是否已完成过一次预估（用于空态判断，避免首屏闪 empty） */
const loaded = ref(false)

/** 面板内错误信息（预估失败 / 导出失败的后端 message） */
const errorMessage = ref('')

/** 明细折叠面板默认收起 */
const activePanels = ref([])

/** 预估结果（结构镜像后端 BackupEstimateVO） */
const estimate = reactive({
  scope: 'ALL',
  tableCount: 0,
  totalRows: 0,
  tables: [],
  suggestedFileName: '',
  warning: '',
  attachmentBinaryIncluded: false,
  excludedTables: []
})

const scopeTip = computed(() =>
  scope.value === 'CORE' ? t('backup.scope.coreTip') : t('backup.scope.allTip')
)

const formatTip = computed(() =>
  format.value === 'SQL' ? t('backup.format.sqlTip') : t('backup.format.jsonTip')
)

const tableCountText = computed(() =>
  t('backup.estimate.tableUnit', { count: formatNumber(estimate.tableCount) })
)

const totalRowsText = computed(() =>
  t('backup.estimate.rowUnit', { count: formatNumber(estimate.totalRows) })
)

const excludedText = computed(() => (estimate.excludedTables || []).join(', '))

/**
 * 文件名预览：以后端建议名为基准，按当前格式替换扩展名；
 * 后端未返回时本地按 backup_YYYY-MM-DD_HHMMSS.{ext} 兜底。
 */
const previewFileName = computed(() => {
  const ext = format.value === 'SQL' ? 'sql' : 'json'
  const suggested = estimate.suggestedFileName
  if (suggested) {
    return suggested.replace(/\.(json|sql)$/i, `.${ext}`)
  }
  return buildBackupFileName(format.value)
})

/** 重置面板状态（关闭后调用，下次打开是干净的） */
function resetState() {
  scope.value = 'ALL'
  format.value = 'JSON'
  estimating.value = false
  exporting.value = false
  loaded.value = false
  errorMessage.value = ''
  activePanels.value = []
  Object.assign(estimate, {
    scope: 'ALL',
    tableCount: 0,
    totalRows: 0,
    tables: [],
    suggestedFileName: '',
    warning: '',
    attachmentBinaryIncluded: false,
    excludedTables: []
  })
}

/**
 * 拉取导出预估（打开面板与切换范围时触发）。
 * @returns {Promise<void>}
 */
async function refreshEstimate() {
  estimating.value = true
  errorMessage.value = ''
  try {
    const data = await estimateBackup({ scope: scope.value })
    Object.assign(estimate, {
      scope: (data && data.scope) || scope.value,
      tableCount: (data && data.tableCount) || 0,
      totalRows: (data && data.totalRows) || 0,
      tables: (data && data.tables) || [],
      suggestedFileName: (data && data.suggestedFileName) || '',
      warning: (data && data.warning) || '',
      attachmentBinaryIncluded: Boolean(data && data.attachmentBinaryIncluded),
      excludedTables: (data && data.excludedTables) || []
    })
    loaded.value = true
  } catch (e) {
    errorMessage.value = (e && e.message) || t('backup.msg.estimateFailed')
    estimate.tables = []
    estimate.tableCount = 0
    estimate.totalRows = 0
    loaded.value = true
  } finally {
    estimating.value = false
  }
}

/**
 * 执行导出：成功触发 blob 下载并关闭面板；
 * 失败（HTTP 200 + JSON 错误体）在面板内展示具体 message，可重试。
 * @returns {Promise<void>}
 */
async function doExport() {
  if (exporting.value) return
  exporting.value = true
  errorMessage.value = ''
  try {
    const response = await exportBackup({ scope: scope.value, format: format.value })
    const blob = response && response.data
    if (!blob) {
      errorMessage.value = t('backup.msg.emptyBody')
      return
    }
    // 后端约定：失败时返回 application/json 错误体，必须先判类型再下载
    if (isBackupErrorResponse(response)) {
      const message = await parseBackupErrorMessage(blob)
      errorMessage.value = message || t('backup.msg.failed')
      return
    }
    const fileName = resolveBackupFileName(response, buildBackupFileName(format.value))
    downloadBlob(blob, fileName)
    ElMessage.success(t('backup.msg.success', { name: fileName }))
    emit('exported', { scope: scope.value, format: format.value, fileName })
    emit('update:modelValue', false)
  } catch (e) {
    errorMessage.value = (e && e.message) || t('backup.msg.failed')
  } finally {
    exporting.value = false
  }
}

function onVisibleChange(value) {
  emit('update:modelValue', value)
}

function onClosed() {
  resetState()
}

// 打开面板 → 首次预估（默认 ALL + JSON）
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      resetState()
      refreshEstimate()
    }
  }
)

// 切换范围 → 实时刷新表数 / 条数；格式仅影响扩展名，无需重新请求
watch(scope, () => {
  if (props.modelValue) {
    refreshEstimate()
  }
})

defineExpose({ refreshEstimate, doExport })
</script>

<style scoped>
.backup-drawer {
  min-height: 200px;
}

.backup-drawer__alert {
  margin-bottom: 12px;
}

.backup-drawer__form {
  margin-bottom: 4px;
}

.backup-drawer__radios {
  flex-wrap: wrap;
}

.backup-drawer__tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.backup-drawer__desc {
  margin-bottom: 12px;
}

.backup-drawer__filename {
  font-family: var(--el-font-family-monospace, monospace);
  word-break: break-all;
  color: var(--el-text-color-primary);
}

.backup-drawer__collapse {
  margin-bottom: 12px;
}

/* 移动端：明细表格允许横向滚动，避免列被压扁 */
.backup-drawer__table-wrap {
  width: 100%;
  overflow-x: auto;
}

.backup-drawer__table {
  min-width: 280px;
}

.backup-drawer__notes {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
</style>

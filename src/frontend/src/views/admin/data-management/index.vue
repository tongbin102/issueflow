<template>
  <!--
    数据管理页（Phase10 需求三）—— 承接原「备份设置」页。

    页面职责：
      1. 发起备份 / 上传备份包，并用进度弹窗跟踪异步任务；
      2. 备份历史列表（筛选 + 分页 + 下载 / 详情 / 恢复 / 删除）；
      3. 备份保留策略维护；
      4. 数据初始化（危险操作，沿用既有 DataResetDrawer）。

    容错原则：所有接口调用都有 loading 与 toast，失败绝不静默。
    任务类接口（备份 / 恢复 / 上传）成功后统一拉起进度轮询，
    终态时自动刷新列表，保证用户看到的状态永远是后端的真实状态。
  -->
  <div class="dm-page">
    <el-card shadow="never" class="dm-card">
      <template #header>
        <div class="dm-card__header">
          <div>
            <div class="dm-card__title">{{ t('dataManagement.title') }}</div>
            <div class="dm-card__subtitle">{{ t('dataManagement.subtitle') }}</div>
          </div>
          <div class="dm-card__actions">
            <el-button type="primary" :icon="Plus" @click="openCreate">
              {{ t('dataManagement.action.create') }}
            </el-button>
            <el-button :icon="Upload" @click="openUpload">
              {{ t('dataManagement.action.upload') }}
            </el-button>
            <el-button :icon="Setting" @click="openConfig">
              {{ t('dataManagement.action.config') }}
            </el-button>
            <el-button :icon="Refresh" :loading="listLoading" @click="fetchList">
              {{ t('dataManagement.action.refresh') }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="dm-filter" @submit.prevent>
        <el-form-item :label="t('dataManagement.filter.keyword')">
          <el-input
            v-model="filter.keyword"
            clearable
            style="width: 200px"
            :placeholder="t('dataManagement.filter.keywordPlaceholder')"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('dataManagement.filter.type')">
          <el-select
            v-model="filter.backupType"
            clearable
            style="width: 150px"
            :placeholder="t('dataManagement.filter.all')"
          >
            <el-option
              v-for="item in BACKUP_TYPES"
              :key="item"
              :label="t(`dataManagement.type.${item}`)"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dataManagement.filter.source')">
          <el-select
            v-model="filter.source"
            clearable
            style="width: 160px"
            :placeholder="t('dataManagement.filter.all')"
          >
            <el-option
              v-for="item in BACKUP_SOURCES"
              :key="item"
              :label="t(`dataManagement.source.${item}`)"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dataManagement.filter.status')">
          <el-select
            v-model="filter.status"
            clearable
            style="width: 140px"
            :placeholder="t('dataManagement.filter.all')"
          >
            <el-option
              v-for="item in TASK_STATUSES"
              :key="item"
              :label="t(`dataManagement.status.${item}`)"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            {{ t('dataManagement.action.search') }}
          </el-button>
          <el-button @click="handleReset">{{ t('dataManagement.action.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 备份历史表格 -->
      <el-table v-loading="listLoading" :data="rows" border stripe>
        <el-table-column
          :label="t('dataManagement.column.name')"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.name || row.fileName || '-' }}</template>
        </el-table-column>

        <el-table-column
          prop="fileName"
          :label="t('dataManagement.column.fileName')"
          min-width="200"
          show-overflow-tooltip
        />

        <el-table-column :label="t('dataManagement.column.type')" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ enumText('type', row.backupType) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="t('dataManagement.column.source')" width="130">
          <template #default="{ row }">{{ enumText('source', row.source) }}</template>
        </el-table-column>

        <el-table-column :label="t('dataManagement.column.size')" width="110">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>

        <el-table-column :label="t('dataManagement.column.status')" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">
              {{ enumText('status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          prop="operatorName"
          :label="t('dataManagement.column.operator')"
          width="120"
          show-overflow-tooltip
        />

        <el-table-column :label="t('dataManagement.column.duration')" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>

        <el-table-column
          prop="createTime"
          :label="t('dataManagement.column.createTime')"
          width="170"
        />

        <el-table-column
          :label="t('dataManagement.column.actions')"
          width="240"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ t('dataManagement.action.detail') }}
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="!isRestorable(row)"
              @click="handleDownload(row)"
            >
              {{ t('dataManagement.action.download') }}
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="!isRestorable(row)"
              @click="openRestore(row)"
            >
              {{ t('dataManagement.action.restore') }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              {{ t('dataManagement.action.delete') }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty :description="t('dataManagement.empty')" />
        </template>
      </el-table>

      <div class="dm-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 危险操作：数据初始化（沿用既有抽屉组件） -->
    <el-card shadow="never" class="dm-card dm-card--danger">
      <template #header>
        <span class="dm-card__title dm-card__title--danger">
          {{ t('dataManagement.reset.group') }}
        </span>
      </template>
      <div class="dm-reset">
        <div class="dm-reset__info">
          <div class="dm-reset__title">{{ t('dataManagement.reset.title') }}</div>
          <div class="dm-reset__desc">{{ t('dataManagement.reset.desc') }}</div>
        </div>
        <el-button type="danger" plain @click="resetVisible = true">
          {{ t('dataManagement.reset.button') }}
        </el-button>
      </div>
    </el-card>

    <!-- 各类弹窗 -->
    <CreateBackupDialog
      v-model="createVisible"
      :submitting="creating"
      @submit="handleCreate"
    />

    <RestoreConfirmDialog
      v-model="restoreVisible"
      :backup="currentRow"
      :submitting="restoring"
      @submit="handleRestore"
    />

    <UploadRestoreDialog
      v-model="uploadVisible"
      :size-limit-m-b="config.sizeLimitMB"
      :uploading="uploading"
      :upload-percent="uploadPercent"
      @submit="handleUpload"
    />

    <RetentionConfigDialog
      v-model="configVisible"
      :config="config"
      :saving="configSaving"
      @submit="handleSaveConfig"
    />

    <BackupDetailDialog
      v-model="detailVisible"
      :detail="detail"
      :loading="detailLoading"
    />

    <TaskProgressPanel
      v-model="progressVisible"
      :task-type="taskType"
      :percent="percent"
      :phase="phase"
      :message="message"
      :error-msg="errorMsg"
      :finished="finished"
      :succeeded="succeeded"
      :failed="failed"
      :lost="lostConnection"
      @close="handleProgressClose"
    />

    <DataResetDrawer v-model="resetVisible" @success="fetchList" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Setting, Upload } from '@element-plus/icons-vue'

import {
  BACKUP_SOURCES,
  BACKUP_TYPES,
  TASK_STATUSES,
  createBackup,
  deleteBackup,
  downloadBackup,
  fetchBackupDetail,
  fetchBackupList,
  fetchDataConfig,
  restoreBackup,
  updateDataConfig,
  uploadAndRestore
} from '@/api/dataManagement'
import { useTaskProgress } from '@/composables/useTaskProgress'
import { formatDuration, formatSize, parseFileName } from './format'

import CreateBackupDialog from './components/CreateBackupDialog.vue'
import RestoreConfirmDialog from './components/RestoreConfirmDialog.vue'
import UploadRestoreDialog from './components/UploadRestoreDialog.vue'
import RetentionConfigDialog from './components/RetentionConfigDialog.vue'
import BackupDetailDialog from './components/BackupDetailDialog.vue'
import TaskProgressPanel from './components/TaskProgressPanel.vue'
import DataResetDrawer from '@/components/DataResetDrawer.vue'

const { t, te } = useI18n()

/* ------------------------------ 列表状态 ------------------------------ */

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const listLoading = ref(false)

const filter = reactive({
  keyword: '',
  backupType: '',
  source: '',
  status: ''
})

/* ------------------------------ 弹窗状态 ------------------------------ */

const createVisible = ref(false)
const creating = ref(false)

const restoreVisible = ref(false)
const restoring = ref(false)
/** 当前操作的行（恢复 / 详情共用） */
const currentRow = ref(null)

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)

const configVisible = ref(false)
const configSaving = ref(false)
/** 保留策略；默认值与后端 DTO 一致，接口没回来之前也能正常渲染 */
const config = reactive({
  maxCopies: 20,
  defaultDays: 30,
  sizeLimitMB: 512
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const resetVisible = ref(false)

/* ------------------------------ 任务进度 ------------------------------ */

const progressVisible = ref(false)
/** 当前任务类型：BACKUP / RESTORE，决定进度弹窗的措辞 */
const taskType = ref('BACKUP')

const {
  percent,
  phase,
  message,
  errorMsg,
  finished,
  succeeded,
  failed,
  lostConnection,
  start: startPolling,
  stop: stopPolling
} = useTaskProgress()

/**
 * 任务一进终态就刷新列表：备份成功要出现在列表里，
 * 恢复成功后列表本身也可能被覆盖成备份时刻的数据。
 */
watch(finished, (value) => {
  if (value) fetchList()
})

/* ------------------------------ 工具函数 ------------------------------ */

/**
 * 枚举码转文案，未收录的码回退到「未知」。
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

/**
 * 状态对应的标签配色。
 *
 * @param {string} status 状态码
 * @returns {string} el-tag 的 type
 */
function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELED') return 'info'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

/**
 * 仅成功的备份才能下载 / 恢复 —— 失败或执行中的记录背后没有可用文件，
 * 放开入口只会让用户拿到一个 0 字节的 zip。
 *
 * @param {Object} row 备份记录
 * @returns {boolean} 是否可下载 / 恢复
 */
function isRestorable(row) {
  return Boolean(row) && row.status === 'SUCCESS'
}

/**
 * 统一的错误提示：优先用后端返回的 message，避免弹一个无信息量的「请求失败」。
 *
 * @param {Error} error 异常对象
 * @param {string} fallback 兜底文案
 */
function notifyError(error, fallback) {
  const msg = (error && (error.message || error.msg)) || ''
  ElMessage.error(msg || fallback)
}

/* ------------------------------ 数据加载 ------------------------------ */

/** 拉取备份列表 */
async function fetchList() {
  listLoading.value = true
  try {
    const data = await fetchBackupList({
      page: page.value,
      size: size.value,
      keyword: filter.keyword || undefined,
      backupType: filter.backupType || undefined,
      source: filter.source || undefined,
      status: filter.status || undefined
    })
    rows.value = (data && data.list) || []
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    notifyError(error, t('common.msg.loadFailed'))
  } finally {
    listLoading.value = false
  }
}

/** 拉取保留策略配置（失败不阻断页面，沿用默认值） */
async function loadConfig() {
  try {
    const data = await fetchDataConfig()
    if (data) {
      config.maxCopies = Number(data.maxCopies) || config.maxCopies
      config.defaultDays = Number(data.defaultDays) || config.defaultDays
      config.sizeLimitMB = Number(data.sizeLimitMB) || config.sizeLimitMB
    }
  } catch (error) {
    // 配置读失败只影响上传体积提示，列表功能照常，故仅告警不打断
    notifyError(error, t('common.msg.loadFailed'))
  }
}

/* ------------------------------ 筛选 / 分页 ------------------------------ */

/** 查询：回到第一页再拉数据 */
function handleSearch() {
  page.value = 1
  fetchList()
}

/** 重置筛选条件 */
function handleReset() {
  filter.keyword = ''
  filter.backupType = ''
  filter.source = ''
  filter.status = ''
  page.value = 1
  fetchList()
}

/** 每页条数变化 */
function handleSizeChange() {
  page.value = 1
  fetchList()
}

/* ------------------------------ 备份 ------------------------------ */

/** 打开新建备份弹窗 */
function openCreate() {
  createVisible.value = true
}

/**
 * 创建备份：受理后立刻拉起进度轮询。
 *
 * @param {{name: string, type: string, includeConfig: boolean}} payload 备份参数
 */
async function handleCreate(payload) {
  creating.value = true
  try {
    const progress = await createBackup(payload)
    createVisible.value = false
    ElMessage.success(t('dataManagement.msg.createAccepted'))
    beginTask('BACKUP', progress)
  } catch (error) {
    notifyError(error, t('dataManagement.msg.taskRunning'))
  } finally {
    creating.value = false
  }
}

/* ------------------------------ 下载 ------------------------------ */

/**
 * 下载备份包。
 *
 * 后端鉴权失败时返回的是 JSON 而非 zip，这里靠 Content-Type 判别，
 * 否则用户会得到一个内容是错误信息的 .zip 文件。
 *
 * @param {Object} row 备份记录
 */
async function handleDownload(row) {
  if (!isRestorable(row)) return
  try {
    const response = await downloadBackup(row.id)
    const blob = response && response.data
    const contentType = (response && response.headers && response.headers['content-type']) || ''

    if (!blob || contentType.includes('application/json')) {
      ElMessage.error(t('dataManagement.msg.downloadFailed'))
      return
    }

    const disposition =
      (response.headers && response.headers['content-disposition']) || ''
    const fileName = parseFileName(disposition, row.fileName || 'backup.zip')

    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success(t('dataManagement.msg.downloadStarted'))
  } catch (error) {
    notifyError(error, t('dataManagement.msg.downloadFailed'))
  }
}

/* ------------------------------ 删除 ------------------------------ */

/**
 * 删除备份（二次确认）。
 *
 * @param {Object} row 备份记录
 */
async function handleDelete(row) {
  if (!row) return
  try {
    await ElMessageBox.confirm(
      t('dataManagement.msg.deleteConfirm', { name: row.name || row.fileName || '' }),
      t('dataManagement.msg.deleteConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('dataManagement.action.confirm'),
        cancelButtonText: t('dataManagement.action.cancel')
      }
    )
  } catch (e) {
    // 用户取消，静默返回
    return
  }

  try {
    await deleteBackup(row.id)
    ElMessage.success(t('dataManagement.msg.deleteSuccess'))
    // 删掉当前页最后一条时回退一页，避免停在空白页
    if (rows.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    fetchList()
  } catch (error) {
    notifyError(error, t('common.msg.loadFailed'))
  }
}

/* ------------------------------ 详情 ------------------------------ */

/**
 * 打开详情弹窗并拉取详情。
 *
 * @param {Object} row 备份记录
 */
async function openDetail(row) {
  if (!row) return
  currentRow.value = row
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchBackupDetail(row.id)
  } catch (error) {
    notifyError(error, t('common.msg.loadFailed'))
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/* ------------------------------ 恢复 ------------------------------ */

/**
 * 打开恢复确认弹窗。
 *
 * @param {Object} row 备份记录
 */
function openRestore(row) {
  if (!isRestorable(row)) return
  currentRow.value = row
  restoreVisible.value = true
}

/**
 * 提交恢复。
 *
 * @param {{preBackup: boolean, remark: string}} payload 恢复参数
 */
async function handleRestore(payload) {
  if (!currentRow.value) return
  restoring.value = true
  try {
    const progress = await restoreBackup(currentRow.value.id, payload)
    restoreVisible.value = false
    ElMessage.warning(t('dataManagement.msg.restoreAccepted'))
    beginTask('RESTORE', progress)
  } catch (error) {
    notifyError(error, t('dataManagement.msg.taskRunning'))
  } finally {
    restoring.value = false
  }
}

/* ------------------------------ 上传恢复 ------------------------------ */

/** 打开上传弹窗（顺带刷新一次体积上限） */
function openUpload() {
  loadConfig()
  uploadVisible.value = true
}

/**
 * 上传备份包（可选立即恢复）。
 *
 * @param {{file: File, meta: Object}} payload 上传参数
 */
async function handleUpload(payload) {
  uploading.value = true
  uploadPercent.value = 0
  try {
    const progress = await uploadAndRestore(payload.file, payload.meta, (percentValue) => {
      uploadPercent.value = percentValue
    })
    uploadPercent.value = 100
    uploadVisible.value = false
    ElMessage.success(t('dataManagement.msg.uploadAccepted'))

    if (payload.meta.restoreNow) {
      beginTask('RESTORE', progress)
    } else {
      // 仅登记：没有后台任务可跟踪，直接刷新列表即可
      ElMessage.success(t('dataManagement.upload.onlyRegisterDone'))
      fetchList()
    }
  } catch (error) {
    notifyError(error, t('common.msg.loadFailed'))
  } finally {
    uploading.value = false
  }
}

/* ------------------------------ 保留策略 ------------------------------ */

/** 打开保留策略弹窗 */
function openConfig() {
  loadConfig()
  configVisible.value = true
}

/**
 * 保存保留策略。
 *
 * @param {{maxCopies: number, defaultDays: number, sizeLimitMB: number}} payload 新配置
 */
async function handleSaveConfig(payload) {
  configSaving.value = true
  try {
    const data = await updateDataConfig(payload)
    const saved = data || payload
    config.maxCopies = Number(saved.maxCopies) || payload.maxCopies
    config.defaultDays = Number(saved.defaultDays) || payload.defaultDays
    config.sizeLimitMB = Number(saved.sizeLimitMB) || payload.sizeLimitMB
    configVisible.value = false
    ElMessage.success(t('dataManagement.config.saved'))
  } catch (error) {
    notifyError(error, t('common.msg.loadFailed'))
  } finally {
    configSaving.value = false
  }
}

/* ------------------------------ 任务轮询 ------------------------------ */

/**
 * 拉起一个后台任务的进度跟踪。
 *
 * @param {string} type 任务类型：BACKUP / RESTORE
 * @param {Object} progress 后端返回的初始进度（TaskProgressDTO）
 */
function beginTask(type, progress) {
  const id = progress && progress.taskId
  if (!id) {
    // 拿不到 taskId 就别假装有进度，直接刷新列表让用户看真实状态
    fetchList()
    return
  }
  taskType.value = type
  progressVisible.value = true
  startPolling(id, progress)
}

/** 进度弹窗关闭：停轮询并刷新列表 */
function handleProgressClose() {
  stopPolling()
  fetchList()
}

/* ------------------------------ 生命周期 ------------------------------ */

onMounted(() => {
  fetchList()
  loadConfig()
})
</script>

<style scoped>
.dm-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dm-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.dm-card__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.dm-card__title--danger {
  color: var(--el-color-danger);
}

.dm-card__subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dm-card__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.dm-filter {
  margin-bottom: 4px;
}

.dm-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dm-card--danger {
  border-color: var(--el-color-danger-light-7);
}

.dm-reset {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dm-reset__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.dm-reset__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
</style>

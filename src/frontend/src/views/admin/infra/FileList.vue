<template>
  <!-- Phase7 T6：基础设施 > 文件管理 > 文件列表（分页 + 上传 + 预览 / 下载 / 删除） -->
  <div class="file-list">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('infra.file.list.title') }}</span>
          <div class="head__actions">
            <el-button :icon="Refresh" @click="fetchData">{{ t('common.action.refresh') }}</el-button>
            <!-- 自定义 http-request：走 api/fileManage.uploadFile（带 Bearer + 进度） -->
            <el-upload
              v-perm="'file:upload'"
              class="uploader"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :http-request="customUpload"
            >
              <el-button type="primary" :icon="Upload" :loading="uploading">
                {{ uploading ? t('infra.file.list.uploading') : t('infra.file.list.upload') }}
              </el-button>
            </el-upload>
          </div>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item :label="t('infra.file.list.filter.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('common.placeholder.search')"
            clearable
            class="filter-input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item :label="t('infra.file.list.filter.ext')">
          <el-input
            v-model="query.ext"
            :placeholder="t('infra.file.list.filter.extPlaceholder')"
            clearable
            class="filter-input filter-input--sm"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item :label="t('infra.file.list.filter.bizType')">
          <el-input
            v-model="query.bizType"
            :placeholder="t('infra.file.list.filter.bizTypePlaceholder')"
            clearable
            class="filter-input filter-input--sm"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.field.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('common.field.startDate')"
            :end-placeholder="t('common.field.endDate')"
            class="filter-date"
            unlink-panels
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">
            {{ t('common.action.search') }}
          </el-button>
          <el-button @click="onReset">{{ t('common.action.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表（移动端横向滚动，见 .table-wrap） -->
      <div class="table-wrap">
        <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
          <el-table-column
            prop="originalName"
            :label="t('infra.file.list.col.name')"
            min-width="220"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div class="file-cell">
                <el-icon class="file-cell__icon">
                  <Picture v-if="row.previewable" />
                  <Document v-else />
                </el-icon>
                <span>{{ row.originalName || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="ext"
            :label="t('infra.file.list.col.ext')"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.ext || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.file.list.col.size')" width="110" align="right">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column
            prop="bizType"
            :label="t('infra.file.list.col.bizType')"
            width="130"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.bizType || '-' }}</template>
          </el-table-column>
          <el-table-column
            prop="bizRef"
            :label="t('infra.file.list.col.bizRef')"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.bizRef || '-' }}</template>
          </el-table-column>
          <el-table-column
            prop="uploaderName"
            :label="t('infra.file.list.col.uploader')"
            width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.uploaderName || '-' }}</template>
          </el-table-column>
          <el-table-column
            prop="createdAt"
            :label="t('infra.file.list.col.createdAt')"
            width="170"
          >
            <template #default="{ row }">{{ row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column
            :label="t('infra.file.list.col.actions')"
            width="180"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="row.previewable"
                link
                type="primary"
                size="small"
                @click="onPreview(row)"
              >
                {{ t('infra.file.list.preview') }}
              </el-button>
              <el-button link type="primary" size="small" @click="onDownload(row)">
                {{ t('common.action.download') }}
              </el-button>
              <el-button
                v-perm="'file:delete'"
                link
                type="danger"
                size="small"
                @click="onDelete(row)"
              >
                {{ t('common.action.delete') }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="t('infra.file.list.empty')" :image-size="60" />
          </template>
        </el-table>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          :layout="pagerLayout"
          background
          @current-change="fetchData"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 图片预览弹窗：blob object URL（带 token，避免直链 401） -->
    <el-dialog
      v-model="previewVisible"
      :title="t('infra.file.list.previewTitle', { name: previewName })"
      width="60%"
      append-to-body
      @closed="onPreviewClosed"
    >
      <div class="preview-box">
        <img v-if="previewUrl" :src="previewUrl" class="preview-img" :alt="previewName" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search, Refresh, Document, Picture } from '@element-plus/icons-vue'
import {
  pageFiles,
  uploadFile,
  downloadFile,
  previewFile,
  deleteFile,
  getFileConfig
} from '@/api/fileManage'
import { downloadBlob } from '@/utils/exportUtil'
import { formatFileSize } from '@/utils/format'
import { useAppStore } from '@/store/app'

const { t } = useI18n()
const appStore = useAppStore()

const loading = ref(false)
const uploading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const query = reactive({ keyword: '', ext: '', bizType: '' })
/** 上传时间范围 [startDate, endDate]，value-format 'YYYY-MM-DD' */
const dateRange = ref([])

/** 前端预校验用的上传限制（取自 /api/admin/files/config，拿不到则放行交后端判） */
const uploadLimit = reactive({ maxSizeMb: 0, allowedExts: '' })

const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

/** 移动端精简分页布局 */
const pagerLayout = computed(() =>
  appStore.isMobile ? 'total, prev, next' : 'total, sizes, prev, pager, next, jumper'
)

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (query.keyword) params.keyword = query.keyword.trim()
    if (query.ext) params.ext = query.ext.trim().toLowerCase()
    if (query.bizType) params.bizType = query.bizType.trim()
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const data = (await pageFiles(params)) || {}
    list.value = data.list || []
    total.value = Number(data.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  fetchData()
}

function onSizeChange() {
  page.value = 1
  fetchData()
}

function onReset() {
  query.keyword = ''
  query.ext = ''
  query.bizType = ''
  dateRange.value = []
  page.value = 1
  fetchData()
}

/** 拉取上传限制，仅用于前端预校验（失败静默，不影响页面） */
async function loadUploadLimit() {
  try {
    const config = (await getFileConfig()) || {}
    uploadLimit.maxSizeMb = config.maxSizeMb ?? 0
    uploadLimit.allowedExts = config.allowedExts || ''
  } catch (e) {
    uploadLimit.maxSizeMb = 0
    uploadLimit.allowedExts = ''
  }
}

/**
 * 上传前校验：大小 + 扩展名（与后端 FileConfigService.validate 同口径，提前拦截省一次往返）。
 * @param {File} file 待上传文件
 * @returns {boolean} false 阻止上传
 */
function beforeUpload(file) {
  const maxMb = uploadLimit.maxSizeMb
  if (maxMb > 0 && file.size / 1024 / 1024 > maxMb) {
    ElMessage.error(t('infra.file.list.sizeExceed', { max: maxMb }))
    return false
  }
  const allowed = (uploadLimit.allowedExts || '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
  if (allowed.length) {
    const name = file.name || ''
    const dot = name.lastIndexOf('.')
    const ext = dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
    if (!allowed.includes(ext)) {
      ElMessage.error(
        t('infra.file.list.extNotAllowed', { ext: ext || '-', allowed: allowed.join(', ') })
      )
      return false
    }
  }
  return true
}

/**
 * el-upload 自定义上传：绕开组件默认 XHR，复用 axios 实例（自动带 Bearer + 统一错误处理）。
 * @param {{file:File}} options el-upload 传入的上传选项
 */
async function customUpload(options) {
  const file = options && options.file
  if (!file) return
  uploading.value = true
  try {
    await uploadFile(file)
    ElMessage.success(t('infra.file.list.uploadSuccess'))
    page.value = 1
    await fetchData()
    // 上传成功后刷新限制（管理员可能刚改过配置）
    loadUploadLimit()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

function onDownload(row) {
  downloadFile(row.id)
    .then((blob) => downloadBlob(blob, row.originalName || `file-${row.id}`))
    .catch(() => {})
}

async function onPreview(row) {
  if (!row.previewable) {
    ElMessage.warning(t('infra.file.list.previewUnsupported'))
    return
  }
  try {
    const url = await previewFile(row.id)
    releasePreview()
    previewUrl.value = url
    previewName.value = row.originalName || ''
    previewVisible.value = true
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

/** 释放已创建的 object URL，避免内存泄漏 */
function releasePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function onPreviewClosed() {
  releasePreview()
  previewName.value = ''
}

function onDelete(row) {
  ElMessageBox.confirm(
    t('infra.file.list.deleteConfirm', { name: row.originalName || row.id }),
    t('common.msg.warning'),
    { type: 'warning' }
  )
    .then(async () => {
      try {
        const message = await deleteFile(row.id)
        ElMessage.success(message || t('infra.file.list.deleteSuccess'))
        // 删除后当前页可能空掉，回退一页
        if (list.value.length === 1 && page.value > 1) page.value -= 1
        fetchData()
      } catch (e) {
        // 错误提示由 request 拦截器统一处理
      }
    })
    .catch(() => {})
}

onMounted(() => {
  loadUploadLimit()
  fetchData()
})

onBeforeUnmount(releasePreview)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.head__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.uploader {
  display: inline-block;
}

.filter-form {
  margin-bottom: 4px;
}

.filter-input {
  width: 200px;
}

.filter-input--sm {
  width: 140px;
}

.filter-date {
  width: 260px;
}

/* 移动端：表格横向滚动，不压缩列宽 */
.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.file-cell__icon {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.preview-box {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
  background: var(--if-stat-card-bg, var(--el-fill-color-lighter));
}

.preview-img {
  max-width: 100%;
  max-height: 60vh;
  object-fit: contain;
}

@media (max-width: 768px) {
  .head__actions {
    width: 100%;
  }
  .filter-input,
  .filter-input--sm,
  .filter-date {
    width: 100%;
  }
  .filter-form :deep(.el-form-item) {
    display: block;
    margin-right: 0;
  }
  .pager {
    justify-content: center;
  }
}
</style>

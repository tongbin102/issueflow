<template>
  <div class="attachment-uploader">
    <!-- 详情模式：issueId 存在 → 展示已上传 + 服务端上传 -->
    <template v-if="issueId">
      <div class="att-list">
        <div v-for="att in attachments" :key="att.id" class="att-item">
          <div class="att-thumb">
            <img
              v-if="isImage(att)"
              :src="previewUrls[att.id]"
              alt="预览"
              class="att-img"
            />
            <el-icon v-else class="att-file-icon"><Document /></el-icon>
          </div>
          <div class="att-meta">
            <div class="att-name" :title="att.originalName">
              {{ att.originalName || att.fileName }}
            </div>
            <div class="att-size text-muted">{{ formatSize(att.fileSize) }}</div>
          </div>
          <div class="att-actions">
            <el-button
              v-if="isImage(att)"
              link
              type="primary"
              size="small"
              @click="onPreview(att)"
              >预览</el-button
            >
            <el-button link type="primary" size="small" @click="onDownload(att)"
              >下载</el-button
            >
            <el-button link type="danger" size="small" @click="onDelete(att)"
              >删除</el-button
            >
          </div>
        </div>
        <el-empty
          v-if="!attachments.length"
          description="暂无附件"
          :image-size="48"
        />
      </div>
      <el-upload
        class="att-uploader"
        :show-file-list="false"
        :auto-upload="false"
        :before-upload="beforeUpload"
        :on-change="handleDetailChange"
      >
        <el-button :icon="Upload" :loading="uploading">上传附件</el-button>
      </el-upload>
    </template>

    <!-- 新建模式：issueId 为空 → 仅本地收集文件（由父组件读取后随表单提交） -->
    <el-upload
      v-else
      v-model:file-list="localList"
      list-type="picture-card"
      :auto-upload="false"
      :before-upload="beforeUpload"
      :on-change="handleLocalChange"
      :on-remove="handleLocalChange"
    >
      <el-icon><Plus /></el-icon>
    </el-upload>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Plus, Document } from '@element-plus/icons-vue'
import {
  uploadAttachments,
  downloadAttachment,
  deleteAttachment,
  previewAttachment
} from '@/api/issue'
import { downloadBlob } from '@/utils/exportUtil'

const props = defineProps({
  // 详情模式下传入问题 id（启用服务端上传/删除）
  issueId: { type: [Number, String], default: null },
  // 已上传附件列表（详情模式展示）：{id,originalName,fileName,fileSize,contentType}
  attachments: { type: Array, default: () => [] },
  maxSizeMB: { type: Number, default: 20 }
})
const emit = defineEmits(['change', 'uploaded', 'removed'])

const uploading = ref(false)
const localList = ref([])

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function isImage(att) {
  const ct = (att.contentType || '').toLowerCase()
  const name = (att.originalName || att.fileName || '').toLowerCase()
  return ct.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp)$/.test(name)
}

function beforeUpload(file) {
  const sizeMB = file.size / 1024 / 1024
  if (sizeMB > props.maxSizeMB) {
    ElMessage.error(`文件超过 ${props.maxSizeMB}MB 限制`)
    return false
  }
  return true
}

// 新建模式：本地文件变化 → 暴露给父组件
function handleLocalChange() {
  const files = localList.value.map((f) => f.raw).filter(Boolean)
  emit('change', files)
}
// 供父组件读取已选文件
function getFiles() {
  return localList.value.map((f) => f.raw).filter(Boolean)
}
// 清空本地已选文件
function clear() {
  localList.value = []
  emit('change', [])
}
defineExpose({ getFiles, clear })

// 详情模式：选中文件即上传
function handleDetailChange(uploadFile) {
  const file = uploadFile && uploadFile.raw
  if (!file) return
  if (!beforeUpload(file)) return
  uploading.value = true
  uploadAttachments(props.issueId, [file])
    .then((res) => {
      const list = Array.isArray(res) ? res : res ? [res] : []
      list.forEach((att) => emit('uploaded', att))
      ElMessage.success('上传成功')
    })
    .catch(() => {})
    .finally(() => {
      uploading.value = false
    })
}

function onDownload(att) {
  downloadAttachment(att.id)
    .then((blob) => downloadBlob(blob, att.originalName || att.fileName))
    .catch(() => {})
}

// 图片预览：fetch 携带 token 取 blob → object URL，新标签页打开
function onPreview(att) {
  previewAttachment(att.id)
    .then((url) => {
      createdUrls.add(url)
      window.open(url, '_blank')
    })
    .catch(() => {})
}

// 为每个图片附件预拉取预览 blob URL（带 token，避免 <img> 直链 401）
const previewUrls = reactive({})
const createdUrls = new Set()

function buildPreviewUrls(list) {
  const ids = new Set((list || []).map((a) => a.id))
  Object.keys(previewUrls).forEach((key) => {
    if (!ids.has(Number(key))) {
      URL.revokeObjectURL(previewUrls[key])
      delete previewUrls[key]
    }
  })
  ;(list || []).forEach((att) => {
    if (isImage(att) && !previewUrls[att.id]) {
      previewAttachment(att.id)
        .then((url) => {
          previewUrls[att.id] = url
          createdUrls.add(url)
        })
        .catch(() => {})
    }
  })
}

watch(() => props.attachments, (list) => buildPreviewUrls(list), { immediate: true })

onBeforeUnmount(() => {
  createdUrls.forEach((u) => URL.revokeObjectURL(u))
  createdUrls.clear()
})

function onDelete(att) {
  deleteAttachment(att.id)
    .then(() => {
      emit('removed', att.id)
      ElMessage.success('已删除')
    })
    .catch(() => {})
}
</script>

<style scoped>
.attachment-uploader {
  width: 100%;
}
.att-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}
.att-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-base);
}
.att-thumb {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  border-radius: 4px;
  overflow: hidden;
}
.att-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.att-file-icon {
  font-size: 22px;
  color: var(--text-secondary);
}
.att-meta {
  flex: 1;
  min-width: 0;
}
.att-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.att-size {
  font-size: 12px;
}
.att-actions {
  flex-shrink: 0;
}
.att-uploader {
  margin-top: 4px;
}
</style>

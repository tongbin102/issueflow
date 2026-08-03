import request from './request'

/**
 * 数据管理（备份 / 恢复）API —— Phase10。
 *
 * 后端前缀 `/api/admin/data`（见 Constants.DATA_MANAGEMENT_API_PREFIX），
 * request 实例已带 `/api` baseURL，故此处路径统一以 `/admin/data` 开头。
 *
 * 权限码对照（后端 @PreAuthorize）：
 *   查看列表/详情/进度/配置 → system:data:view
 *   发起备份               → system:data:backup
 *   下载                   → system:data:download
 *   删除                   → system:data:delete
 *   上传                   → system:data:upload + system:data:restore
 *   恢复                   → system:data:restore
 *   修改配置               → system:data:config
 */

/** 备份类型枚举（与后端 BackupTypeEnum 对齐） */
export const BACKUP_TYPES = ['FULL', 'DB_ONLY', 'CONFIG_ONLY']

/** 备份来源枚举（与后端 BackupSourceEnum 对齐） */
export const BACKUP_SOURCES = ['MANUAL', 'AUTO', 'UPLOAD', 'PRE_RESTORE']

/** 任务状态枚举（与后端 TaskStatusEnum 对齐） */
export const TASK_STATUSES = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELED']

/**
 * 发起手动备份。
 * @param {{name?: string, type?: string, includeConfig?: boolean}} payload 备份参数
 * @returns {Promise<Object>} 任务初始进度 TaskProgressDTO
 */
export function createBackup(payload = {}) {
  return request.post('/admin/data/backups', {
    name: payload.name || '',
    type: payload.type || 'FULL',
    includeConfig: payload.includeConfig !== false
  })
}

/**
 * 分页查询备份列表。
 * @param {{page?: number, size?: number, backupType?: string, source?: string, status?: string, keyword?: string}} params 查询条件
 * @returns {Promise<Object>} PageResult<BackupListVO>
 */
export function fetchBackupList(params = {}) {
  return request.get('/admin/data/backups', {
    params: {
      page: params.page || 1,
      size: params.size || 10,
      backupType: params.backupType || undefined,
      source: params.source || undefined,
      status: params.status || undefined,
      keyword: params.keyword || undefined
    }
  })
}

/**
 * 查询备份详情（恢复确认弹窗使用）。
 * @param {number|string} id 备份记录 id
 * @returns {Promise<Object>} BackupDetailVO
 */
export function fetchBackupDetail(id) {
  return request.get(`/admin/data/backups/${id}`)
}

/**
 * 下载备份文件。
 *
 * 用 rawResponse 拿完整响应：后端在鉴权失败时返回的是 JSON 错误体而非 zip，
 * 必须靠 Content-Type 区分，否则会把一段 JSON 存成 .zip 让用户一头雾水。
 *
 * @param {number|string} id 备份记录 id
 * @returns {Promise<Object>} axios 原始响应（data 为 Blob）
 */
export function downloadBackup(id) {
  return request.get(`/admin/data/backups/${id}/download`, {
    responseType: 'blob',
    rawResponse: true
  })
}

/**
 * 删除备份（记录 + 磁盘文件）。
 * @param {number|string} id 备份记录 id
 * @returns {Promise<void>}
 */
export function deleteBackup(id) {
  return request.delete(`/admin/data/backups/${id}`)
}

/**
 * 从指定备份恢复数据。
 * @param {number|string} id 备份记录 id
 * @param {{preBackup?: boolean, remark?: string}} payload 恢复参数
 * @returns {Promise<Object>} 任务初始进度 TaskProgressDTO
 */
export function restoreBackup(id, payload = {}) {
  return request.post(`/admin/data/backups/${id}/restore`, {
    preBackup: payload.preBackup !== false,
    remark: payload.remark || ''
  })
}

/**
 * 上传备份包并（可选）立即恢复。
 *
 * 后端用 @RequestPart 接收两段：file（二进制）+ meta（JSON）。
 * meta 必须显式声明 Content-Type: application/json，
 * 否则 Spring 会按 text/plain 解析导致 415。
 *
 * @param {File} file 备份 zip 文件
 * @param {{name?: string, preBackup?: boolean, restoreNow?: boolean, remark?: string}} meta 上传参数
 * @param {(percent: number) => void} [onProgress] 上传进度回调（0-100）
 * @returns {Promise<Object>} 任务初始进度 TaskProgressDTO
 */
export function uploadAndRestore(file, meta = {}, onProgress) {
  const form = new FormData()
  form.append('file', file)
  const metaPayload = {
    name: meta.name || '',
    preBackup: meta.preBackup !== false,
    restoreNow: meta.restoreNow !== false,
    remark: meta.remark || ''
  }
  form.append('meta', new Blob([JSON.stringify(metaPayload)], { type: 'application/json' }))

  return request.post('/admin/data/backups/upload', form, {
    timeout: 0,
    onUploadProgress: (event) => {
      if (typeof onProgress !== 'function' || !event.total) return
      onProgress(Math.min(99, Math.round((event.loaded * 100) / event.total)))
    }
  })
}

/**
 * 读取数据管理配置（保留份数 / 天数 / 上传上限）。
 * @returns {Promise<Object>} DataManagementConfigDTO
 */
export function fetchDataConfig() {
  return request.get('/admin/data/config')
}

/**
 * 更新数据管理配置。
 * @param {{maxCopies: number, defaultDays: number, sizeLimitMB: number}} payload 新配置
 * @returns {Promise<Object>} 落库后的最新配置
 */
export function updateDataConfig(payload) {
  return request.put('/admin/data/config', payload)
}

/**
 * 查询任务进度（前端轮询）。
 * @param {string} taskId 任务号
 * @returns {Promise<Object>} TaskProgressDTO
 */
export function fetchTaskProgress(taskId) {
  return request.get(`/admin/data/tasks/${taskId}`)
}

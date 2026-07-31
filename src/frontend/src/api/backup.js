import request from './request'
import { formatDate } from '@/utils/format'

/**
 * 数据备份 API（Phase7 T8，对应后端 BackupController，前缀 /api/admin/backup）。
 *
 * 说明（ARCH §3.8 / §7.1）：
 *  - 权限码统一为 `system:backup:export`（estimate 与 export 共用）；
 *  - 范围 scope 仅 'ALL' | 'CORE'，格式 format 仅 'JSON' | 'SQL'（大写，与后端
 *    BackupReq 的 @Pattern 校验一致），前端**不可**传表名；
 *  - export 成功返回 application/octet-stream 二进制；**失败返回 HTTP 200 +
 *    application/json 错误体**，因此必须 rawResponse 拿到响应头，先判断
 *    Content-Type 再决定当作 blob 下载还是解析出 message 展示。
 */

/** 合法导出范围 */
export const BACKUP_SCOPES = ['ALL', 'CORE']

/** 合法导出格式 */
export const BACKUP_FORMATS = ['JSON', 'SQL']

/** 导出接口超时（大表流式导出耗时较久，5 分钟） */
const EXPORT_TIMEOUT = 300000

/**
 * 规范化范围值，非法值回落到 'ALL'。
 * @param {string} scope 范围
 * @returns {string} 'ALL' | 'CORE'
 */
function normalizeScope(scope) {
  const value = String(scope || '').toUpperCase()
  return BACKUP_SCOPES.includes(value) ? value : 'ALL'
}

/**
 * 规范化格式值，非法值回落到 'JSON'。
 * @param {string} format 格式
 * @returns {string} 'JSON' | 'SQL'
 */
function normalizeFormat(format) {
  const value = String(format || '').toUpperCase()
  return BACKUP_FORMATS.includes(value) ? value : 'JSON'
}

/**
 * 导出前预估（system:backup:export）。
 * @param {{scope?:string}} [params] 查询参数
 * @returns {Promise<{scope:string,tableCount:number,totalRows:number,
 *   tables:Array<{name:string,rows:number}>,suggestedFileName:string,
 *   warning:string,attachmentBinaryIncluded:boolean,excludedTables:string[]}>}
 */
export function estimateBackup(params = {}) {
  return request.get('/admin/backup/estimate', {
    params: { scope: normalizeScope(params.scope) }
  })
}

/**
 * 执行导出（system:backup:export）。
 *
 * 返回**完整 axios 响应**（rawResponse:true），调用方需自行判断
 * `response.headers['content-type']` / `response.data.type`。
 * @param {{scope?:string,format?:string}} [params] 导出参数
 * @returns {Promise<import('axios').AxiosResponse<Blob>>}
 */
export function exportBackup(params = {}) {
  const body = {
    scope: normalizeScope(params.scope),
    format: normalizeFormat(params.format)
  }
  return request.post('/admin/backup/export', body, {
    responseType: 'blob',
    rawResponse: true,
    timeout: EXPORT_TIMEOUT
  })
}

/**
 * 生成本地兜底文件名：backup_YYYY-MM-DD_HHMMSS.{json|sql}。
 * 仅在响应头缺失 Content-Disposition（如被代理剥离）时使用。
 * @param {string} [format='JSON'] 导出格式
 * @param {Date} [date=new Date()] 时间
 * @returns {string} 文件名
 */
export function buildBackupFileName(format = 'JSON', date = new Date()) {
  const ext = normalizeFormat(format) === 'SQL' ? 'sql' : 'json'
  return `backup_${formatDate(date, 'YYYY-MM-DD_HHmmss')}.${ext}`
}

/**
 * 从响应头 Content-Disposition 解析文件名，失败时返回兜底名。
 * 兼容 `filename*=UTF-8''xxx` 与 `filename="xxx"` 两种写法。
 * @param {import('axios').AxiosResponse} response axios 响应
 * @param {string} fallback 兜底文件名
 * @returns {string} 文件名
 */
export function resolveBackupFileName(response, fallback) {
  const headers = (response && response.headers) || {}
  const disposition = headers['content-disposition'] || headers['Content-Disposition'] || ''
  if (!disposition) return fallback
  const starMatch = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)
  if (starMatch && starMatch[1]) {
    try {
      return decodeURIComponent(starMatch[1].trim().replace(/^"|"$/g, ''))
    } catch (e) {
      return fallback
    }
  }
  const plainMatch = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition)
  if (plainMatch && plainMatch[1]) {
    try {
      return decodeURIComponent(plainMatch[1].trim())
    } catch (e) {
      return plainMatch[1].trim()
    }
  }
  return fallback
}

/**
 * 判断导出响应是否为「JSON 错误体」而非二进制备份文件。
 * @param {import('axios').AxiosResponse} response axios 响应
 * @returns {boolean} true 表示是错误体
 */
export function isBackupErrorResponse(response) {
  const headers = (response && response.headers) || {}
  const headerType = String(headers['content-type'] || headers['Content-Type'] || '')
  const blobType = String((response && response.data && response.data.type) || '')
  const type = `${headerType} ${blobType}`.toLowerCase()
  return type.includes('json')
}

/**
 * 读取 JSON 错误体 Blob 中的 message 字段。
 * @param {Blob} blob 响应体
 * @returns {Promise<string>} 错误信息（解析失败返回空串）
 */
export async function parseBackupErrorMessage(blob) {
  if (!blob || typeof blob.text !== 'function') return ''
  try {
    const text = await blob.text()
    if (!text) return ''
    const json = JSON.parse(text)
    return json.message || json.msg || json.error || ''
  } catch (e) {
    return ''
  }
}

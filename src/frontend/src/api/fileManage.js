import request from './request'

/**
 * 文件管理 API（Phase7 T6，对应后端 FileController，前缀 /api/admin/files）。
 *
 * 说明：
 *  - 列表为分页接口，返回 PageResult<FileRecordVO>：{ list, total, page, size }；
 *  - 上传走 multipart/form-data，字段名固定为 `file`（后端 @RequestParam("file")），
 *    业务类型由后端固定写入 bizType='MANUAL'，前端不再额外传参；
 *  - 下载 / 预览为二进制流，必须带 Bearer（走 request 实例的请求拦截器），
 *    因此统一用 axios responseType:'blob'，禁止用 <a href> 直链（会 401）。
 */

/**
 * 文件列表分页（file:list）。
 * @param {{page?:number,size?:number,keyword?:string,ext?:string,bizType?:string,startDate?:string,endDate?:string}} params 查询参数
 * @returns {Promise<{list:Array,total:number,page:number,size:number}>}
 */
export function pageFiles(params) {
  return request.get('/admin/files', { params })
}

/**
 * 手工上传文件（file:upload）。
 * @param {File} file 浏览器 File 对象
 * @param {(percent:number)=>void} [onProgress] 上传进度回调（0-100）
 * @returns {Promise<Object>} FileRecordVO
 */
export function uploadFile(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/files', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    onUploadProgress: (event) => {
      if (typeof onProgress !== 'function' || !event || !event.total) return
      onProgress(Math.round((event.loaded * 100) / event.total))
    }
  })
}

/**
 * 下载文件（file:list）：返回 Blob，由 utils/exportUtil.downloadBlob 触发保存。
 * @param {number|string} id 文件 id
 * @returns {Promise<Blob>}
 */
export function downloadFile(id) {
  return request.get(`/admin/files/${id}/download`, { responseType: 'blob', timeout: 120000 })
}

/**
 * 预览文件（file:list）：仅图片类可用，返回可直接塞进 <img src> 的 object URL。
 * 调用方需在关闭预览时 URL.revokeObjectURL 释放。
 * @param {number|string} id 文件 id
 * @returns {Promise<string>} object URL
 */
export async function previewFile(id) {
  const blob = await request.get(`/admin/files/${id}/preview`, { responseType: 'blob' })
  return URL.createObjectURL(blob)
}

/**
 * 删除文件（file:delete）：软删记录 + 物理删除，返回后端提示语。
 * @param {number|string} id 文件 id
 * @returns {Promise<string>}
 */
export function deleteFile(id) {
  return request.delete(`/admin/files/${id}`)
}

/**
 * 读取文件存储配置（file:config）。
 * @returns {Promise<{storageRoot:string,maxSizeMb:number,allowedExts:string,storageType:string,usedSize:number,fileCount:number,writable:boolean}>}
 */
export function getFileConfig() {
  return request.get('/admin/files/config')
}

/**
 * 保存文件存储配置（file:config）。
 * @param {{storageRoot:string,maxSizeMb:number,allowedExts:string,storageType:string}} data 配置
 * @returns {Promise<Object>} 保存后的配置视图
 */
export function saveFileConfig(data) {
  return request.put('/admin/files/config', data)
}

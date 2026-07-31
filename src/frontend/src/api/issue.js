import request from './request'

/**
 * 问题模块 API（合并 flow / attachment 相关接口）。
 * baseURL = /api，各方法路径为相对路径。
 */

// 分页 + 多条件筛选：GET /api/issues
// IssuePageReq 字段：page,size,status,severity,tag,version,assigneeId,reporterId,keyword,startDate,endDate
export function pageIssues(params) {
  return request.get('/issues', { params })
}

// 导出 Excel：GET /api/issues/export → xlsx 二进制流
// 参数与 pageIssues 一致（不含 page/size），后端按 5000 行上限截断。
export function exportIssues(params) {
  return request.get('/issues/export', { params, responseType: 'blob' })
}

// 详情：GET /api/issues/{id} → IssueDetailVO
export function getIssue(id) {
  return request.get(`/issues/${id}`)
}

// 新建：POST /api/issues（multipart/form-data，含 files）
export function createIssue(formData) {
  return request.post('/issues', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 编辑：PUT /api/issues/{id}
export function updateIssue(id, data) {
  return request.put(`/issues/${id}`, data)
}

// 删除：DELETE /api/issues/{id}
export function deleteIssue(id) {
  return request.delete(`/issues/${id}`)
}

// 操作历史：GET /api/issues/{id}/history
export function getHistory(id, params) {
  return request.get(`/issues/${id}/history`, { params })
}

// 状态流转：POST /api/issues/{id}/status {toStatus,remark}
export function changeStatus(id, payload) {
  return request.post(`/issues/${id}/status`, payload)
}

// 重开：POST /api/issues/{id}/reopen {remark}
export function reopenIssue(id, remark) {
  return request.post(`/issues/${id}/reopen`, { remark })
}

// 上传附件：POST /api/issues/{id}/attachments（multipart，字段名 files）
export function uploadAttachments(id, files) {
  const fd = new FormData()
  ;(files || []).forEach((f) => fd.append('files', f))
  return request.post(`/issues/${id}/attachments`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 下载附件：GET /api/attachments/{id}/download → Blob 流（fetch 携带 token）
export function downloadAttachment(id) {
  return request.get(`/attachments/${id}/download`, { responseType: 'blob' })
}

// 问题关联：获取前置/后置列表 GET /api/issues/{id}/relations
export function getRelations(id) {
  return request.get(`/issues/${id}/relations`)
}

// 问题关联：整体保存 PUT /api/issues/{id}/relations {predecessorIds, successorIds}
export function saveRelations(id, data) {
  return request.put(`/issues/${id}/relations`, data)
}

// 关联问题下拉选项 GET /api/issues/options?excludeId=
export function listIssueOptions(excludeId) {
  return request.get('/issues/options', {
    params: excludeId != null ? { excludeId } : {}
  })
}

// 图片预览：GET /api/attachments/{id}/preview → 返回 Blob 的 object URL
// 采用 fetch 携带 Authorization，避免 <img src> 直链无法带 token 导致 401。
// 调用方负责使用完毕后 URL.revokeObjectURL(url) 释放。
export async function previewAttachment(id) {
  const blob = await request.get(`/attachments/${id}/preview`, { responseType: 'blob' })
  return URL.createObjectURL(blob)
}

// 删除附件：DELETE /api/attachments/{id}
export function deleteAttachment(id) {
  return request.delete(`/attachments/${id}`)
}

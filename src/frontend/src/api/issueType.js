import request from './request'

/**
 * 问题类型 API（/api/issue-types，6 接口）
 */

// 管理列表（含停用项 + 引用计数）：GET /api/issue-types?keyword=&enabled=
export function listIssueTypes(params) {
  return request.get('/issue-types', { params })
}

// 下拉选项：GET /api/issue-types/options?includeDisabled=
// includeDisabled=false（默认）→ 仅启用项（新建/编辑表单）；true → 全量含停用（筛选下拉，Q6）
export function listIssueTypeOptions(includeDisabled = false) {
  return request.get('/issue-types/options', {
    params: includeDisabled ? { includeDisabled: true } : {}
  })
}

// 新增：POST /api/issue-types
export function createIssueType(data) {
  return request.post('/issue-types', data)
}

// 编辑：PUT /api/issue-types/{id}
export function updateIssueType(id, data) {
  return request.put(`/issue-types/${id}`, data)
}

// 启停切换：PUT /api/issue-types/{id}/status {enabled}
export function toggleIssueTypeStatus(id, enabled) {
  return request.put(`/issue-types/${id}/status`, { enabled })
}

// 删除（被引用时后端阻断）：DELETE /api/issue-types/{id}
export function deleteIssueType(id) {
  return request.delete(`/issue-types/${id}`)
}

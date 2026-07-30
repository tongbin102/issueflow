import request from './request'

/**
 * 模块管理 API（Phase 4 R1/R2/R5）。
 * baseURL = /api，控制器 @RequestMapping("/api/modules")。
 */

// 项目模块树（仅需登录）：GET /api/modules/tree?projectId=
export function listModuleTree(projectId) {
  return request.get('/modules/tree', { params: { projectId } })
}

// 新建模块：POST /api/modules
export function createModule(data) {
  return request.post('/modules', data)
}

// 编辑模块（仅 name/description）：PUT /api/modules/{id}
export function updateModule(id, data) {
  return request.put(`/modules/${id}`, data)
}

// 删除模块（级联软删子孙）：DELETE /api/modules/{id}
export function deleteModule(id) {
  return request.delete(`/modules/${id}`)
}

// 移动模块（即拖即存）：PUT /api/modules/{id}/move
export function moveModule(id, data) {
  return request.put(`/modules/${id}/move`, data)
}

// 批量删除（原子阻断）：POST /api/modules/batch-delete
export function batchDeleteModule(data) {
  return request.post('/modules/batch-delete', data)
}

// 批量移动：POST /api/modules/batch-move
export function batchMoveModule(data) {
  return request.post('/modules/batch-move', data)
}

// 设置依赖（全量替换 + 防环）：PUT /api/modules/{id}/dependencies
export function setModuleDependencies(id, dependsOnIds) {
  return request.put(`/modules/${id}/dependencies`, { dependsOnIds })
}

import request from './request'

/**
 * 角色管理模块 API（/api/roles）。
 */

// 角色列表：GET /api/roles
export function listRoles() {
  return request.get('/roles')
}

// 新建角色：POST /api/roles
export function createRole(data) {
  return request.post('/roles', data)
}

// 编辑角色：PUT /api/roles/{id}
export function updateRole(id, data) {
  return request.put(`/roles/${id}`, data)
}

// 删除角色：DELETE /api/roles/{id}
export function deleteRole(id) {
  return request.delete(`/roles/${id}`)
}

// 角色已分配权限码集合：GET /api/roles/{id}/permissions
export function getRolePermissions(id) {
  return request.get(`/roles/${id}/permissions`)
}

// 分配角色权限（整体替换）：PUT /api/roles/{id}/permissions {permissionCodes}
export function assignRolePermissions(id, permissionCodes) {
  return request.put(`/roles/${id}/permissions`, { permissionCodes })
}

// 刷新全部角色权限缓存：POST /api/roles/permissions/refresh
export function refreshPermissions() {
  return request.post('/roles/permissions/refresh')
}

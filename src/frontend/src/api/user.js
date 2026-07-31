import request from './request'

/**
 * 用户 + 角色 API。
 */

// 用户分页：GET /api/users
export function pageUsers(params) {
  return request.get('/users', { params })
}

// 新建用户：POST /api/users
export function createUser(data) {
  return request.post('/users', data)
}

// 编辑用户：PUT /api/users/{id}
export function updateUser(id, data) {
  return request.put(`/users/${id}`, data)
}

// 删除用户：DELETE /api/users/{id}
export function deleteUser(id) {
  return request.delete(`/users/${id}`)
}

// 用户下拉选项：GET /api/users/options（仅需登录，keyword 可选模糊匹配 realName/username）
export function listUserOptions(params) {
  return request.get('/users/options', { params })
}

// 用户已分配角色码：GET /api/users/{id}/roles → List<String>（Phase8 W3 #11，编辑回显兜底）
export function listUserRoles(id) {
  return request.get(`/users/${id}/roles`)
}

// 角色字典：GET /api/roles → List<Role>
export function listRoles() {
  return request.get('/roles')
}

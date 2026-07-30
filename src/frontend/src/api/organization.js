import request from './request'

/**
 * 组织模块 API。
 */

// 组织列表（扁平）：GET /api/organizations
export function listOrganizations() {
  return request.get('/organizations')
}

// 新建组织：POST /api/organizations
export function createOrganization(data) {
  return request.post('/organizations', data)
}

// 编辑组织：PUT /api/organizations/{id}
export function updateOrganization(id, data) {
  return request.put(`/organizations/${id}`, data)
}

// 删除组织：DELETE /api/organizations/{id}
export function deleteOrganization(id) {
  return request.delete(`/organizations/${id}`)
}

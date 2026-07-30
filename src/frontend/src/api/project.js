import request from './request'

/**
 * 项目模块 API。
 * baseURL = /api，各方法路径为相对路径。
 */

// 项目分页：GET /api/projects
export function pageProjects(params) {
  return request.get('/projects', { params })
}

// 新建项目：POST /api/projects
export function createProject(data) {
  return request.post('/projects', data)
}

// 编辑项目：PUT /api/projects/{id}
export function updateProject(id, data) {
  return request.put(`/projects/${id}`, data)
}

// 删除项目：DELETE /api/projects/{id}
export function deleteProject(id) {
  return request.delete(`/projects/${id}`)
}

// 项目下拉选项：GET /api/projects/options（仅需登录，含 status）
export function listProjectOptions() {
  return request.get('/projects/options')
}

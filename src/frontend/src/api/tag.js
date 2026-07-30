import request from './request'

/**
 * 分类标签字典 API。
 */

// 标签列表：GET /api/tags
export function listTags() {
  return request.get('/tags')
}

// 新建标签：POST /api/tags
export function createTag(data) {
  return request.post('/tags', data)
}

// 编辑标签：PUT /api/tags/{id}
export function updateTag(id, data) {
  return request.put(`/tags/${id}`, data)
}

// 删除标签：DELETE /api/tags/{id}
export function deleteTag(id) {
  return request.delete(`/tags/${id}`)
}

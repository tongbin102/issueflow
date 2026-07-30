import request from './request'

/**
 * 菜单模块 API。
 */

// 菜单列表（按端过滤，type 可空）：GET /api/menus?type=
export function listMenus(type) {
  return request.get('/menus', { params: type != null ? { type } : {} })
}

// 侧栏菜单树（按端）：GET /api/menus/sidebar?type=
export function getSidebarMenus(type) {
  return request.get('/menus/sidebar', { params: { type } })
}

// 新建菜单：POST /api/menus
export function createMenu(data) {
  return request.post('/menus', data)
}

// 编辑菜单：PUT /api/menus/{id}
export function updateMenu(id, data) {
  return request.put(`/menus/${id}`, data)
}

// 删除菜单：DELETE /api/menus/{id}
export function deleteMenu(id) {
  return request.delete(`/menus/${id}`)
}

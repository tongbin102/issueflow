import request from './request'

/**
 * 菜单模块 API。
 */

// 菜单列表（扁平）：GET /api/menus
export function listMenus() {
  return request.get('/menus')
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

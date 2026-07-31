import request from './request'

/**
 * 字典配置 API（/api/dicts，10 接口）
 * 类型 5 个 + 选项 5 个（其中 /options 为全站下拉唯一数据源）。
 *
 * 约定：wire 层一律使用 typeCode（字典类型编码），落库列为 dict_code；
 * 选项以 code（item_code）作为业务取值，issue.source 存的即为该 code。
 */

// 类型列表：GET /api/dicts/types?keyword=&enabled=
export function listDictTypes(params) {
  return request.get('/dicts/types', { params })
}

// 新增类型：POST /api/dicts/types
export function createDictType(data) {
  return request.post('/dicts/types', data)
}

// 编辑类型（code 后端忽略）：PUT /api/dicts/types/{id}
export function updateDictType(id, data) {
  return request.put(`/dicts/types/${id}`, data)
}

// 类型启停：PUT /api/dicts/types/{id}/status {enabled}
export function toggleDictTypeStatus(id, enabled) {
  return request.put(`/dicts/types/${id}/status`, { enabled })
}

// 删除类型（预设类型/仍有选项时后端阻断）：DELETE /api/dicts/types/{id}
export function deleteDictType(id) {
  return request.delete(`/dicts/types/${id}`)
}

// 选项列表：GET /api/dicts/items?typeCode=&keyword=&enabled=
export function listDictItems(params) {
  return request.get('/dicts/items', { params })
}

// 新增选项：POST /api/dicts/items（body 含 typeCode）
export function createDictItem(data) {
  return request.post('/dicts/items', data)
}

// 编辑选项（预设项 code 后端静默忽略）：PUT /api/dicts/items/{id}
export function updateDictItem(id, data) {
  return request.put(`/dicts/items/${id}`, data)
}

// 选项启停：PUT /api/dicts/items/{id}/status {enabled}
export function toggleDictItemStatus(id, enabled) {
  return request.put(`/dicts/items/${id}/status`, { enabled })
}

// 删除选项（预设项/被引用项后端阻断）：DELETE /api/dicts/items/{id}
export function deleteDictItem(id) {
  return request.delete(`/dicts/items/${id}`)
}

/**
 * 全站下拉唯一数据源：GET /api/dicts/options?typeCode=&includeDisabled=
 * @param {string} typeCode 字典类型编码，如 ISSUE_SOURCE
 * @param {boolean} includeDisabled false（默认）仅启用项；true 含停用项（置底）
 * @returns {Promise<Array<{id:number,name:string,code:string,enabled:boolean,extra:string}>>}
 */
export function getDictOptions(typeCode, includeDisabled = false) {
  const params = { typeCode }
  if (includeDisabled) params.includeDisabled = true
  return request.get('/dicts/options', { params })
}

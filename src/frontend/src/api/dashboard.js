import request from './request'

/**
 * 看板 API。
 * DashboardQueryReq：start,end,version（入参 yyyy-MM-dd 字符串）。
 * 返回 DashboardVO（趋势 / 状态分布 / 平均解决周期 / 解决率 / 严重占比）。
 */

// 概览聚合：GET /api/dashboard/overview
export function overview(params) {
  return request.get('/dashboard/overview', { params })
}

// Excel 导出：GET /api/dashboard/export → Blob 流
export function exportExcel(params) {
  return request.get('/dashboard/export', { params, responseType: 'blob' })
}

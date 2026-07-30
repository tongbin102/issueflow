import request from './request'

/**
 * 系统数据 API（Phase 5 R7）。
 */

// 数据初始化（仅 ADMIN + system:reset，需确认文本 RESET）：POST /api/system/data/reset
export function resetSystemData(confirmText) {
  return request.post('/system/data/reset', { confirmText })
}

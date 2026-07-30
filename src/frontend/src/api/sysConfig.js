import request from './request'

/**
 * 系统配置 API：主题 / 流程 / 菜单配置读写。
 * GET /api/sys/config 返回 Map；PUT /api/sys/config 接收 {configKey,configValue}。
 * 流程开关：GET/PUT /api/flow/config。
 */

// 读取全部配置：GET /api/sys/config → Map
export function getConfig() {
  return request.get('/sys/config')
}

// 写入单个配置：PUT /api/sys/config {configKey,configValue}
export function setConfig(payload) {
  return request.put('/sys/config', payload)
}

// 读取流程开关：GET /api/flow/config → {rejectEnabled,reopenEnabled}
export function getFlowConfig() {
  return request.get('/flow/config')
}

// 写入流程开关：PUT /api/flow/config {rejectEnabled,reopenEnabled}
export function setFlowConfig(payload) {
  return request.put('/flow/config', payload)
}

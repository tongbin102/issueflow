import request from './request'

/**
 * 流程定义 API（Phase 5 R2）。
 * baseURL = /api，控制器 @RequestMapping("/api/flow/definition")。
 * 数据模型为节点 id 驱动：流转以 fromNodeId/toNodeId 引用 flow_node.id。
 */

// 流程图（节点 + 流转边）：GET /api/flow/definition/graph
export function getFlowGraph() {
  return request.get('/flow/definition/graph')
}

// 新建流程节点：POST /api/flow/definition/nodes
export function createFlowNode(data) {
  return request.post('/flow/definition/nodes', data)
}

// 编辑流程节点：PUT /api/flow/definition/nodes/{id}
export function updateFlowNode(id, data) {
  return request.put(`/flow/definition/nodes/${id}`, data)
}

// 删除流程节点：DELETE /api/flow/definition/nodes/{id}
export function deleteFlowNode(id) {
  return request.delete(`/flow/definition/nodes/${id}`)
}

// 批量保存节点坐标：PUT /api/flow/definition/nodes/positions
// positions: [{ id, posX, posY }]
export function saveFlowNodePositions(positions) {
  return request.put('/flow/definition/nodes/positions', { positions })
}

// 新建流转规则：POST /api/flow/definition/transitions
export function createFlowTransition(data) {
  return request.post('/flow/definition/transitions', data)
}

// 编辑流转规则：PUT /api/flow/definition/transitions/{id}
export function updateFlowTransition(id, data) {
  return request.put(`/flow/definition/transitions/${id}`, data)
}

// 删除流转规则：DELETE /api/flow/definition/transitions/{id}
export function deleteFlowTransition(id) {
  return request.delete(`/flow/definition/transitions/${id}`)
}

// 恢复默认流程（清空两表→重灌 5 节点 + 6 流转）：POST /api/flow/definition/reset-default
export function resetFlowDefault() {
  return request.post('/flow/definition/reset-default')
}

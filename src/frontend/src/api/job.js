import request from './request'

/**
 * 定时任务 API（Phase7 T7，对应后端 ScheduledTaskController，前缀 /api/admin/jobs）。
 *
 * 安全约定：jobKey 必须来自 {@link getJobOptions} 返回的后端白名单，
 * 前端不得拼接 / 透传任意类名（后端亦会硬拦截）。
 */

/**
 * 任务分页列表（job:list）。
 * @param {{page?:number,size?:number,keyword?:string,status?:number}} params 查询参数
 * @returns {Promise<{list:Array,total:number,page:number,size:number}>}
 */
export function pageJobs(params) {
  return request.get('/admin/jobs', { params })
}

/**
 * 可选执行目标下拉（job:list）：后端 jobRegistry 白名单。
 * @returns {Promise<Array<{jobKey:string,displayName:string}>>}
 */
export function getJobOptions() {
  return request.get('/admin/jobs/options')
}

/**
 * 新增任务（job:create）。
 * @param {{taskName:string,taskGroup:string,jobKey:string,cron:string,params:string,status:number,description:string}} data 任务
 * @returns {Promise<number>} 新任务 id
 */
export function createJob(data) {
  return request.post('/admin/jobs', data)
}

/**
 * 编辑任务（job:update）。
 * @param {number|string} id 任务 id
 * @param {Object} data 任务
 * @returns {Promise<void>}
 */
export function updateJob(id, data) {
  return request.put(`/admin/jobs/${id}`, data)
}

/**
 * 删除任务（job:delete）。
 * @param {number|string} id 任务 id
 * @returns {Promise<void>}
 */
export function deleteJob(id) {
  return request.delete(`/admin/jobs/${id}`)
}

/**
 * 启停切换（job:update）：后端入参为通用 StatusToggleReq {enabled}。
 * @param {number|string} id 任务 id
 * @param {boolean} enabled true 恢复运行 / false 暂停
 * @returns {Promise<void>}
 */
export function toggleJobStatus(id, enabled) {
  return request.put(`/admin/jobs/${id}/status`, { enabled })
}

/**
 * 立即执行一次（job:run，triggerType=MANUAL）。
 * @param {number|string} id 任务 id
 * @returns {Promise<void>}
 */
export function runJobOnce(id) {
  return request.post(`/admin/jobs/${id}/run`)
}

/**
 * 任务执行日志分页（job:list）。
 * @param {number|string} id 任务 id
 * @param {{page?:number,size?:number}} params 分页参数
 * @returns {Promise<{list:Array,total:number,page:number,size:number}>}
 */
export function pageJobLogs(id, params) {
  return request.get(`/admin/jobs/${id}/logs`, { params })
}

import request from './request'

/**
 * 个人中心 API（/api/profile，Phase 7 T5）。
 *
 * 硬约定（ARCH §7.8）：所有写接口**不传 userId**，后端一律取
 * SecurityUtils.getCurrentUserId()，从签名层面消灭越权可能。
 * 唯一带 userId 的是头像只读端点 —— 展示他人头像是正常需求。
 */

/**
 * 当前用户资料：GET /api/profile
 * @returns {Promise<Object>} ProfileVO：email/phone 为脱敏值，emailRaw/phoneRaw 为编辑回填原值
 */
export function getProfile() {
  return request.get('/profile')
}

/**
 * 编辑当前用户资料：PUT /api/profile
 * @param {Object} data ProfileUpdateReq{nickname, realName, email, phone}
 * @returns {Promise<Object>} 更新后的 ProfileVO
 */
export function updateProfile(data) {
  return request.put('/profile', data)
}

/**
 * 上传头像：POST /api/profile/avatar（multipart/form-data）
 * @param {File} file 图片文件
 * @returns {Promise<string>} 头像相对路径（写入 user.avatar）
 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/profile/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 读取头像字节流：GET /api/profile/avatar/{userId}
 *
 * 该端点需 Bearer token，无法直接写进 <img src>，因此走 axios 取 Blob 后
 * 由组件 URL.createObjectURL 渲染（响应拦截器对非 Result 结构原样返回）。
 * @param {number|string} userId 目标用户 id
 * @returns {Promise<Blob>} 图片二进制
 */
export function fetchAvatarBlob(userId) {
  return request.get(`/profile/avatar/${userId}`, { responseType: 'blob' })
}

/**
 * 修改密码：PUT /api/profile/password
 *
 * 成功后后端会把当前 token 加入黑名单（强制登出），前端需提示并跳登录页。
 * @param {Object} data PasswordChangeReq{oldPassword, newPassword, confirmPassword}
 * @returns {Promise<void>}
 */
export function changePassword(data) {
  return request.put('/profile/password', data)
}

/**
 * 变更手机 / 邮箱绑定：PUT /api/profile/binding（需当前密码二次确认）
 * @param {Object} data BindingChangeReq{type:'PHONE'|'EMAIL', value, currentPassword}
 * @returns {Promise<Object>} 更新后的 ProfileVO
 */
export function changeBinding(data) {
  return request.put('/profile/binding', data)
}

/**
 * 活动记录（登录日志 + 本人 issue 操作历史归并时间线）：GET /api/profile/activities
 * @param {Object} params ActivityPageReq{page, size, type:'ALL'|'LOGIN'|'ISSUE', startDate, endDate}
 * @returns {Promise<Object>} PageResult<ActivityVO>{list, total, page, size}
 */
export function pageActivities(params) {
  return request.get('/profile/activities', { params })
}

/**
 * 本人登录日志分页：GET /api/profile/login-logs
 * @param {Object} params {page, size}
 * @returns {Promise<Object>} PageResult<LoginLogVO>{list, total, page, size}
 */
export function pageLoginLogs(params) {
  return request.get('/profile/login-logs', { params })
}

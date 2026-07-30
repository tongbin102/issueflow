import request from './request'

// 登录：POST /api/auth/login → LoginVO{token,userInfo,roles}（已解包到 data）
export function login(credentials) {
  return request.post('/auth/login', credentials)
}

// 登出：POST /api/auth/logout
export function logout() {
  return request.post('/auth/logout')
}

// 当前用户信息：GET /api/auth/info → LoginVO
export function getInfo() {
  return request.get('/auth/info')
}

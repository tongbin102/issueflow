import request from './request'

/**
 * 权限目录模块 API（/api/permissions）。
 * 返回全部权限码目录，供角色授权页渲染复选框（只读目录）。
 */

// 权限目录：GET /api/permissions → List<PermissionVO>
export function listPermissions() {
  return request.get('/permissions')
}

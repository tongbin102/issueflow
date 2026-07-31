import request from './request'

/**
 * 网站设置 API
 */

// 读取公开 site.* 配置（公开接口，登录页可用，不含敏感键）：GET /api/site/config
export function getSiteConfig() {
  return request.get('/site/config')
}

// Phase8 W1 #2：管理端读取全部 site.* 配置（含 site.default_password）：GET /api/admin/site/config
// 需登录 + site:config:update 权限，供「系统设置」页回填表单使用
export function getAdminSiteConfig() {
  return request.get('/admin/site/config')
}

// 保存网站设置（ADMIN，site:config:update）：PUT /api/admin/site/config
export function saveSiteConfig(data) {
  return request.put('/admin/site/config', data)
}

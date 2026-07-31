import request from './request'

/**
 * 网站设置 API
 */

// 读取全部 site.* 配置（公开接口，登录页可用）：GET /api/site/config
export function getSiteConfig() {
  return request.get('/site/config')
}

// 保存网站设置（ADMIN，site:config:update）：PUT /api/admin/site/config
export function saveSiteConfig(data) {
  return request.put('/admin/site/config', data)
}

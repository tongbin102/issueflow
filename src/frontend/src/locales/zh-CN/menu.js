/**
 * 菜单名文案（zh-CN）：按 path 映射（utils/i18nEnum.js 的 MENU_KEY_BY_PATH）
 * 数据库 menu.name 无命中时回退原值
 */
export default {
  user: {
    dashboard: '工作台',
    issueManage: '问题管理',
    myIssues: '我的问题',
    stats: '个人看板'
  },
  admin: {
    overview: '概览',
    issues: '问题管理',
    issueTypes: '问题类型',
    projectGroup: '项目管理',
    projects: '项目配置',
    modules: '模块配置',
    flowGroup: '流程管理',
    flowMonitor: '流程监控',
    flowConfig: '流程配置',
    system: '系统管理',
    users: '用户管理',
    organizations: '组织管理',
    menus: '菜单管理',
    roles: '角色管理',
    siteSettings: '网站设置',
    systemSettings: '系统设置'
  }
}

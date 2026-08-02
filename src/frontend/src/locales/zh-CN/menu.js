/**
 * 菜单名文案（zh-CN）：按 path 映射（utils/i18nEnum.js 的 MENU_KEY_BY_PATH）
 * 数据库 menu.name 无命中时回退原值
 */
export default {
  user: {
    dashboard: '工作台',
    issueManage: '问题管理',
    myIssues: '我的问题',
    stats: '个人看板',
    profile: '个人中心'
  },
  admin: {
    overview: '概览',
    issues: '问题管理',
    business: '业务管理',
    issueList: '问题列表',
    dict: '字典配置',
    // Phase9：issueTypes（问题类型）键下线，问题类型改由字典 ISSUE_TYPE 维护
    fieldConfigs: '字段配置',
    infra: '基础设施',
    infraFile: '文件管理',
    infraFileConfig: '文件配置',
    infraFileList: '文件列表',
    infraConfig: '配置管理',
    infraRedis: 'Redis 监控',
    infraJob: '定时任务',
    projectGroup: '项目管理',
    projects: '项目配置',
    flowGroup: '流程管理',
    flowMonitor: '流程监控',
    flowConfig: '流程配置',
    system: '系统管理',
    users: '用户管理',
    organizations: '组织管理',
    menus: '菜单管理',
    roles: '角色管理',
    // Phase8 W1 #2：菜单文案调整（路由 path / component 不变）
    // /admin/system/site  → 系统设置（原「网站设置」）
    // /admin/system/settings → 备份设置（原「系统设置」）
    siteSettings: '系统设置',
    systemSettings: '备份设置'
  }
}

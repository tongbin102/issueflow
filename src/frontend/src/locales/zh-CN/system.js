/**
 * 系统设置页文案（zh-CN，复用既有系统设置页）
 */
export default {
  title: '系统设置',
  group: {
    basic: '基础设置',
    security: '安全设置',
    flow: '流程开关',
    data: '数据管理'
  },
  field: {
    siteName: '站点名称',
    logo: '站点 Logo',
    sessionTimeout: '会话超时(分)',
    passwordPolicy: '密码策略',
    reopenEnabled: '允许重开',
    rejectEnabled: '允许验证回退'
  },
  msg: {
    saveSuccess: '设置已保存'
  },
  reset: {
    title: '数据初始化',
    desc: '清空所有业务数据（问题、项目、模块、组织、非 admin 用户等），保留角色、权限、菜单、系统配置与流程定义。适用于试运行结束后正式上线前的一次性清库。该操作不可撤销，请谨慎执行。',
    button: '初始化数据',
    doneTitle: '数据初始化已完成，各表清理条数如下：',
    countUnit: '{count} 条',
    table: {
      issue_attachment: '问题附件',
      issue_history: '问题历史',
      issue_relation: '问题关联',
      issue: '问题',
      tag: '标签',
      module_dependency: '模块依赖',
      module: '模块',
      project: '项目',
      organization: '组织',
      user: '用户（除 admin）'
    }
  }
}

/**
 * 系统设置页文案（zh-CN，复用既有系统设置页）
 */
export default {
  // Phase10 需求三：页面标题 title（原「备份设置」）随 SystemSettings.vue 一并下线，
  // 页面职责整体迁移到数据管理页（dataManagement.title）。
  // 本文件保留 reset.table.* 表名文案 —— 数据管理页展示初始化结果时仍在复用。
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
    // Phase10 需求三：数据初始化抽屉（DataResetDrawer）文案，原为硬编码中文，
    // 随入口迁入数据管理页一并补齐 zh/en 成对。
    alertTitle: '高危操作：数据初始化不可撤销！',
    alertDesc: '执行后以下业务数据将被永久清除且无法恢复，请务必确认已做好备份。',
    clearTitle: '将被清除',
    keepTitle: '将被保留',
    confirmTip: '请输入 {keyword} 以确认执行：',
    confirmPlaceholder: '请输入 {keyword}',
    confirmButton: '确认清除',
    success: '数据初始化完成',
    clearItems: {
      issue: '问题',
      issueHistory: '问题历史',
      attachment: '附件',
      issueRelation: '问题关联',
      tagRelation: '标签关联',
      project: '项目',
      module: '模块',
      moduleDependency: '模块依赖',
      organization: '组织',
      user: '除 admin 外的用户'
    },
    keepItems: {
      role: '角色',
      permission: '权限',
      menu: '菜单',
      config: '系统配置',
      flow: '流程定义',
      admin: 'admin 账号'
    },
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

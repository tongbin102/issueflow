/**
 * 角色管理页文案（zh-CN）
 */
export default {
  page: {
    title: '角色管理'
  },
  col: {
    name: '角色名称',
    code: '角色编码',
    description: '描述',
    userCount: '用户数',
    permissionCount: '权限数',
    builtin: '内置'
  },
  action: {
    create: '新建角色',
    refreshCache: '刷新权限缓存',
    assignPerm: '分配权限'
  },
  drawer: {
    createTitle: '新增角色',
    editTitle: '编辑角色'
  },
  placeholder: {
    code: '如 CUSTOM_ROLE',
    searchPerm: '搜索权限名称 / 编码'
  },
  permModule: {
    dashboard: '仪表盘',
    issue: '问题',
    project: '项目',
    user: '用户',
    organization: '组织',
    menu: '菜单',
    role: '角色',
    settings: '设置',
    flow: '流程',
    system: '系统',
    other: '其他'
  },
  form: {
    name: '角色名称',
    code: '角色编码',
    description: '描述',
    permissions: '权限'
  },
  tree: {
    selectAll: '全选',
    expandAll: '展开全部',
    collapseAll: '收起全部'
  },
  msg: {
    codeRequired: '请输入角色码',
    nameRequired: '请输入名称',
    cacheRefreshed: '权限缓存已刷新',
    permSaved: '权限已保存',
    builtinTip: '内置角色权限可调整，但角色码不可修改、角色不可删除。',
    createSuccess: '角色已创建',
    updateSuccess: '角色已更新',
    deleteSuccess: '角色已删除',
    deleteConfirm: '确认删除角色「{name}」？',
    builtinProtected: '内置角色受保护，禁止删除或修改角色码'
  }
}

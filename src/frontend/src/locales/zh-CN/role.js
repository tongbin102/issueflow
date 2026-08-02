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
    assignPerm: '分配权限',
    retry: '重试',
    reload: '重新加载',
    reset: '重置'
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
  /** 分配权限抽屉：前后台分段控件 */
  tab: {
    frontend: '前台端',
    backend: '后台端'
  },
  tree: {
    selectAll: '全选',
    expandAll: '展开全部',
    collapseAll: '收起全部',
    uncategorized: '未分类',
    uncategorizedTip: '该权限未标记端维度，已归入后台端',
    invertSelect: '反选',
    selectAllVisible: '全选可见项',
    selectGroup: '全选本组',
    clearGroup: '清空本组',
    matchCount: '命中 {count} 项'
  },
  /** 分配权限抽屉：右侧已选清单 */
  selected: {
    title: '已选权限',
    count: '已选 {count} 项',
    distribution: '前台 {front} / 后台 {back}',
    scopeCurrent: '当前端',
    scopeAll: '全部',
    remove: '移除',
    clear: '清空',
    clearConfirm: '确定清空「{scope}」已选的 {count} 项权限？',
    stale: '已失效',
    staleTip: '该权限已不在权限目录中，保存后仍会保留，如需清理请手动移除',
    staleCount: '含 {count} 项已失效权限'
  },
  /** 分配权限抽屉：保存前的变更确认 */
  diff: {
    title: '确认权限变更',
    subtitle: '本次将为角色「{role}」更新权限，保存后立即生效',
    added: '新增 {count} 项',
    removed: '移除 {count} 项',
    total: '变更后合计：{count} 项',
    viewAll: '查看全部',
    confirm: '确认保存',
    cancel: '再想想'
  },
  empty: {
    noPermission: '暂无可分配的权限',
    noPermissionInTab: '当前端暂无权限项',
    noSearchResult: '未找到匹配「{keyword}」的权限',
    clearSearch: '清除搜索'
  },
  tip: {
    manyItems: '权限项较多，建议使用搜索定位'
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
    builtinProtected: '内置角色受保护，禁止删除或修改角色码',
    noChanges: '当前无任何变更',
    permSaveFailed: '权限保存失败，请重试',
    permSaveTimeout: '保存超时，请检查网络后重试',
    permSaveFailedMulti: '多次保存失败，建议稍后再试或联系管理员',
    loadPermFailed: '权限数据加载失败',
    discardConfirm: '当前有未保存的权限变更，确定放弃吗？'
  }
}

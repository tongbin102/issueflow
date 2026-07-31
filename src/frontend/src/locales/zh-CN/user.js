/**
 * 用户管理页文案（zh-CN）
 */
export default {
  page: {
    title: '用户管理'
  },
  col: {
    username: '账号',
    realName: '姓名',
    email: '邮箱',
    phone: '手机',
    role: '角色',
    org: '组织',
    leader: '上级领导',
    status: '状态',
    createdAt: '创建时间'
  },
  drawer: {
    createTitle: '新增用户',
    editTitle: '编辑用户'
  },
  form: {
    username: '账号',
    realName: '姓名',
    role: '角色',
    roles: '角色',
    org: '组织',
    leader: '上级领导',
    password: '密码',
    status: '状态'
  },
  placeholder: {
    selectRole: '请选择角色',
    selectRoles: '请选择角色（可多选）',
    selectOrg: '请选择组织',
    selectLeader: '搜索并选择上级领导（可空）'
  },
  tip: {
    primaryRole: '首个选中的角色为主角色，权限按全部角色取并集'
  },
  action: {
    create: '新建用户',
    resetPwd: '重置密码'
  },
  msg: {
    rolesRequired: '请至少选择一个角色',
    leaderSelf: '上级领导不能设置为自己',
    createSuccess: '用户已创建',
    updateSuccess: '用户已更新',
    deleteSuccess: '用户已删除',
    deleteConfirm: '确认删除用户「{name}」？',
    resetPwdSuccess: '密码已重置',
    resetPwdConfirm: '确认重置「{name}」的密码？'
  }
}

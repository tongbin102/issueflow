/**
 * 问题类型管理页文案（zh-CN）
 */
export default {
  page: {
    title: '问题类型'
  },
  col: {
    name: '类型名称',
    code: '编码',
    description: '描述',
    sort: '排序',
    status: '状态',
    issueCount: '引用数',
    updatedAt: '更新时间',
    actions: '操作'
  },
  form: {
    name: '类型名称',
    code: '类型编码',
    description: '描述',
    sort: '排序',
    status: '状态'
  },
  drawer: {
    createTitle: '新增类型',
    editTitle: '编辑类型'
  },
  placeholder: {
    name: '请输入类型名称',
    code: '大写字母开头，如 BUG',
    description: '请输入描述'
  },
  rules: {
    nameRequired: '请输入类型名称',
    codeRequired: '请输入类型编码',
    codePattern: '编码须大写字母开头，仅含大写字母、数字与下划线'
  },
  msg: {
    createSuccess: '类型已创建',
    updateSuccess: '类型已更新',
    deleteSuccess: '类型已删除',
    deleteConfirm: '确认删除类型「{name}」？',
    codeExists: '类型编码已存在',
    deleteInUse: '该类型下存在 {count} 个问题，无法删除，可改为停用',
    switchToDisabled: '已停用',
    switchToEnabled: '已启用'
  }
}

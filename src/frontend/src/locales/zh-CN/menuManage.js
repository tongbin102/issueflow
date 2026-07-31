/**
 * 菜单管理页文案（zh-CN）
 * 注意：在 locales/{lang}/index.js 中与 menu.js（菜单名映射）合并到同一 `menu` 根命名空间，
 * 二者子命名空间不冲突（user/admin vs page/col/form/type/placeholder/msg）
 */
export default {
  page: {
    title: '菜单管理'
  },
  col: {
    name: '菜单名称',
    path: '路由路径',
    icon: '图标',
    parent: '上级',
    sort: '排序',
    type: '类型',
    permission: '权限标识'
  },
  form: {
    name: '菜单名称',
    path: '路由路径',
    icon: '图标',
    parent: '上级',
    sort: '排序',
    type: '菜单类型',
    permission: '权限标识'
  },
  type: {
    catalog: '目录',
    menu: '菜单',
    button: '按钮',
    front: '前台',
    admin: '后台'
  },
  placeholder: {
    selectParent: '请选择上级'
  },
  msg: {
    createSuccess: '菜单已创建',
    updateSuccess: '菜单已更新',
    deleteSuccess: '菜单已删除',
    deleteConfirm: '确认删除菜单「{name}」？'
  }
}

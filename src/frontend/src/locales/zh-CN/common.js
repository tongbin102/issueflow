/**
 * 通用文案（zh-CN）：按钮动作 / 状态 / 通用字段 / 消息 / 占位符 / 分页
 * key 规范：common.{group}.{semantic}（见 ARCH_phase6 §七.1）
 */
export default {
  action: {
    save: '保存',
    cancel: '取消',
    submit: '提交',
    confirm: '确定',
    close: '关闭',
    create: '新增',
    edit: '编辑',
    delete: '删除',
    view: '查看',
    reset: '重置',
    search: '查询',
    refresh: '刷新',
    export: '导出',
    expandAll: '展开全部',
    collapseAll: '收起全部',
    restoreDefault: '恢复默认',
    fullscreen: '全屏',
    exitFullscreen: '退出全屏',
    operation: '操作',
    back: '返回',
    detail: '详情',
    upload: '上传',
    download: '下载',
    enable: '启用',
    disable: '停用',
    logout: '退出登录',
    // Phase9 T8：空状态 / 卡片流通用动作
    retry: '重试',
    clearFilter: '重置筛选',
    viewAll: '查看全部',
    more: '更多'
  },
  status: {
    enabled: '启用',
    disabled: '停用',
    all: '全部'
  },
  field: {
    createdAt: '创建时间',
    updatedAt: '更新时间',
    remark: '备注',
    sort: '排序',
    status: '状态',
    keyword: '关键字',
    description: '描述',
    name: '名称',
    code: '编码',
    dateRange: '时间范围',
    startDate: '开始日期',
    endDate: '结束日期'
  },
  msg: {
    saveSuccess: '保存成功',
    createSuccess: '新增成功',
    updateSuccess: '更新成功',
    deleteSuccess: '删除成功',
    deleteConfirm: '确认删除「{name}」？',
    operationSuccess: '操作成功',
    noData: '暂无数据',
    loading: '加载中…',
    required: '此项为必填',
    tip: '提示',
    warning: '警告',
    loadFailed: '加载失败'
  },
  // Phase9 T8：IfEmptyState 三类场景文案（scene = empty | noResult | error）
  empty: {
    emptyTitle: '暂无数据',
    emptyDesc: '当前还没有可展示的内容',
    noResultTitle: '没有匹配的结果',
    noResultDesc: '试试调整或重置筛选条件',
    errorTitle: '加载失败',
    errorDesc: '网络异常或服务暂时不可用，请稍后重试'
  },
  placeholder: {
    input: '请输入',
    select: '请选择',
    search: '输入关键字搜索'
  },
  pager: {
    total: '共 {total} 条'
  }
}

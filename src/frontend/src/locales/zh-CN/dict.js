/**
 * 字典配置页文案（zh-CN）
 * value.* 为系统预设项名映射：t('dict.value.' + typeCode + '.' + code)，未命中回退数据库 name
 */
export default {
  page: {
    title: '字典配置',
    typePanel: '字典类型',
    itemPanel: '字典选项',
    emptyType: '请先选择左侧字典类型'
  },
  col: {
    name: '名称',
    code: '编码',
    description: '描述',
    sort: '排序',
    status: '状态',
    itemCount: '选项数',
    refCount: '引用数',
    updatedAt: '更新时间',
    actions: '操作'
  },
  form: {
    typeName: '类型名称',
    typeCode: '类型编码',
    itemName: '选项名称',
    itemCode: '选项编码',
    belongType: '所属类型',
    description: '描述',
    sort: '排序',
    status: '状态'
  },
  tab: {
    basic: '基本信息',
    desc: '详细描述',
    config: '配置选项',
    nav: '分组导航'
  },
  drawer: {
    createType: '新增类型',
    editType: '编辑类型',
    createItem: '新增选项',
    editItem: '编辑选项'
  },
  action: {
    createType: '新增类型',
    createItem: '新增选项'
  },
  placeholder: {
    typeName: '请输入类型名称',
    typeCode: '大写字母开头，如 ISSUE_SOURCE',
    itemName: '请输入选项名称',
    itemCode: '大写字母开头，如 MANUAL',
    description: '请输入描述',
    selectType: '请选择字典类型'
  },
  rules: {
    nameRequired: '请输入名称',
    codeRequired: '请输入编码',
    codePattern: '编码须大写字母开头，仅含大写字母、数字与下划线'
  },
  tag: {
    system: '系统预设',
    custom: '自定义',
    mirror: '枚举镜像'
  },
  tip: {
    mirrorType: '该类型为系统枚举镜像，修改名称不影响业务取值',
    systemItemDelete: '系统预设项不可删除，可改为停用',
    systemTypeDelete: '系统预设类型不可删除',
    codeReadonly: '编码创建后不可修改'
  },
  msg: {
    createTypeSuccess: '字典类型已创建',
    updateTypeSuccess: '字典类型已更新',
    deleteTypeSuccess: '字典类型已删除',
    deleteTypeConfirm: '确认删除字典类型「{name}」？',
    createItemSuccess: '字典选项已创建',
    updateItemSuccess: '字典选项已更新',
    deleteItemSuccess: '字典选项已删除',
    deleteItemConfirm: '确认删除选项「{name}」？',
    switchToEnabled: '已启用',
    switchToDisabled: '已停用'
  },
  disabledSuffix: '（已停用）',
  value: {
    ISSUE_SOURCE: {
      // SYSTEM 为 Phase7 种子中的默认来源项（issue.source 兜底值）
      SYSTEM: '系统录入',
      MANUAL: '手工录入',
      API_IMPORT: '接口导入',
      EXCEL_IMPORT: 'Excel 导入',
      EMAIL: '邮件反馈',
      OTHER: '其他'
    },
    ISSUE_STATUS: {
      PENDING: '待处理',
      IN_PROGRESS: '处理中',
      PENDING_VERIFY: '待验证',
      VERIFIED: '验证通过',
      CLOSED: '已关闭'
    },
    ISSUE_PRIORITY: {
      HIGH: '高',
      MEDIUM: '中',
      LOW: '低'
    },
    ISSUE_SEVERITY: {
      FATAL: '致命',
      SERIOUS: '严重',
      NORMAL: '一般',
      MINOR: '轻微'
    }
  }
}

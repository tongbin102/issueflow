/**
 * 问题模块文案（zh-CN）：提交面板 / 列表 / 详情 / 流转
 */
export default {
  form: {
    title: '标题',
    type: '问题类型',
    severity: '严重等级',
    priority: '优先级',
    project: '项目',
    module: '模块',
    tags: '标签',
    assignee: '指派给',
    description: '问题描述',
    steps: '复现步骤',
    expected: '期望结果',
    actual: '实际结果',
    attachment: '附件',
    envOs: '操作系统',
    envBrowser: '浏览器',
    envAppVersion: '应用版本',
    envDevice: '设备型号',
    claimTip: '认领后将无法改派，请确认',
    section: {
      basic: '基本信息',
      category: '归属与分类',
      material: '补充材料',
      env: '环境信息'
    }
  },
  placeholder: {
    title: '请输入标题',
    selectType: '请选择问题类型',
    selectSeverity: '请选择严重等级',
    selectProject: '请选择项目',
    selectModule: '请选择模块',
    selectAssignee: '请选择指派对象',
    description: '请描述问题现象与影响',
    steps: '请描述复现步骤',
    tags: '多个标签用逗号分隔'
  },
  rules: {
    titleRequired: '请输入标题',
    typeRequired: '请选择问题类型',
    severityRequired: '请选择严重等级',
    descriptionRequired: '请输入问题描述'
  },
  section: {
    basic: '基本信息',
    detail: '详细描述',
    attachment: '附件与备注'
  },
  list: {
    title: '问题列表',
    myTitle: '我的问题',
    col: {
      issueNo: '编号',
      title: '标题',
      type: '类型',
      severity: '严重等级',
      status: '状态',
      tags: '标签',
      project: '项目',
      module: '模块',
      reporter: '提交者',
      assignee: '指派',
      createdAt: '创建时间',
      updatedAt: '更新时间',
      actions: '操作'
    },
    filter: {
      status: '状态筛选',
      type: '类型筛选',
      severity: '等级筛选',
      project: '项目筛选',
      tag: '标签筛选',
      version: '版本筛选',
      keyword: '标题/描述关键字'
    }
  },
  filter: {
    typeDisabledSuffix: '（已停用）'
  },
  detail: {
    title: '问题详情',
    none: '无',
    section: {
      basic: '基本信息',
      flow: '流转记录',
      attachment: '附件',
      relation: '关联问题',
      action: '流转操作',
      history: '操作历史'
    },
    field: {
      reporter: '报告人',
      claimedBy: '认领人'
    },
    flow: {
      action: '操作',
      operator: '操作人',
      time: '时间',
      comment: '备注',
      empty: '暂无流转记录'
    }
  },
  drawer: {
    createTitle: '提交新问题',
    editTitle: '编辑问题'
  },
  msg: {
    createSuccess: '问题已提交',
    updateSuccess: '问题已更新',
    deleteSuccess: '问题已删除',
    deleteConfirm: '确认删除该问题？删除后不可恢复',
    claimSuccess: '认领成功',
    submitFixSuccess: '修复已提交',
    verifyPassSuccess: '验证通过',
    verifyRejectSuccess: '已退回',
    closeSuccess: '已关闭',
    reopenSuccess: '已重开',
    claimRequired: '请先认领该问题',
    notFound: '问题不存在或已删除',
    remarkRequired: '该操作必须填写备注'
  },
  action: {
    new: '提交问题',
    submitNew: '提交新问题',
    viewDetail: '查看详情',
    flow: '流转',
    remark: '填写备注'
  },
  flowBtn: {
    claim: '认领 / 处理中',
    submitFix: '提交修复',
    verifyPass: '验证通过',
    reject: '回退',
    close: '关闭',
    reopen: '重开',
    noAction: '当前状态无可执行流转',
    remarkOptional: '可选备注',
    remarkRequiredPh: '请填写原因（必填）',
    remarkWarn: '请填写原因',
    success: '操作成功'
  }
}

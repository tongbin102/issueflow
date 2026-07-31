/**
 * 枚举文案（zh-CN）：状态 / 严重等级 / 角色 / 历史动作 / 流程节点类型
 * 由 utils/i18nEnum.js 用 t('enum.xxx.' + code) 动态拼接
 */
export default {
  status: {
    0: '待处理',
    1: '处理中',
    2: '待验证',
    3: '验证通过',
    4: '已关闭',
    unknown: '未知'
  },
  severity: {
    0: '致命',
    1: '严重',
    2: '一般',
    3: '轻微',
    unknown: '未知'
  },
  role: {
    SUBMITTER: '提交者',
    DEVELOPER: '开发人员',
    TESTER: '测试人员',
    ADMIN: '管理员'
  },
  action: {
    CREATE: '新建',
    CLAIM: '认领',
    SUBMIT_FIX: '提交修复',
    VERIFY_PASS: '验证通过',
    VERIFY_REJECT: '验证回退',
    CLOSE: '关闭',
    REOPEN: '重开',
    EDIT: '编辑'
  },
  nodeType: {
    1: '开始',
    2: '审核',
    3: '结束'
  }
}

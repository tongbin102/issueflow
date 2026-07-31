/**
 * 流程监控 / 流程配置页文案（zh-CN）
 */
export default {
  monitor: {
    title: '流程监控',
    recent: '最近流转（按更新时间）',
    col: {
      instance: '流程实例',
      issue: '关联问题',
      currentNode: '当前节点',
      status: '状态',
      duration: '耗时',
      startedAt: '发起时间'
    },
    detail: '流程详情',
    node: {
      start: '开始',
      review: '审核',
      end: '结束'
    }
  },
  config: {
    title: '流程配置',
    col: {
      name: '流程名称',
      nodes: '节点数',
      status: '状态'
    },
    form: {
      name: '流程名称',
      description: '描述'
    },
    node: {
      name: '节点名称',
      type: '节点类型'
    },
    transition: {
      from: '来源',
      to: '目标',
      action: '动作'
    },
    msg: {
      saveSuccess: '流程已保存'
    }
  }
}

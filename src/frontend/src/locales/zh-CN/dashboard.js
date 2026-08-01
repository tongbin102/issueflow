/**
 * 工作台 / 概览文案（zh-CN）
 */
export default {
  title: '工作台',
  greeting: {
    morning: '上午好',
    afternoon: '下午好',
    evening: '晚上好'
  },
  welcome: '欢迎回来，{name}',
  card: {
    todo: '待我处理',
    claimed: '已认领',
    submitted: '我提交',
    verifying: '待我验证',
    closed: '已关闭',
    total: '问题总数',
    open: '待处理',
    inProgress: '处理中',
    // Phase9 T8：状态卡可点击提示（无障碍 aria-label 同用）
    clickHint: '点击查看该状态下的问题'
  },
  section: {
    recent: '最近更新',
    mine: '我的关注',
    // Phase9 T8：前台工作台新增分区
    overview: '数据概览',
    quickEntry: '快捷入口',
    myRecent: '我的最近问题',
    trend: '提交趋势'
  },
  quick: {
    create: '提交问题',
    list: '所有问题',
    // Phase9 T8：快捷入口卡片（标题 + 一句话说明）
    myIssues: '我的问题',
    stats: '我的统计',
    profile: '个人中心',
    createDesc: '快速登记一个新问题',
    myIssuesDesc: '查看并跟进我提交的问题',
    statsDesc: '查看个人提交与处理数据',
    profileDesc: '维护个人资料与偏好设置'
  },
  recent: {
    empty: '你还没有提交过问题',
    emptyDesc: '提交第一个问题，它会出现在这里',
    emptyAction: '提交问题',
    viewAll: '查看全部',
    updatedAt: '更新于 {time}'
  },
  empty: '暂无数据',
  user: {
    statsTitle: '个人看板',
    submittedTotal: '我提交总计',
    myTrend: '我的趋势',
    viewMy: '查看我的问题 →',
    submitTrend: '提交趋势'
  },
  admin: {
    title: '全局看板',
    avgCycle: '平均解决周期(小时)',
    resolveRate: '解决率',
    trend: '趋势',
    trendTitle: '提交/解决趋势',
    distribution: '分布',
    exportPng: '导出 PNG',
    exportExcel: '导出 Excel',
    loadFailed: '看板加载失败'
  }
}

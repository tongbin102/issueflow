/**
 * 数据备份文案（zh-CN，Phase7 T8）
 * key 规范：backup.{group}.{semantic}
 * 约束（ARCH §七.1）：本文件 key 集合必须与 en-US/backup.js 完全一致。
 */
export default {
  entry: {
    group: '数据维护',
    desc: '导出业务数据快照用于归档或迁移，仅导出数据库表数据，不含附件二进制文件。'
  },
  drawer: {
    title: '备份数据'
  },
  action: {
    open: '备份数据',
    confirm: '确认备份',
    exporting: '导出中…',
    refresh: '重新预估'
  },
  form: {
    scope: '导出范围',
    format: '导出格式'
  },
  scope: {
    ALL: '全部数据',
    CORE: '仅核心配置',
    allTip: '导出全部业务表（含问题、评论、日志等），数据量较大，耗时较久。',
    coreTip: '仅导出字典、菜单、角色、流程等核心配置表，体积小，适合环境迁移。'
  },
  format: {
    JSON: 'JSON',
    SQL: 'SQL',
    jsonTip: '结构化 JSON 快照，含元信息头，便于程序解析与差异比对。',
    sqlTip: 'INSERT 语句脚本，可直接在目标库执行导入（需自行保证表结构一致）。'
  },
  estimate: {
    title: '导出预估',
    tableCount: '预计导出表数量',
    totalRows: '预计导出数据量',
    fileName: '文件名',
    detail: '逐表明细',
    tableUnit: '{count} 张',
    rowUnit: '{count} 条',
    loading: '预估中…',
    failed: '预估失败，请稍后重试',
    empty: '当前范围下没有可导出的数据表'
  },
  col: {
    table: '表名',
    rows: '行数'
  },
  tip: {
    noBinary: '附件二进制文件不在备份范围内，仅保留文件路径等元信息。',
    passwordMasked: '用户密码已脱敏为 ***，本备份无法直接用于账号还原。',
    excluded: '不导出的表：{tables}',
    noRepeat: '导出期间请勿重复点击，文件生成完成后会自动下载。'
  },
  msg: {
    success: '备份文件已开始下载：{name}',
    failed: '备份导出失败',
    emptyBody: '服务端未返回备份内容，请重试',
    estimateFailed: '获取导出预估失败'
  }
}

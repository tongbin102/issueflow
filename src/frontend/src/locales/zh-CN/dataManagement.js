/**
 * 数据管理页文案（zh-CN，Phase10）。
 *
 * 承接原「备份设置」页，覆盖备份 / 恢复 / 上传 / 保留策略 / 数据初始化全流程。
 * 与 en-US/dataManagement.js 必须逐 key 成对，缺一即视为红线违规。
 */
export default {
  title: '数据管理',
  menu: '数据管理',
  subtitle: '备份、下载、恢复系统数据，并维护备份保留策略',

  action: {
    create: '立即备份',
    refresh: '刷新',
    download: '下载',
    delete: '删除',
    restore: '恢复',
    upload: '上传恢复',
    detail: '详情',
    config: '保留策略',
    save: '保存',
    cancel: '取消',
    confirm: '确定',
    close: '关闭',
    search: '查询',
    reset: '重置'
  },

  filter: {
    keyword: '关键字',
    keywordPlaceholder: '文件名或备注',
    type: '备份类型',
    source: '来源',
    status: '状态',
    all: '全部'
  },

  column: {
    name: '备份名称',
    fileName: '文件名',
    type: '类型',
    source: '来源',
    size: '大小',
    status: '状态',
    operator: '操作人',
    createTime: '创建时间',
    duration: '耗时',
    actions: '操作'
  },

  type: {
    FULL: '全量备份',
    DB_ONLY: '仅数据库',
    CONFIG_ONLY: '仅配置'
  },

  source: {
    MANUAL: '手动备份',
    AUTO: '自动备份',
    UPLOAD: '上传导入',
    PRE_RESTORE: '恢复前安全备份'
  },

  status: {
    PENDING: '排队中',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELED: '已取消'
  },

  phase: {
    INIT: '初始化',
    LOCK: '获取任务锁',
    DUMP_DB: '导出数据库',
    DUMP_CONFIG: '导出配置',
    PACKAGE: '打包归档',
    CHECKSUM: '计算校验和',
    PERSIST: '登记备份记录',
    VALIDATE: '校验备份包',
    PRE_BACKUP: '生成安全备份',
    UNPACK: '解包备份文件',
    IMPORT_DB: '导入数据库',
    REFRESH_CACHE: '刷新缓存',
    DONE: '完成'
  },

  create: {
    title: '新建备份',
    name: '备份名称',
    namePlaceholder: '选填，便于日后辨认，例如「上线前基线」',
    type: '备份类型',
    typeTip: '全量备份包含数据库与配置文件；仅数据库不含配置；仅配置不可用于数据恢复。',
    includeConfig: '包含配置文件',
    includeConfigTip: '配置文件中的密码、密钥等敏感项会在打包时自动脱敏，不会明文进入备份包。',
    hint: '备份期间系统可正常使用，但同一时刻只允许一个备份或恢复任务运行。'
  },

  restore: {
    title: '恢复确认',
    danger: '高风险操作：恢复将用备份内容整体覆盖当前数据库',
    dangerDetail: '当前所有业务数据（问题、项目、用户等）都会被备份时刻的数据替换，且无法通过「撤销」找回。恢复期间系统进入只读状态，所有写操作会被拒绝。',
    backupInfo: '待恢复的备份',
    preBackup: '恢复前自动生成安全备份',
    preBackupTip: '强烈建议保留。系统会在覆盖前先完整备份当前数据库，万一恢复结果不符合预期，可用这份安全备份回退。',
    preBackupForced: '系统已强制开启恢复前安全备份，无法关闭。',
    confirmLabel: '确认文本',
    confirmPlaceholder: '请输入 RESTORE 以确认',
    confirmWord: 'RESTORE',
    confirmTip: '为防止误触，请手动输入确认文本后再提交。',
    remark: '恢复备注',
    remarkPlaceholder: '选填，记录本次恢复的原因',
    readonlyNotice: '恢复开始后请勿关闭浏览器，也不要让其他成员继续操作系统。'
  },

  upload: {
    title: '上传备份包恢复',
    file: '备份文件',
    dropTip: '将 .zip 备份包拖到此处，或点击选择文件',
    formatLimit: '仅支持由本系统导出的 .zip 备份包',
    sizeLimit: '单个文件不超过 {size} MB',
    name: '备份名称',
    namePlaceholder: '选填，用于在列表中标识这份上传的备份',
    restoreNow: '上传后立即恢复',
    restoreNowTip: '关闭时仅把备份包登记到列表，不执行恢复；之后可在列表中随时发起恢复。',
    uploading: '正在上传（{percent}%）',
    validating: '正在校验备份包…',
    onlyRegisterDone: '备份包已上传并登记，可在列表中查看'
  },

  config: {
    title: '备份保留策略',
    maxCopies: '最多保留份数',
    maxCopiesTip: '超出份数时自动清理最旧的备份。恢复前生成的安全备份、以及最后一份成功备份不参与清理。',
    defaultDays: '保留天数',
    defaultDaysTip: '超过该天数的备份会被自动清理，与「最多保留份数」同时生效，满足任一条件即清理。',
    sizeLimitMB: '上传体积上限(MB)',
    sizeLimitTip: '限制单个上传备份包的大小，防止超大文件拖垮服务器磁盘与内存。',
    saved: '保留策略已保存'
  },

  progress: {
    titleBackup: '备份进行中',
    titleRestore: '数据恢复进行中',
    phaseLabel: '当前阶段',
    lost: '进度获取失败',
    lostTip: '与服务器的连接不稳定，任务可能仍在后台运行。请稍后刷新列表确认结果。',
    successBackup: '备份完成',
    successRestore: '数据恢复完成，建议刷新页面以加载最新数据',
    failedBackup: '备份失败',
    failedRestore: '数据恢复失败',
    keepOpen: '任务运行期间请保持页面打开'
  },

  detail: {
    title: '备份详情',
    checksum: '校验和',
    dbName: '数据库',
    appVersion: '应用版本',
    tableCount: '表数量',
    startedAt: '开始时间',
    finishedAt: '结束时间',
    errorMsg: '失败原因'
  },

  reset: {
    group: '危险操作',
    title: '数据初始化',
    desc: '清空所有业务数据（问题、项目、模块、组织、非 admin 用户等），保留角色、权限、菜单、系统配置与流程定义。该操作不可撤销，执行前请务必先做一次全量备份。',
    button: '初始化数据',
    doneTitle: '数据初始化已完成，各表清理条数如下：',
    countUnit: '{count} 条'
  },

  empty: '暂无备份记录',
  unknown: '未知',

  msg: {
    createAccepted: '备份任务已提交',
    deleteConfirm: '确定要删除备份「{name}」吗？该操作会同时删除磁盘上的备份文件，且不可撤销。',
    deleteConfirmTitle: '删除备份',
    deleteSuccess: '备份已删除',
    downloadStarted: '下载已开始',
    downloadFailed: '下载失败，备份文件可能已丢失',
    restoreAccepted: '恢复任务已提交，系统进入只读状态',
    uploadAccepted: '备份包已上传',
    confirmMismatch: '确认文本不正确，请输入 RESTORE',
    selectFile: '请先选择要上传的备份文件',
    fileTypeInvalid: '仅支持 .zip 格式的备份包',
    fileTooLarge: '文件超出上传上限（{size} MB）',
    taskRunning: '已有备份或恢复任务正在执行，请等待其完成'
  }
}

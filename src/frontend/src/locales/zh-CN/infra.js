/**
 * 基础设施文案（zh-CN）：文件管理 / 配置管理 / Redis 监控 / 定时任务
 * key 规范：infra.{module}.{group}.{semantic}
 * 约束（ARCH §七.1）：本文件 key 集合必须与 en-US/infra.js 完全一致。
 */
export default {
  file: {
    config: {
      title: '文件配置',
      desc: '文件存储的全局配置，修改后仅对新上传的文件生效，存量文件不会迁移。',
      edit: '编辑配置',
      drawerTitle: '编辑文件配置',
      storageType: '存储类型',
      storageRoot: '存储根目录',
      maxSizeMb: '单文件大小上限',
      allowedExts: '允许的扩展名',
      usedSize: '已用空间',
      fileCount: '文件总数',
      writable: '目录可写',
      writableYes: '可写',
      writableNo: '不可写',
      writableWarn: '存储根目录当前不可写，上传会失败，请检查服务器磁盘权限。',
      unitMb: 'MB',
      countUnit: '{count} 个',
      rootTip: '服务器上的绝对路径，需保证运行账号有读写权限。',
      extsTip: '多个扩展名以英文逗号分隔，不带点号，例如：png,jpg,pdf',
      typeTip: '当前版本仅支持本地磁盘存储（LOCAL）。',
      statTitle: '存储概览',
      saveSuccess: '文件配置已保存',
      rules: {
        rootRequired: '存储根目录不能为空',
        maxSizeRequired: '单文件大小上限不能为空',
        maxSizeRange: '单文件大小上限需在 1 ~ 100 MB 之间',
        extsRequired: '允许的扩展名不能为空',
        extsPattern: '仅允许字母、数字与英文逗号'
      }
    },
    list: {
      title: '文件列表',
      upload: '上传文件',
      uploading: '上传中…',
      col: {
        name: '文件名',
        ext: '类型',
        size: '大小',
        bizType: '业务类型',
        bizRef: '关联业务',
        uploader: '上传人',
        createdAt: '上传时间',
        actions: '操作'
      },
      filter: {
        keyword: '文件名',
        ext: '扩展名',
        bizType: '业务类型',
        extPlaceholder: '如 png',
        bizTypePlaceholder: '如 MANUAL'
      },
      preview: '预览',
      previewTitle: '图片预览 - {name}',
      previewUnsupported: '该文件类型不支持在线预览，请下载后查看',
      deleteConfirm: '确认删除文件「{name}」？删除后不可恢复。',
      uploadSuccess: '上传成功',
      deleteSuccess: '删除成功',
      sizeExceed: '文件大小超过 {max}MB 限制',
      extNotAllowed: '不支持的文件类型「{ext}」，允许：{allowed}',
      empty: '暂无文件'
    }
  },
  config: {
    title: '配置管理',
    desc: '集中查看系统所有配置项。网站设置与文件存储配置为「同源不同视图」，请到各自页面修改。',
    col: {
      key: '配置键',
      value: '配置值',
      group: '分组',
      source: '来源',
      actions: '操作'
    },
    group: {
      all: '全部分组',
      SYS: '系统配置',
      FLOW: '流程开关',
      SITE: '网站设置',
      FILE: '文件存储'
    },
    builtin: '内置',
    readonly: '只读',
    editTitle: '编辑配置项',
    keyLabel: '配置键',
    valueLabel: '配置值',
    valueRequired: '配置值不能为空',
    saveSuccess: '配置已保存',
    emptyValue: '（空）',
    gotoSite: '前往网站设置',
    gotoFile: '前往文件配置',
    siteTip: '网站设置类配置请在「系统管理 > 网站设置」中修改。',
    fileTip: '文件存储类配置请在「基础设施 > 文件管理 > 文件配置」中修改。',
    builtinTip: '内置配置键由系统使用，仅允许改值，不允许删除。',
    empty: '暂无配置项'
  },
  redis: {
    title: 'Redis 监控',
    autoRefresh: '自动刷新',
    autoRefreshTip: '开启后每 10 秒自动拉取一次',
    lastUpdate: '最后更新：{time}',
    readonlyTip: '本页仅执行只读的 INFO / DBSIZE 查询，不会对 Redis 做任何写操作。',
    unavailableTitle: 'Redis 连接失败',
    unavailableDesc: '监控数据当前不可用：{reason}',
    unavailableUnknown: '未知原因，请检查 Redis 服务状态与连接配置',
    unavailableHint: '若生产环境禁用了 INFO / DBSIZE 命令，本页将持续显示此状态，属预期行为。',
    retry: '重试',
    server: {
      title: '服务器',
      version: 'Redis 版本',
      mode: '运行模式',
      os: '操作系统',
      uptime: '运行时长',
      clients: '当前连接数',
      uptimeDays: '{days} 天'
    },
    memory: {
      title: '内存',
      used: '已用内存',
      peak: '内存峰值',
      max: '最大内存',
      fragmentation: '碎片率',
      usage: '内存占用率',
      maxUnlimited: '未限制'
    },
    stats: {
      title: '运行统计',
      dbSize: 'Key 总数',
      hits: '命中次数',
      misses: '未命中次数',
      hitRate: '命中率',
      expired: '过期 Key 数',
      evicted: '淘汰 Key 数',
      totalConnections: '历史连接数',
      totalCommands: '处理命令数'
    },
    keyspace: {
      title: '键空间分布',
      db: '数据库',
      keys: 'Key 数',
      expires: '带过期时间',
      avgTtl: '平均 TTL(ms)',
      empty: '各数据库均无数据'
    },
    unknown: '-'
  },
  job: {
    title: '定时任务',
    create: '新增任务',
    col: {
      name: '任务名称',
      group: '分组',
      jobKey: '执行目标',
      cron: 'cron 表达式',
      status: '状态',
      lastExecTime: '上次执行',
      lastResult: '上次结果',
      cost: '耗时',
      nextExecTime: '下次执行',
      actions: '操作'
    },
    filter: {
      keyword: '任务名 / 执行目标',
      status: '运行状态'
    },
    state: {
      running: '运行中',
      paused: '已暂停'
    },
    result: {
      success: '成功',
      fail: '失败',
      none: '未执行'
    },
    action: {
      run: '立即执行',
      pause: '暂停',
      resume: '启用',
      logs: '执行日志'
    },
    form: {
      name: '任务名称',
      group: '任务分组',
      jobKey: '执行目标',
      cron: 'cron 表达式',
      params: '任务参数',
      status: '运行状态',
      description: '描述'
    },
    placeholder: {
      name: '请输入任务名称',
      group: '留空则为 default',
      jobKey: '请选择执行目标',
      cron: '6 段 cron，如 0 0 2 * * ?',
      params: 'key=value 或 JSON，可留空',
      description: '任务用途说明，可留空'
    },
    rules: {
      nameRequired: '任务名称不能为空',
      jobKeyRequired: '执行目标不能为空',
      cronRequired: 'cron 表达式不能为空',
      cronPattern: 'cron 需为 6 段（秒 分 时 日 月 周）'
    },
    drawer: {
      createTitle: '新增定时任务',
      editTitle: '编辑定时任务',
      logTitle: '执行日志 - {name}'
    },
    log: {
      startTime: '开始时间',
      cost: '耗时',
      trigger: '触发方式',
      result: '结果',
      message: '执行信息',
      empty: '暂无执行日志'
    },
    trigger: {
      CRON: '定时触发',
      MANUAL: '手动触发'
    },
    msg: {
      createSuccess: '任务已创建',
      updateSuccess: '任务已更新',
      deleteSuccess: '任务已删除',
      deleteConfirm: '确认删除任务「{name}」？删除后调度将同时取消。',
      runConfirm: '确认立即执行任务「{name}」？',
      runSuccess: '已触发执行，请稍后在执行日志中查看结果',
      pauseSuccess: '任务已暂停',
      resumeSuccess: '任务已启用'
    },
    cronTip: 'Spring 6 段 cron：秒 分 时 日 月 周。示例 0 0 2 * * ? 表示每天 02:00 执行。',
    whitelistTip: '执行目标只能从后端注册的任务白名单中选择，不支持填写类名。',
    empty: '暂无定时任务'
  }
}

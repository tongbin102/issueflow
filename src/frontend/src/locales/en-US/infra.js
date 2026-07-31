/**
 * Infrastructure texts (en-US): file / config / redis / scheduled jobs
 * Key set MUST stay identical to zh-CN/infra.js (ARCH §7.1).
 */
export default {
  file: {
    config: {
      title: 'File Config',
      desc: 'Global file storage settings. Changes apply to new uploads only; existing files are not migrated.',
      edit: 'Edit Config',
      drawerTitle: 'Edit File Config',
      storageType: 'Storage Type',
      storageRoot: 'Storage Root',
      maxSizeMb: 'Max File Size',
      allowedExts: 'Allowed Extensions',
      usedSize: 'Used Space',
      fileCount: 'Total Files',
      writable: 'Root Writable',
      writableYes: 'Writable',
      writableNo: 'Not Writable',
      writableWarn:
        'The storage root is not writable, uploads will fail. Please check server disk permissions.',
      unitMb: 'MB',
      countUnit: '{count} file(s)',
      rootTip: 'Absolute path on the server; the runtime account must have read/write access.',
      extsTip: 'Comma-separated extensions without dots, e.g. png,jpg,pdf',
      typeTip: 'Only local disk storage (LOCAL) is supported in this version.',
      statTitle: 'Storage Overview',
      saveSuccess: 'File config saved',
      rules: {
        rootRequired: 'Storage root is required',
        maxSizeRequired: 'Max file size is required',
        maxSizeRange: 'Max file size must be between 1 and 100 MB',
        extsRequired: 'Allowed extensions are required',
        extsPattern: 'Only letters, digits and commas are allowed'
      }
    },
    list: {
      title: 'File List',
      upload: 'Upload File',
      uploading: 'Uploading…',
      col: {
        name: 'File Name',
        ext: 'Type',
        size: 'Size',
        bizType: 'Business Type',
        bizRef: 'Related To',
        uploader: 'Uploader',
        createdAt: 'Uploaded At',
        actions: 'Actions'
      },
      filter: {
        keyword: 'File Name',
        ext: 'Extension',
        bizType: 'Business Type',
        extPlaceholder: 'e.g. png',
        bizTypePlaceholder: 'e.g. MANUAL'
      },
      preview: 'Preview',
      previewTitle: 'Image Preview - {name}',
      previewUnsupported: 'This file type cannot be previewed online, please download it instead',
      deleteConfirm: 'Delete file "{name}"? This cannot be undone.',
      uploadSuccess: 'Uploaded successfully',
      deleteSuccess: 'Deleted successfully',
      sizeExceed: 'File exceeds the {max}MB limit',
      extNotAllowed: 'Unsupported file type "{ext}". Allowed: {allowed}',
      empty: 'No files yet'
    }
  },
  config: {
    title: 'Config Management',
    desc: 'Central view of all system configuration. Site and file settings share the same source and are edited on their own pages.',
    col: {
      key: 'Config Key',
      value: 'Value',
      group: 'Group',
      source: 'Source',
      actions: 'Actions'
    },
    group: {
      all: 'All Groups',
      SYS: 'System',
      FLOW: 'Workflow Switches',
      SITE: 'Site Settings',
      FILE: 'File Storage'
    },
    builtin: 'Built-in',
    readonly: 'Read-only',
    editTitle: 'Edit Config Item',
    keyLabel: 'Config Key',
    valueLabel: 'Value',
    valueRequired: 'Value is required',
    saveSuccess: 'Config saved',
    emptyValue: '(empty)',
    gotoSite: 'Go to Site Settings',
    gotoFile: 'Go to File Config',
    siteTip: 'Site settings are edited under "System > Site Settings".',
    fileTip: 'File storage settings are edited under "Infrastructure > File Management > File Config".',
    builtinTip: 'Built-in keys are used by the system: values can be changed, keys cannot be removed.',
    empty: 'No config items'
  },
  redis: {
    title: 'Redis Monitor',
    autoRefresh: 'Auto Refresh',
    autoRefreshTip: 'Refetch every 10 seconds when enabled',
    lastUpdate: 'Last updated: {time}',
    readonlyTip: 'This page issues read-only INFO / DBSIZE queries only; no write command is executed.',
    unavailableTitle: 'Redis Connection Failed',
    unavailableDesc: 'Monitoring data is unavailable: {reason}',
    unavailableUnknown: 'Unknown reason, please check the Redis service and connection settings',
    unavailableHint:
      'If INFO / DBSIZE are disabled in production, this state is expected and permanent.',
    retry: 'Retry',
    server: {
      title: 'Server',
      version: 'Redis Version',
      mode: 'Mode',
      os: 'OS',
      uptime: 'Uptime',
      clients: 'Connected Clients',
      uptimeDays: '{days} day(s)'
    },
    memory: {
      title: 'Memory',
      used: 'Used Memory',
      peak: 'Peak Memory',
      max: 'Max Memory',
      fragmentation: 'Fragmentation Ratio',
      usage: 'Memory Usage',
      maxUnlimited: 'Unlimited'
    },
    stats: {
      title: 'Runtime Stats',
      dbSize: 'Total Keys',
      hits: 'Keyspace Hits',
      misses: 'Keyspace Misses',
      hitRate: 'Hit Rate',
      expired: 'Expired Keys',
      evicted: 'Evicted Keys',
      totalConnections: 'Total Connections',
      totalCommands: 'Total Commands'
    },
    keyspace: {
      title: 'Keyspace',
      db: 'Database',
      keys: 'Keys',
      expires: 'With TTL',
      avgTtl: 'Avg TTL(ms)',
      empty: 'All databases are empty'
    },
    unknown: '-'
  },
  job: {
    title: 'Scheduled Tasks',
    create: 'New Task',
    col: {
      name: 'Task Name',
      group: 'Group',
      jobKey: 'Job Target',
      cron: 'Cron',
      status: 'Status',
      lastExecTime: 'Last Run',
      lastResult: 'Last Result',
      cost: 'Duration',
      nextExecTime: 'Next Run',
      actions: 'Actions'
    },
    filter: {
      keyword: 'Name / Job Target',
      status: 'Status'
    },
    state: {
      running: 'Running',
      paused: 'Paused'
    },
    result: {
      success: 'Success',
      fail: 'Failed',
      none: 'Never Run'
    },
    action: {
      run: 'Run Now',
      pause: 'Pause',
      resume: 'Resume',
      logs: 'Logs'
    },
    form: {
      name: 'Task Name',
      group: 'Task Group',
      jobKey: 'Job Target',
      cron: 'Cron Expression',
      params: 'Parameters',
      status: 'Status',
      description: 'Description'
    },
    placeholder: {
      name: 'Enter task name',
      group: 'Defaults to "default" when empty',
      jobKey: 'Select a job target',
      cron: '6-field cron, e.g. 0 0 2 * * ?',
      params: 'key=value or JSON, optional',
      description: 'What this task does, optional'
    },
    rules: {
      nameRequired: 'Task name is required',
      jobKeyRequired: 'Job target is required',
      cronRequired: 'Cron expression is required',
      cronPattern: 'Cron must have 6 fields (sec min hour day month week)'
    },
    drawer: {
      createTitle: 'New Scheduled Task',
      editTitle: 'Edit Scheduled Task',
      logTitle: 'Execution Logs - {name}'
    },
    log: {
      startTime: 'Start Time',
      cost: 'Duration',
      trigger: 'Trigger',
      result: 'Result',
      message: 'Message',
      empty: 'No execution logs yet'
    },
    trigger: {
      CRON: 'Scheduled',
      MANUAL: 'Manual'
    },
    msg: {
      createSuccess: 'Task created',
      updateSuccess: 'Task updated',
      deleteSuccess: 'Task deleted',
      deleteConfirm: 'Delete task "{name}"? Its schedule will be cancelled as well.',
      runConfirm: 'Run task "{name}" right now?',
      runSuccess: 'Execution triggered, check the logs shortly',
      pauseSuccess: 'Task paused',
      resumeSuccess: 'Task resumed'
    },
    cronTip: 'Spring 6-field cron: sec min hour day month week. e.g. 0 0 2 * * ? runs daily at 02:00.',
    whitelistTip: 'Job targets come from the backend whitelist only; class names are not accepted.',
    empty: 'No scheduled tasks'
  }
}

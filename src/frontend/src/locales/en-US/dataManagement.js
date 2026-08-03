/**
 * Data management page copy (en-US, Phase10).
 *
 * Successor to the former "Backup Settings" page; covers backup / restore /
 * upload / retention policy / data reset.
 * Must stay key-for-key paired with zh-CN/dataManagement.js — a missing key
 * counts as a hard-line violation.
 */
export default {
  title: 'Data Management',
  menu: 'Data Management',
  subtitle: 'Back up, download and restore system data, and maintain the backup retention policy',

  action: {
    create: 'Back Up Now',
    refresh: 'Refresh',
    download: 'Download',
    delete: 'Delete',
    restore: 'Restore',
    upload: 'Upload & Restore',
    detail: 'Details',
    config: 'Retention Policy',
    save: 'Save',
    cancel: 'Cancel',
    confirm: 'OK',
    close: 'Close',
    search: 'Search',
    reset: 'Reset'
  },

  filter: {
    keyword: 'Keyword',
    keywordPlaceholder: 'File name or remark',
    type: 'Backup Type',
    source: 'Source',
    status: 'Status',
    all: 'All'
  },

  column: {
    name: 'Backup Name',
    fileName: 'File Name',
    type: 'Type',
    source: 'Source',
    size: 'Size',
    status: 'Status',
    operator: 'Operator',
    createTime: 'Created At',
    duration: 'Duration',
    actions: 'Actions'
  },

  type: {
    FULL: 'Full Backup',
    DB_ONLY: 'Database Only',
    CONFIG_ONLY: 'Config Only'
  },

  source: {
    MANUAL: 'Manual',
    AUTO: 'Scheduled',
    UPLOAD: 'Uploaded',
    PRE_RESTORE: 'Pre-restore Safety Backup'
  },

  status: {
    PENDING: 'Pending',
    RUNNING: 'Running',
    SUCCESS: 'Succeeded',
    FAILED: 'Failed',
    CANCELED: 'Canceled'
  },

  phase: {
    INIT: 'Initializing',
    LOCK: 'Acquiring task lock',
    DUMP_DB: 'Exporting database',
    DUMP_CONFIG: 'Exporting configuration',
    PACKAGE: 'Packaging archive',
    CHECKSUM: 'Computing checksum',
    PERSIST: 'Recording backup entry',
    VALIDATE: 'Validating archive',
    PRE_BACKUP: 'Creating safety backup',
    UNPACK: 'Unpacking archive',
    IMPORT_DB: 'Importing database',
    REFRESH_CACHE: 'Refreshing cache',
    DONE: 'Done'
  },

  create: {
    title: 'New Backup',
    name: 'Backup Name',
    namePlaceholder: 'Optional, e.g. "Pre-release baseline"',
    type: 'Backup Type',
    typeTip: 'A full backup includes both the database and configuration files; database-only excludes configuration; config-only cannot be used to restore data.',
    includeConfig: 'Include Config Files',
    includeConfigTip: 'Sensitive entries such as passwords and keys are masked during packaging and never stored in plain text inside the archive.',
    hint: 'The system stays usable during a backup, but only one backup or restore task may run at a time.'
  },

  restore: {
    title: 'Confirm Restore',
    danger: 'High-risk operation: restoring overwrites the entire current database with the backup contents',
    dangerDetail: 'All current business data (issues, projects, users, etc.) will be replaced by the data captured at backup time, and cannot be recovered via "undo". The system enters read-only mode during the restore and all write operations are rejected.',
    backupInfo: 'Backup to restore',
    preBackup: 'Create a safety backup before restoring',
    preBackupTip: 'Strongly recommended. The system fully backs up the current database before overwriting it, so you can roll back if the restore result is not what you expected.',
    preBackupForced: 'The pre-restore safety backup is enforced by the system and cannot be disabled.',
    confirmLabel: 'Confirmation Text',
    confirmPlaceholder: 'Type RESTORE to confirm',
    confirmWord: 'RESTORE',
    confirmTip: 'To prevent accidental clicks, type the confirmation text manually before submitting.',
    remark: 'Restore Remark',
    remarkPlaceholder: 'Optional, record why this restore was performed',
    readonlyNotice: 'Do not close the browser once the restore starts, and keep other members from operating the system.'
  },

  upload: {
    title: 'Upload Archive & Restore',
    file: 'Backup File',
    dropTip: 'Drop a .zip archive here, or click to choose a file',
    formatLimit: 'Only .zip archives exported by this system are supported',
    sizeLimit: 'A single file must not exceed {size} MB',
    name: 'Backup Name',
    namePlaceholder: 'Optional, used to identify this uploaded backup in the list',
    restoreNow: 'Restore immediately after upload',
    restoreNowTip: 'When off, the archive is only registered in the list without restoring; you can start a restore from the list at any time later.',
    uploading: 'Uploading ({percent}%)',
    validating: 'Validating archive…',
    onlyRegisterDone: 'The archive has been uploaded and registered; you can find it in the list'
  },

  config: {
    title: 'Backup Retention Policy',
    maxCopies: 'Max Copies',
    maxCopiesTip: 'The oldest backups are cleaned up automatically once this count is exceeded. Pre-restore safety backups and the latest successful backup are excluded from cleanup.',
    defaultDays: 'Retention Days',
    defaultDaysTip: 'Backups older than this are cleaned up automatically. This works together with "Max Copies" — meeting either condition triggers cleanup.',
    sizeLimitMB: 'Upload Size Limit (MB)',
    sizeLimitTip: 'Caps the size of a single uploaded archive to keep oversized files from exhausting server disk and memory.',
    saved: 'Retention policy saved'
  },

  progress: {
    titleBackup: 'Backup in Progress',
    titleRestore: 'Restore in Progress',
    phaseLabel: 'Current Phase',
    lost: 'Failed to fetch progress',
    lostTip: 'The connection to the server is unstable; the task may still be running in the background. Refresh the list later to confirm the result.',
    successBackup: 'Backup completed',
    successRestore: 'Restore completed. Refreshing the page is recommended to load the latest data',
    failedBackup: 'Backup failed',
    failedRestore: 'Restore failed',
    keepOpen: 'Keep this page open while the task is running'
  },

  detail: {
    title: 'Backup Details',
    checksum: 'Checksum',
    dbName: 'Database',
    appVersion: 'App Version',
    tableCount: 'Table Count',
    startedAt: 'Started At',
    finishedAt: 'Finished At',
    errorMsg: 'Failure Reason'
  },

  reset: {
    group: 'Danger Zone',
    title: 'Data Reset',
    desc: 'Clears all business data (issues, projects, modules, organizations, non-admin users, etc.) while keeping roles, permissions, menus, system configuration and flow definitions. This action cannot be undone — always take a full backup first.',
    button: 'Reset Data',
    doneTitle: 'Data reset completed. Rows cleared per table:',
    countUnit: '{count} rows'
  },

  empty: 'No backup records yet',
  unknown: 'Unknown',

  msg: {
    createAccepted: 'Backup task submitted',
    deleteConfirm: 'Delete backup "{name}"? This also removes the backup file from disk and cannot be undone.',
    deleteConfirmTitle: 'Delete Backup',
    deleteSuccess: 'Backup deleted',
    downloadStarted: 'Download started',
    downloadFailed: 'Download failed; the backup file may be missing',
    restoreAccepted: 'Restore task submitted; the system is now read-only',
    uploadAccepted: 'Archive uploaded',
    confirmMismatch: 'Confirmation text is incorrect, please type RESTORE',
    selectFile: 'Please choose a backup file first',
    fileTypeInvalid: 'Only .zip archives are supported',
    fileTooLarge: 'File exceeds the upload limit ({size} MB)',
    taskRunning: 'A backup or restore task is already running, please wait for it to finish'
  }
}

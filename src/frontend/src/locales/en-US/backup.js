/**
 * Data backup texts (en-US, Phase7 T8)
 * Key convention: backup.{group}.{semantic}
 * Constraint (ARCH §7.1): key set must exactly match zh-CN/backup.js.
 */
export default {
  entry: {
    group: 'Data Maintenance',
    desc: 'Export a snapshot of business data for archiving or migration. Database table rows only, attachment binaries are not included.'
  },
  drawer: {
    title: 'Backup Data'
  },
  action: {
    open: 'Backup Data',
    confirm: 'Start Backup',
    exporting: 'Exporting…',
    refresh: 'Re-estimate'
  },
  form: {
    scope: 'Scope',
    format: 'Format'
  },
  scope: {
    ALL: 'All Data',
    CORE: 'Core Config Only',
    allTip: 'Exports every business table (issues, comments, logs and more). Large volume and slower to generate.',
    coreTip: 'Exports only core config tables such as dictionaries, menus, roles and flows. Small and ideal for environment migration.'
  },
  format: {
    JSON: 'JSON',
    SQL: 'SQL',
    jsonTip: 'Structured JSON snapshot with a metadata header, easy to parse and diff.',
    sqlTip: 'INSERT statement script that can be executed directly on the target database (schema must match).'
  },
  estimate: {
    title: 'Export Estimate',
    tableCount: 'Tables to export',
    totalRows: 'Rows to export',
    fileName: 'File name',
    detail: 'Per-table detail',
    tableUnit: '{count} tables',
    rowUnit: '{count} rows',
    loading: 'Estimating…',
    failed: 'Failed to estimate, please retry',
    empty: 'No exportable table under the current scope'
  },
  col: {
    table: 'Table',
    rows: 'Rows'
  },
  tip: {
    noBinary: 'Attachment binaries are excluded; only metadata such as file paths is kept.',
    passwordMasked: 'User passwords are masked as ***, so this backup cannot restore accounts directly.',
    excluded: 'Excluded tables: {tables}',
    noRepeat: 'Do not click repeatedly while exporting; the download starts once the file is ready.'
  },
  msg: {
    success: 'Backup download started: {name}',
    failed: 'Backup export failed',
    emptyBody: 'The server returned no backup content, please retry',
    estimateFailed: 'Failed to load the export estimate'
  }
}

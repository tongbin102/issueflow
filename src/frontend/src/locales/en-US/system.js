/**
 * System settings texts (en-US, existing settings page)
 */
export default {
  // Phase10 requirement 3: the `title` key (formerly "Backup Settings") retired along
  // with SystemSettings.vue; the page role moved to the data management page
  // (dataManagement.title). reset.table.* is kept — the data management page still
  // reuses those table labels when rendering data-reset results.
  group: {
    basic: 'Basic',
    security: 'Security',
    flow: 'Flow Switches',
    data: 'Data Management'
  },
  field: {
    siteName: 'Site Name',
    logo: 'Logo',
    sessionTimeout: 'Session Timeout (min)',
    passwordPolicy: 'Password Policy',
    reopenEnabled: 'Allow Reopen',
    rejectEnabled: 'Allow Verify Reject'
  },
  msg: {
    saveSuccess: 'Settings saved'
  },
  reset: {
    title: 'Data Reset',
    desc: 'Clears all business data (issues, projects, modules, organizations, non-admin users, etc.) while keeping roles, permissions, menus, system configs and flow definitions. Intended as a one-time cleanup before go-live. This action cannot be undone.',
    button: 'Reset Data',
    doneTitle: 'Data reset completed. Rows cleaned per table:',
    countUnit: '{count} rows',
    // Phase10 requirement 3: copy for the data reset drawer (DataResetDrawer), which used
    // to be hardcoded Chinese; paired zh/en added as the entry moved into data management.
    alertTitle: 'Dangerous action: data reset cannot be undone!',
    alertDesc: 'The business data listed below will be permanently erased and cannot be recovered. Make sure a backup exists before proceeding.',
    clearTitle: 'Will be erased',
    keepTitle: 'Will be kept',
    confirmTip: 'Type {keyword} to confirm:',
    confirmPlaceholder: 'Type {keyword}',
    confirmButton: 'Erase Data',
    success: 'Data reset completed',
    clearItems: {
      issue: 'Issues',
      issueHistory: 'Issue history',
      attachment: 'Attachments',
      issueRelation: 'Issue relations',
      tagRelation: 'Tag relations',
      project: 'Projects',
      module: 'Modules',
      moduleDependency: 'Module dependencies',
      organization: 'Organizations',
      user: 'Users except admin'
    },
    keepItems: {
      role: 'Roles',
      permission: 'Permissions',
      menu: 'Menus',
      config: 'System configs',
      flow: 'Flow definitions',
      admin: 'admin account'
    },
    table: {
      issue_attachment: 'Issue Attachments',
      issue_history: 'Issue History',
      issue_relation: 'Issue Relations',
      issue: 'Issues',
      tag: 'Tags',
      module_dependency: 'Module Dependencies',
      module: 'Modules',
      project: 'Projects',
      organization: 'Organizations',
      user: 'Users (except admin)'
    }
  }
}

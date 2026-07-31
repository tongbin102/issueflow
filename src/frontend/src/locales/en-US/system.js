/**
 * System settings texts (en-US, existing settings page)
 */
export default {
  title: 'System Settings',
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

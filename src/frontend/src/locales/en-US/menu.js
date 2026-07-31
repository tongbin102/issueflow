/**
 * Menu name texts (en-US): mapped by path (MENU_KEY_BY_PATH in utils/i18nEnum.js)
 */
export default {
  user: {
    dashboard: 'Workspace',
    issueManage: 'Issue Management',
    myIssues: 'My Issues',
    stats: 'My Dashboard',
    profile: 'Profile Center'
  },
  admin: {
    overview: 'Overview',
    issues: 'Issue Management',
    business: 'Business',
    issueList: 'Issue List',
    dict: 'Dictionaries',
    infra: 'Infrastructure',
    infraFile: 'File Management',
    infraFileConfig: 'File Config',
    infraFileList: 'File List',
    infraConfig: 'Config Management',
    infraRedis: 'Redis Monitor',
    infraJob: 'Scheduled Tasks',
    issueTypes: 'Issue Types',
    projectGroup: 'Project',
    projects: 'Project Config',
    flowGroup: 'Workflow',
    flowMonitor: 'Flow Monitor',
    flowConfig: 'Flow Config',
    system: 'System',
    users: 'Users',
    organizations: 'Organizations',
    menus: 'Menus',
    roles: 'Roles',
    // Phase8 W1 #2: menu label rework (route path / component unchanged)
    // /admin/system/site      → System Settings (was "Site Settings")
    // /admin/system/settings  → Backup Settings (was "System Settings")
    siteSettings: 'System Settings',
    systemSettings: 'Backup Settings'
  }
}

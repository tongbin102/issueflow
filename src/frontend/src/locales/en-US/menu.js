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
    fieldConfigs: 'Field Config',
    infra: 'Infrastructure',
    infraFile: 'File Management',
    infraFileConfig: 'File Config',
    infraFileList: 'File List',
    infraConfig: 'Config Management',
    infraRedis: 'Redis Monitor',
    infraJob: 'Scheduled Tasks',
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
    siteSettings: 'System Settings'
    // Phase10 requirement 3: the former systemSettings ("Backup Settings") is retired;
    // /admin/system/settings redirects to /admin/system/data-management and the menu
    // label now comes from dataManagement.menu (see utils/i18nEnum.js).
  }
}

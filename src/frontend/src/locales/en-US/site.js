/**
 * Site settings texts (en-US, new in Phase6)
 */
export default {
  page: {
    // Phase8 W1 #2: menu "Site Settings" → "System Settings", page title kept in sync
    title: 'System Settings'
  },
  group: {
    basic: 'Basic Info',
    appearance: 'Default Appearance',
    footer: 'Footer',
    security: 'Security'
  },
  form: {
    name: 'Site Name',
    shortName: 'Short Name',
    subtitle: 'Subtitle',
    title: 'Browser Title',
    defaultTheme: 'Default Theme',
    defaultLocale: 'Default Language',
    copyright: 'Copyright',
    icp: 'ICP License',
    footerText: 'Footer Text',
    logo: 'Site Logo',
    favicon: 'Favicon',
    defaultPassword: 'Default Password for New Users'
  },
  tip: {
    defaultPassword: 'Initial password used when an admin creates a user; change it after first login'
  },
  rules: {
    nameRequired: 'Please enter the site name',
    shortNameRequired: 'Please enter the short name',
    defaultPasswordRequired: 'Please enter the default password',
    defaultPasswordLength: 'The default password must be 6-32 characters'
  },
  action: {
    restoreDefault: 'Restore Defaults'
  },
  msg: {
    saveSuccess: 'Site settings saved',
    loadError: 'Failed to load site settings',
    restoreTip: 'Defaults restored to the form; click "Save" to apply'
  }
}

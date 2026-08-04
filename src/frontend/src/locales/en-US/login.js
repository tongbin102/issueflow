/**
 * Login page texts (en-US)
 */
export default {
  title: 'Sign In',
  subtitle: 'Defect Tracking & Verification Platform',
  tip: 'Default admin: admin / admin123',
  /** Clear cache button label */
  clearCache: 'Clear Cache',
  /** Clear cache confirmation dialog */
  clearCacheConfirm: {
    title: 'Clear Local Cache',
    content:
      'This will clear local login state and browser cache data (theme, language, layout preferences). Continue?'
  },
  /** Brand panel: value proposition */
  brandDesc:
    'A unified entry for project issue reporting — every issue stays trackable, collaborative and measurable.',
  /** Brand panel: feature highlights (item-by-item paired with zh-CN) */
  features: ['Workflow Tracking', 'Collaborative Reporting', 'Data Analytics'],
  field: {
    username: 'Username',
    password: 'Password',
    remember: 'Remember me'
  },
  action: {
    submit: 'Sign In'
  },
  msg: {
    usernameRequired: 'Please enter your username',
    passwordRequired: 'Please enter your password',
    success: 'Signed in',
    failed: 'Incorrect username or password',
    cacheCleared: 'Cache cleared, login state reset'
  }
}

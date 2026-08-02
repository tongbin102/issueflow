/**
 * Role management texts (en-US)
 */
export default {
  page: {
    title: 'Roles'
  },
  col: {
    name: 'Role Name',
    code: 'Role Code',
    description: 'Description',
    userCount: 'Users',
    permissionCount: 'Permissions',
    builtin: 'Built-in'
  },
  action: {
    create: 'New Role',
    refreshCache: 'Refresh Permission Cache',
    assignPerm: 'Assign Permissions',
    retry: 'Retry',
    reload: 'Reload',
    reset: 'Reset'
  },
  drawer: {
    createTitle: 'New Role',
    editTitle: 'Edit Role'
  },
  placeholder: {
    code: 'e.g. CUSTOM_ROLE',
    searchPerm: 'Search permission name / code'
  },
  permModule: {
    dashboard: 'Dashboard',
    issue: 'Issue',
    project: 'Project',
    user: 'User',
    organization: 'Organization',
    menu: 'Menu',
    role: 'Role',
    settings: 'Settings',
    flow: 'Flow',
    system: 'System',
    other: 'Other'
  },
  form: {
    name: 'Role Name',
    code: 'Role Code',
    description: 'Description',
    permissions: 'Permissions'
  },
  /** Assign-permission drawer: front/back side segmented control */
  tab: {
    frontend: 'Frontend',
    backend: 'Backend'
  },
  tree: {
    selectAll: 'Select All',
    expandAll: 'Expand All',
    collapseAll: 'Collapse All',
    uncategorized: 'Uncategorized',
    uncategorizedTip: 'This permission has no side tag and is grouped under Backend',
    invertSelect: 'Invert',
    selectAllVisible: 'Select all visible',
    selectGroup: 'Select group',
    clearGroup: 'Clear group',
    matchCount: '{count} matched'
  },
  /** Assign-permission drawer: selected list on the right */
  selected: {
    title: 'Selected',
    count: '{count} selected',
    distribution: 'Front {front} / Back {back}',
    scopeCurrent: 'Current side',
    scopeAll: 'All',
    remove: 'Remove',
    clear: 'Clear',
    clearConfirm: 'Clear {count} selected permissions in "{scope}"?',
    stale: 'Deprecated',
    staleTip:
      'This permission is no longer in the catalog; it is kept on save; remove manually if needed',
    staleCount: 'Includes {count} deprecated'
  },
  /** Assign-permission drawer: change confirmation before save */
  diff: {
    title: 'Confirm permission changes',
    subtitle: 'Permissions for "{role}" take effect immediately after save',
    added: '{count} added',
    removed: '{count} removed',
    total: 'Total after change: {count}',
    viewAll: 'View all',
    confirm: 'Save',
    cancel: 'Back'
  },
  empty: {
    noPermission: 'No assignable permissions',
    noPermissionInTab: 'No permissions on this side',
    noSearchResult: 'No permissions matching "{keyword}"',
    clearSearch: 'Clear search'
  },
  tip: {
    manyItems: 'Many items, use search'
  },
  msg: {
    codeRequired: 'Please enter the role code',
    nameRequired: 'Please enter the name',
    cacheRefreshed: 'Permission cache refreshed',
    permSaved: 'Permissions saved',
    builtinTip:
      'Built-in role permissions can be adjusted, but the role code cannot be changed and the role cannot be deleted.',
    createSuccess: 'Role created',
    updateSuccess: 'Role updated',
    deleteSuccess: 'Role deleted',
    deleteConfirm: 'Delete role "{name}"?',
    builtinProtected: 'Built-in roles are protected: cannot delete or change the role code',
    noChanges: 'No changes to save',
    permSaveFailed: 'Failed to save, please retry',
    permSaveTimeout: 'Save timed out, please check network',
    permSaveFailedMulti: 'Repeated failures, please try later',
    loadPermFailed: 'Failed to load permission data',
    discardConfirm: 'Unsaved changes, discard?'
  }
}

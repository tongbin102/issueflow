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
    assignPerm: 'Assign Permissions'
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
  tree: {
    selectAll: 'Select All',
    expandAll: 'Expand All',
    collapseAll: 'Collapse All'
  },
  msg: {
    codeRequired: 'Please enter the role code',
    nameRequired: 'Please enter the name',
    cacheRefreshed: 'Permission cache refreshed',
    permSaved: 'Permissions saved',
    builtinTip: 'Built-in role permissions can be adjusted, but the role code cannot be changed and the role cannot be deleted.',
    createSuccess: 'Role created',
    updateSuccess: 'Role updated',
    deleteSuccess: 'Role deleted',
    deleteConfirm: 'Delete role "{name}"?',
    builtinProtected: 'Built-in roles are protected: cannot delete or change the role code'
  }
}

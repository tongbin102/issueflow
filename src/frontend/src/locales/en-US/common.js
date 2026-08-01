/**
 * Common texts (en-US): actions / status / fields / messages / placeholders / pager
 */
export default {
  action: {
    save: 'Save',
    cancel: 'Cancel',
    submit: 'Submit',
    confirm: 'Confirm',
    close: 'Close',
    create: 'Create',
    edit: 'Edit',
    delete: 'Delete',
    view: 'View',
    reset: 'Reset',
    search: 'Search',
    refresh: 'Refresh',
    export: 'Export',
    expandAll: 'Expand All',
    collapseAll: 'Collapse All',
    restoreDefault: 'Restore Defaults',
    fullscreen: 'Fullscreen',
    exitFullscreen: 'Exit Fullscreen',
    operation: 'Actions',
    back: 'Back',
    detail: 'Detail',
    upload: 'Upload',
    download: 'Download',
    enable: 'Enable',
    disable: 'Disable',
    logout: 'Log Out',
    // Phase9 T8: shared actions for empty states / card list
    retry: 'Retry',
    clearFilter: 'Reset Filters',
    viewAll: 'View All',
    more: 'More'
  },
  status: {
    enabled: 'Enabled',
    disabled: 'Disabled',
    all: 'All'
  },
  field: {
    createdAt: 'Created At',
    updatedAt: 'Updated At',
    remark: 'Remark',
    sort: 'Sort',
    status: 'Status',
    keyword: 'Keyword',
    description: 'Description',
    name: 'Name',
    code: 'Code',
    dateRange: 'Date Range',
    startDate: 'Start Date',
    endDate: 'End Date'
  },
  msg: {
    saveSuccess: 'Saved successfully',
    createSuccess: 'Created successfully',
    updateSuccess: 'Updated successfully',
    deleteSuccess: 'Deleted successfully',
    deleteConfirm: 'Delete "{name}"?',
    operationSuccess: 'Operation succeeded',
    noData: 'No data',
    loading: 'Loading…',
    required: 'This field is required',
    tip: 'Notice',
    warning: 'Warning',
    loadFailed: 'Failed to load'
  },
  // Phase9 T8: IfEmptyState scenes (scene = empty | noResult | error)
  empty: {
    emptyTitle: 'No data yet',
    emptyDesc: 'There is nothing to show here right now',
    noResultTitle: 'No matching results',
    noResultDesc: 'Try adjusting or resetting your filters',
    errorTitle: 'Failed to load',
    errorDesc: 'Network error or service unavailable, please try again later'
  },
  placeholder: {
    input: 'Please enter',
    select: 'Please select',
    search: 'Search by keyword'
  },
  pager: {
    total: '{total} total'
  }
}

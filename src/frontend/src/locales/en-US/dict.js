/**
 * Dictionary config texts (en-US); key set must mirror zh-CN/dict.js exactly.
 */
export default {
  page: {
    title: 'Dictionaries',
    typePanel: 'Dictionary Types',
    itemPanel: 'Dictionary Items',
    emptyType: 'Select a dictionary type on the left first'
  },
  col: {
    name: 'Name',
    code: 'Code',
    description: 'Description',
    sort: 'Sort',
    status: 'Status',
    itemCount: 'Items',
    refCount: 'References',
    updatedAt: 'Updated At',
    actions: 'Actions'
  },
  form: {
    typeName: 'Type Name',
    typeCode: 'Type Code',
    itemName: 'Item Name',
    itemCode: 'Item Code',
    belongType: 'Dictionary Type',
    description: 'Description',
    sort: 'Sort',
    status: 'Status'
  },
  drawer: {
    createType: 'New Type',
    editType: 'Edit Type',
    createItem: 'New Item',
    editItem: 'Edit Item'
  },
  action: {
    createType: 'New Type',
    createItem: 'New Item'
  },
  placeholder: {
    typeName: 'Enter type name',
    typeCode: 'Uppercase start, e.g. ISSUE_SOURCE',
    itemName: 'Enter item name',
    itemCode: 'Uppercase start, e.g. MANUAL',
    description: 'Enter description',
    selectType: 'Select a dictionary type'
  },
  rules: {
    nameRequired: 'Name is required',
    codeRequired: 'Code is required',
    codePattern: 'Code must start with an uppercase letter (A-Z, 0-9, _ only)'
  },
  tag: {
    system: 'System',
    custom: 'Custom',
    mirror: 'Enum Mirror'
  },
  tip: {
    mirrorType: 'This type mirrors a system enum; renaming does not affect business values',
    systemItemDelete: 'System preset items cannot be deleted, but can be disabled',
    systemTypeDelete: 'System preset types cannot be deleted',
    codeReadonly: 'Code cannot be changed after creation'
  },
  msg: {
    createTypeSuccess: 'Dictionary type created',
    updateTypeSuccess: 'Dictionary type updated',
    deleteTypeSuccess: 'Dictionary type deleted',
    deleteTypeConfirm: 'Delete dictionary type "{name}"?',
    createItemSuccess: 'Dictionary item created',
    updateItemSuccess: 'Dictionary item updated',
    deleteItemSuccess: 'Dictionary item deleted',
    deleteItemConfirm: 'Delete item "{name}"?',
    switchToEnabled: 'Enabled',
    switchToDisabled: 'Disabled'
  },
  disabledSuffix: ' (Disabled)',
  value: {
    ISSUE_SOURCE: {
      // SYSTEM is the Phase7 seeded default source (fallback of issue.source)
      SYSTEM: 'System Entry',
      MANUAL: 'Manual Entry',
      API_IMPORT: 'API Import',
      EXCEL_IMPORT: 'Excel Import',
      EMAIL: 'Email Feedback',
      OTHER: 'Other'
    },
    ISSUE_STATUS: {
      PENDING: 'Pending',
      IN_PROGRESS: 'In Progress',
      PENDING_VERIFY: 'Pending Verify',
      VERIFIED: 'Verified',
      CLOSED: 'Closed'
    },
    ISSUE_PRIORITY: {
      HIGH: 'High',
      MEDIUM: 'Medium',
      LOW: 'Low'
    },
    ISSUE_SEVERITY: {
      FATAL: 'Fatal',
      SERIOUS: 'Serious',
      NORMAL: 'Normal',
      MINOR: 'Minor'
    }
  }
}

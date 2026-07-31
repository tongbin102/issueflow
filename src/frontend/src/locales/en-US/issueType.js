/**
 * Issue Type management texts (en-US)
 */
export default {
  page: {
    title: 'Issue Types'
  },
  col: {
    name: 'Type Name',
    code: 'Code',
    description: 'Description',
    sort: 'Sort',
    status: 'Status',
    issueCount: 'Usages',
    updatedAt: 'Updated',
    actions: 'Actions'
  },
  form: {
    name: 'Type Name',
    code: 'Type Code',
    description: 'Description',
    sort: 'Sort',
    status: 'Status'
  },
  drawer: {
    createTitle: 'Create Type',
    editTitle: 'Edit Type'
  },
  placeholder: {
    name: 'Enter the type name',
    code: 'Uppercase, e.g. BUG',
    description: 'Enter the description'
  },
  rules: {
    nameRequired: 'Please enter the type name',
    codeRequired: 'Please enter the type code',
    codePattern: 'Code must start with an uppercase letter and contain only A-Z, 0-9 and _'
  },
  msg: {
    createSuccess: 'Type created',
    updateSuccess: 'Type updated',
    deleteSuccess: 'Type deleted',
    deleteConfirm: 'Delete type "{name}"?',
    codeExists: 'Type code already exists',
    deleteInUse: 'This type has {count} linked issues and cannot be deleted; disable it instead',
    switchToDisabled: 'Disabled',
    switchToEnabled: 'Enabled'
  }
}

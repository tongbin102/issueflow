/**
 * Dynamic field config texts (en-US); key set must mirror zh-CN/fieldConfig.js exactly.
 */
export default {
  page: {
    title: 'Field Configuration',
    subtitle: 'Manage issue form sections and fields; changes apply to the form immediately'
  },
  col: {
    name: 'Name',
    code: 'Code',
    type: 'Type',
    refSource: 'Ref Source',
    dependsOn: 'Depends On',
    sort: 'Sort',
    status: 'Status',
    actions: 'Actions'
  },
  form: {
    section: 'Section',
    name: 'Field Name',
    code: 'Field Code',
    i18nKey: 'i18n Key',
    type: 'Field Type',
    required: 'Required',
    placeholder: 'Placeholder',
    defaultValue: 'Default Value',
    span: 'Grid Span',
    multiline: 'Multiline',
    maxLength: 'Max Length',
    minVal: 'Min Value',
    maxVal: 'Max Value',
    decimalScale: 'Decimal Scale',
    dictCode: 'Dictionary Type',
    refSource: 'Ref Source',
    displayType: 'Display Type',
    multiSelect: 'Multi Select',
    dependsOn: 'Depends On',
    dependsParam: 'Filter Param',
    sort: 'Sort',
    status: 'Status',
    visibleInList: 'Visible In List',
    searchable: 'Searchable',
    baseGroup: 'Basic Attributes',
    attrGroup: 'Type Attributes',
    advanceGroup: 'Advanced Attributes'
  },
  drawer: {
    createField: 'New Field',
    editField: 'Edit Field',
    preview: 'Form Preview'
  },
  action: {
    createField: 'New Field',
    preview: 'Preview Form',
    expandAll: 'Expand All',
    collapseAll: 'Collapse All'
  },
  type: {
    TEXT: 'Text',
    NUMBER: 'Number',
    DATE: 'Date',
    DATETIME: 'Date Time',
    DICT: 'Dictionary',
    REF: 'Reference'
  },
  displayType: {
    select: 'Select',
    tree: 'Tree Select'
  },
  nodeType: {
    section: 'Section',
    field: 'Field'
  },
  tag: {
    system: 'Built-in',
    disabled: 'Disabled'
  },
  tip: {
    systemFieldDelete: 'Built-in fields cannot be deleted',
    codeReadonly: 'Field code cannot be changed after creation',
    typeReadonly: 'Field type cannot be changed after creation',
    systemAttrLocked:
      'For built-in fields only name, required, placeholder, span, sort and status are editable',
    sectionReadonly:
      'Section management is not available yet (backend endpoints missing); view only for now',
    dependsPair: '"Depends On" and "Filter Param" must be filled together',
    dependsOnly:
      'Only single-level dependency is supported: the upstream field must not be multi-select and must not depend on another field',
    selectParentFirst: 'Please select the upstream field first',
    previewEmpty: 'No field configuration to preview',
    noSectionOption: 'No section available; please initialize section data first'
  },
  rules: {
    nameRequired: 'Please enter the field name',
    codeRequired: 'Please enter the field code',
    codePattern: 'Code must start with a lowercase letter and contain letters/digits only',
    typeRequired: 'Please select the field type',
    sectionRequired: 'Please select the section',
    dictCodeRequired: 'Please select the dictionary type',
    refSourceRequired: 'Please select the ref source',
    dependsParamRequired: 'Please enter the filter param name',
    dependsOnRequired: 'Please select the upstream field',
    maxLengthExceed: 'No more than {max} characters',
    minValExceed: 'Cannot be less than {min}',
    maxValExceed: 'Cannot be greater than {max}'
  },
  msg: {
    createSuccess: 'Field created successfully',
    updateSuccess: 'Field updated successfully',
    deleteSuccess: 'Field deleted successfully',
    deleteConfirm:
      'Delete field "{name}"? Values already stored for this field will no longer be shown.',
    toggleSuccess: 'Status updated'
  }
}

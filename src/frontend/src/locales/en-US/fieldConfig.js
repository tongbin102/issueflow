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
  /**
   * Section titles and descriptions of the field config drawer.
   * Keys are referenced by utils/fieldConfigSchema.js via section.titleKey / section.descKey,
   * e.g. fieldConfig.section.base / fieldConfig.section.baseDesc.
   */
  section: {
    base: 'Basic Attributes',
    baseDesc:
      'Define what the field is and how it looks: owning section, name and code, type, grid span and default value.',
    type: 'Type Attributes',
    typeDesc:
      'Shown or hidden according to the field type; they constrain the value range and its source. Attributes that no longer apply are cleared when the type changes.',
    advanced: 'Advanced Attributes',
    advancedDesc:
      'Control how the field behaves in the list, the search bar and the form as a whole: ordering, list column, searchability and enabled state.'
  },
  /**
   * Attribute labels of the field config drawer.
   * Keys are referenced by utils/fieldConfigSchema.js via labelKeyOf(code),
   * e.g. fieldConfig.label.name. One entry per FIELD_FORM_ITEMS code (24 in total).
   */
  label: {
    sectionId: 'Section',
    name: 'Field Name',
    code: 'Field Code',
    i18nKey: 'i18n Key',
    type: 'Field Type',
    span: 'Grid Span',
    placeholder: 'Placeholder',
    defaultValue: 'Default Value',
    required: 'Required',
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
    visibleInList: 'Visible In List',
    searchable: 'Searchable',
    enabled: 'Status'
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
    noSectionOption: 'No section available; please initialize section data first',

    /* ---- Attribute hints: keys referenced by tipKeyOf(code) in fieldConfigSchema.js (24) ---- */
    sectionId:
      'The section of the issue form this field belongs to; it decides where the field is rendered. Sections are initialized by the backend, so you can only pick one here.',
    name: 'The label shown to the person filling in the form. Keep it short and unambiguous, e.g. "Product Line". It can be changed at any time.',
    code: 'Unique identifier of the field in the API and the database. Must be lowerCamelCase (starts with a lowercase letter, letters and digits only), e.g. productLine. It cannot be changed after creation.',
    i18nKey:
      'Reference key of the translation entry. When set, the label is resolved from the current locale; leave it empty to display the "Field Name" as is.',
    type: 'Determines the input control and validation of the field (text / number / date / dictionary / reference). It cannot be changed after creation, so choose carefully.',
    span: 'Width of the field in the 24-column form grid: 24 takes a full row, 12 a half row, 8 a third of a row. Defaults to 12.',
    placeholder:
      'Greyed-out hint shown while the input is empty; use it to indicate the expected format or an example, e.g. "Enter a 6-digit ticket number".',
    defaultValue:
      'Value pre-filled when a new issue is created. Leave it empty for no default. It must match the field type (use a number for numeric fields).',
    required:
      'When enabled the field is mandatory: a red asterisk is shown before the label and submission is blocked while it is empty.',
    multiline:
      'Text fields only. When enabled the single-line input is replaced by a textarea, which suits long descriptive content.',
    maxLength:
      'Text fields only. Limits the maximum number of characters; exceeding it blocks submission. Leave it empty for no limit.',
    minVal:
      'Number fields only. The smallest accepted value (inclusive); smaller values fail validation. Leave it empty for no lower bound.',
    maxVal:
      'Number fields only. The largest accepted value (inclusive); greater values fail validation. Leave it empty for no upper bound.',
    decimalScale:
      'Number fields only. How many decimal places are allowed; 0 means integers only. Leave it empty to treat the value as an integer.',
    dictCode:
      'Dictionary fields only. Picks the dictionary that supplies the values; the dropdown options are generated from its entries.',
    refSource:
      'Reference fields only. Picks the referenced business data source (e.g. product, user); options are fetched from that source at runtime.',
    displayType:
      'Dictionary / reference fields only. Chooses the shape of the picker: "Select" fits flat lists, "Tree Select" fits hierarchical data.',
    multiSelect:
      'Dictionary / reference fields only. When enabled several options can be selected and the value is stored as an array. Note: a multi-select field cannot be used as the upstream of another field.',
    dependsOn:
      'Sets the upstream field for cascading: this field\'s options are filtered by the upstream selection. Only one cascading level is supported, and it must be filled together with "Filter Param".',
    dependsParam:
      'Name of the filter parameter sent to the backend when loading options; its value comes from the current selection of "Depends On", e.g. parentId. Must be filled together with "Depends On".',
    sort: 'Ordering number within the section — smaller values come first. Leaving gaps (10, 20, 30) makes it easier to insert fields later.',
    visibleInList:
      'When enabled the field appears as a column in the issue list for quick scanning. Too many columns hurt readability, so enable it only when needed.',
    searchable:
      'When enabled the field appears in the search area of the issue list and can be used as a filter. Best reserved for highly selective fields.',
    enabled:
      'Enable switch of the field: once disabled it is no longer rendered on the form, while previously stored values are kept and it can be re-enabled at any time.'
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

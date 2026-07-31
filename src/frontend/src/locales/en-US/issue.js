/**
 * Issue module texts (en-US): submit panel / list / detail / flow
 */
export default {
  form: {
    title: 'Title',
    type: 'Issue Type',
    severity: 'Severity',
    priority: 'Priority',
    source: 'Source',
    project: 'Project',
    module: 'Module',
    tags: 'Tags',
    assignee: 'Assignee',
    description: 'Description',
    steps: 'Steps to Reproduce',
    expected: 'Expected Result',
    actual: 'Actual Result',
    attachment: 'Attachment',
    envOs: 'Operating System',
    envBrowser: 'Browser',
    envAppVersion: 'App Version',
    envDevice: 'Device Model',
    claimTip: 'Claimed issues cannot be reassigned',
    section: {
      basic: 'Basic Info',
      category: 'Category & Ownership',
      material: 'Supporting Materials',
      env: 'Environment'
    }
  },
  placeholder: {
    title: 'Enter the title',
    selectType: 'Select issue type',
    selectSeverity: 'Select severity',
    selectPriority: 'Select priority',
    selectSource: 'Select source',
    selectProject: 'Select project',
    selectModule: 'Select module',
    selectAssignee: 'Select assignee',
    description: 'Describe the symptom and impact',
    steps: 'Describe the steps to reproduce',
    tags: 'Separate multiple tags with commas'
  },
  rules: {
    titleRequired: 'Please enter the title',
    typeRequired: 'Please select the issue type',
    severityRequired: 'Please select the severity',
    priorityRequired: 'Please select the priority',
    sourceRequired: 'Please select the source',
    // Phase8 W2 #6: project is now mandatory
    projectRequired: 'Please select the project',
    // Kept for backward compatibility: description is optional since Phase8 W2 #12
    descriptionRequired: 'Please enter the description'
  },
  section: {
    basic: 'Basic Info',
    detail: 'Details',
    attachment: 'Attachments'
  },
  // Phase8 W2 #12: vertical (left) tabs inside the issue dialog
  // (#3.3: rename the "description" tab from "Description" to "Details")
  tab: {
    basic: 'Basic Info',
    description: 'Details',
    attachment: 'Attachments',
    relation: 'Relations',
    history: 'History'
  },
  tabTip: {
    basicInvalid: 'Please complete the required fields in "Basic Info" first',
    // #3.2 / #3.4: unified hint when full validation fails on submit
    submitInvalid: 'Please complete the required fields before submitting',
    relationPending: 'Relations can be managed after the issue is submitted',
    historyPending: 'History will be recorded after the issue is submitted'
  },
  list: {
    title: 'Issue List',
    myTitle: 'My Issues',
    col: {
      issueNo: 'No.',
      title: 'Title',
      type: 'Type',
      source: 'Source',
      priority: 'Priority',
      severity: 'Severity',
      status: 'Status',
      tags: 'Tags',
      project: 'Project',
      module: 'Module',
      reporter: 'Reporter',
      assignee: 'Assignee',
      createdAt: 'Created',
      updatedAt: 'Updated',
      actions: 'Actions'
    },
    filter: {
      status: 'Status',
      type: 'Type',
      severity: 'Severity',
      source: 'Source',
      priority: 'Priority',
      project: 'Project',
      tag: 'Tag',
      version: 'Version',
      keyword: 'Title / description keyword'
    }
  },
  filter: {
    typeDisabledSuffix: ' (Disabled)'
  },
  detail: {
    title: 'Issue Detail',
    none: 'None',
    section: {
      basic: 'Basic Info',
      flow: 'Flow Log',
      attachment: 'Attachments',
      relation: 'Related Issues',
      action: 'Flow Actions',
      history: 'History'
    },
    field: {
      reporter: 'Reporter',
      claimedBy: 'Claimed By'
    },
    flow: {
      action: 'Operation',
      operator: 'Operator',
      time: 'Time',
      comment: 'Comment',
      empty: 'No flow records yet'
    }
  },
  drawer: {
    createTitle: 'Submit New Issue',
    editTitle: 'Edit Issue'
  },
  msg: {
    createSuccess: 'Issue submitted',
    createSuccessWithNo: 'Issue submitted, No. {no}',
    updateSuccess: 'Issue updated',
    deleteSuccess: 'Issue deleted',
    deleteConfirm: 'Delete this issue? This cannot be undone',
    claimSuccess: 'Claimed',
    submitFixSuccess: 'Fix submitted',
    verifyPassSuccess: 'Verified',
    verifyRejectSuccess: 'Rejected',
    closeSuccess: 'Closed',
    reopenSuccess: 'Reopened',
    claimRequired: 'Please claim this issue first',
    notFound: 'Issue not found or deleted',
    remarkRequired: 'A remark is required for this operation',
    exportSuccess: 'Export started, please check your downloads',
    exportFail: 'Export failed, narrow the filters and try again'
  },
  action: {
    new: 'Submit Issue',
    submitNew: 'Submit New Issue',
    backToList: 'Back to List',
    viewDetail: 'View Detail',
    flow: 'Transition',
    exportExcel: 'Export Excel',
    remark: 'Add Remark'
  },
  attachment: {
    preview: 'Preview',
    previewAlt: 'Attachment preview',
    empty: 'No attachment',
    upload: 'Upload Attachment',
    sizeLimit: 'The file exceeds the {size}MB limit',
    uploadSuccess: 'Uploaded successfully',
    deleteSuccess: 'Attachment deleted'
  },
  relation: {
    title: 'Issue Relations',
    predecessor: 'Predecessors',
    successor: 'Successors',
    none: 'None',
    edit: 'Edit Relations',
    selectPredecessor: 'Select predecessor issues',
    selectSuccessor: 'Select successor issues',
    saveSuccess: 'Relations saved'
  },
  history: {
    system: 'System',
    remarkLine: 'Note: {text}',
    empty: 'No operation record'
  },
  flowBtn: {
    claim: 'Claim / In Progress',
    submitFix: 'Submit Fix',
    verifyPass: 'Verify Pass',
    reject: 'Reject',
    close: 'Close',
    reopen: 'Reopen',
    noAction: 'No transition available for current status',
    remarkOptional: 'Optional remark',
    remarkRequiredPh: 'Enter the reason (required)',
    remarkWarn: 'Please enter the reason',
    success: 'Operation succeeded'
  }
}

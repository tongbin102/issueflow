/**
 * Issue module texts (en-US): submit panel / list / detail / flow
 */
export default {
  form: {
    title: 'Title',
    type: 'Issue Type',
    severity: 'Severity',
    priority: 'Priority',
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
    descriptionRequired: 'Please enter the description'
  },
  section: {
    basic: 'Basic Info',
    detail: 'Details',
    attachment: 'Attachments'
  },
  list: {
    title: 'Issue List',
    myTitle: 'My Issues',
    col: {
      issueNo: 'No.',
      title: 'Title',
      type: 'Type',
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
    remarkRequired: 'A remark is required for this operation'
  },
  action: {
    new: 'Submit Issue',
    submitNew: 'Submit New Issue',
    viewDetail: 'View Detail',
    flow: 'Transition',
    remark: 'Add Remark'
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

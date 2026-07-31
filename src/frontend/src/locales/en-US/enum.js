/**
 * Enum texts (en-US): status / severity / role / history action / flow node type
 */
export default {
  status: {
    0: 'Open',
    1: 'In Progress',
    2: 'Pending Verify',
    3: 'Verified',
    4: 'Closed',
    unknown: 'Unknown'
  },
  severity: {
    0: 'Blocker',
    1: 'Critical',
    2: 'Major',
    3: 'Minor',
    unknown: 'Unknown'
  },
  priority: {
    0: 'High',
    1: 'Medium',
    2: 'Low',
    unknown: 'Unknown'
  },
  role: {
    SUBMITTER: 'Submitter',
    DEVELOPER: 'Developer',
    TESTER: 'Tester',
    ADMIN: 'Administrator'
  },
  action: {
    CREATE: 'Create',
    CLAIM: 'Claim',
    SUBMIT_FIX: 'Submit Fix',
    VERIFY_PASS: 'Verify Passed',
    VERIFY_REJECT: 'Verify Rejected',
    CLOSE: 'Close',
    REOPEN: 'Reopen',
    EDIT: 'Edit'
  },
  nodeType: {
    1: 'Start',
    2: 'Review',
    3: 'End'
  }
}

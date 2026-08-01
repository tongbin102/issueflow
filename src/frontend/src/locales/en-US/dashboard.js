/**
 * Workspace / overview texts (en-US)
 */
export default {
  title: 'Workspace',
  greeting: {
    morning: 'Good morning',
    afternoon: 'Good afternoon',
    evening: 'Good evening'
  },
  welcome: 'Welcome back, {name}',
  card: {
    todo: 'To Do',
    claimed: 'Claimed',
    submitted: 'Submitted',
    verifying: 'Awaiting My Verify',
    closed: 'Closed',
    total: 'Total Issues',
    open: 'Open',
    inProgress: 'In Progress',
    // Phase9 T8: clickable stat card hint (also used as aria-label)
    clickHint: 'Click to view issues in this status'
  },
  section: {
    recent: 'Recently Updated',
    mine: 'My Watchlist',
    // Phase9 T8: new sections on the user workspace
    overview: 'Overview',
    quickEntry: 'Quick Actions',
    myRecent: 'My Recent Issues',
    trend: 'Submission Trend'
  },
  quick: {
    create: 'Submit Issue',
    list: 'All Issues',
    // Phase9 T8: quick entry cards (title + one-line description)
    myIssues: 'My Issues',
    stats: 'My Stats',
    profile: 'Profile',
    createDesc: 'Report a new issue in seconds',
    myIssuesDesc: 'Track the issues you submitted',
    statsDesc: 'Review your submission and progress data',
    profileDesc: 'Manage your profile and preferences'
  },
  recent: {
    empty: 'You have not submitted any issue yet',
    emptyDesc: 'Submit your first issue and it will show up here',
    emptyAction: 'Submit Issue',
    viewAll: 'View All',
    updatedAt: 'Updated {time}'
  },
  empty: 'No data',
  user: {
    statsTitle: 'My Dashboard',
    submittedTotal: 'Total Submitted',
    myTrend: 'My Trend',
    viewMy: 'View My Issues →',
    submitTrend: 'Submission Trend'
  },
  admin: {
    title: 'Global Dashboard',
    avgCycle: 'Avg Resolve Cycle (h)',
    resolveRate: 'Resolve Rate',
    trend: 'Trend',
    trendTitle: 'Submitted / Resolved Trend',
    distribution: 'Distribution',
    exportPng: 'Export PNG',
    exportExcel: 'Export Excel',
    loadFailed: 'Failed to load dashboard'
  }
}

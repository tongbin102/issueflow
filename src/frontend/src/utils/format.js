/**
 * 日期格式化。
 * @param {Date|string|number} value 时间
 * @param {string} fmt 格式，默认 'YYYY-MM-DD HH:mm:ss'
 * @returns {string}
 */
export function formatDate(value, fmt = 'YYYY-MM-DD HH:mm:ss') {
  if (!value && value !== 0) return ''
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const map = {
    YYYY: date.getFullYear(),
    MM: pad(date.getMonth() + 1),
    DD: pad(date.getDate()),
    HH: pad(date.getHours()),
    mm: pad(date.getMinutes()),
    ss: pad(date.getSeconds())
  }
  return fmt.replace(/YYYY|MM|DD|HH|mm|ss/g, (k) => map[k])
}

// 角色码 → 中文名
export const ROLE_LABELS = {
  SUBMITTER: '提交者',
  DEVELOPER: '开发人员',
  TESTER: '测试人员',
  ADMIN: '管理员'
}

// 状态枚举（code → label）
export const STATUS_OPTIONS = [
  { value: 0, label: '待处理' },
  { value: 1, label: '处理中' },
  { value: 2, label: '待验证' },
  { value: 3, label: '验证通过' },
  { value: 4, label: '已关闭' }
]

// 严重等级枚举
export const SEVERITY_OPTIONS = [
  { value: 0, label: '致命' },
  { value: 1, label: '严重' },
  { value: 2, label: '一般' },
  { value: 3, label: '轻微' }
]

export function statusLabel(code) {
  const item = STATUS_OPTIONS.find((s) => s.value === Number(code))
  return item ? item.label : '未知'
}

export function severityLabel(code) {
  const item = SEVERITY_OPTIONS.find((s) => s.value === Number(code))
  return item ? item.label : '未知'
}

export function roleLabel(code) {
  return ROLE_LABELS[code] || code || '未知'
}

// 状态 → el-tag 类型（Element Plus 支持 primary/success/info/warning/danger）
export const STATUS_TAG_TYPE = {
  0: 'info',
  1: 'primary',
  2: 'warning',
  3: 'success',
  4: 'info'
}

// 状态 → 颜色（图表 / 自定义着色使用）
export const STATUS_COLORS = {
  0: '#909399',
  1: '#409EFF',
  2: '#E6A23C',
  3: '#67C23A',
  4: '#909399'
}

// 严重等级 → el-tag 类型
export const SEVERITY_TAG_TYPE = {
  0: 'danger',
  1: 'warning',
  2: 'primary',
  3: 'info'
}

// 严重等级 → 颜色
export const SEVERITY_COLORS = {
  0: '#F56C6C',
  1: '#E6A23C',
  2: '#409EFF',
  3: '#909399'
}

export function statusTagType(code) {
  return STATUS_TAG_TYPE[Number(code)] || 'info'
}

export function statusColor(code) {
  return STATUS_COLORS[Number(code)] || '#909399'
}

export function severityTagType(code) {
  return SEVERITY_TAG_TYPE[Number(code)] || 'info'
}

export function severityColor(code) {
  return SEVERITY_COLORS[Number(code)] || '#909399'
}

// 操作历史动作 → 中文
export const ACTION_LABELS = {
  CREATE: '新建',
  CLAIM: '认领',
  SUBMIT_FIX: '提交修复',
  VERIFY_PASS: '验证通过',
  VERIFY_REJECT: '验证回退',
  CLOSE: '关闭',
  REOPEN: '重开',
  EDIT: '编辑'
}

export function actionLabel(action) {
  return ACTION_LABELS[action] || action || '未知'
}

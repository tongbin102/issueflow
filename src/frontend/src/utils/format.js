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

/**
 * 字节数 → 可读文件大小（Phase7 T6：文件列表 / 文件配置 / Redis 内存复用）。
 * @param {number|string} bytes 字节数
 * @param {number} [fractionDigits=1] 小数位数
 * @returns {string} 如 「1.5 MB」；空值返回 '-'
 */
export function formatFileSize(bytes, fractionDigits = 1) {
  const num = Number(bytes)
  if (bytes === null || bytes === undefined || Number.isNaN(num)) return '-'
  if (num < 0) return '-'
  if (num < 1024) return `${num} B`
  const units = ['KB', 'MB', 'GB', 'TB', 'PB']
  let value = num / 1024
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  return `${value.toFixed(fractionDigits)} ${units[index]}`
}

/**
 * 数字 → 千分位字符串（Phase7 T8：备份预估条数展示复用）。
 * @param {number|string} value 数值
 * @returns {string} 如 「12,486」；空值/非法值返回 '0'
 */
export function formatNumber(value) {
  const num = Number(value)
  if (value === null || value === undefined || value === '' || Number.isNaN(num)) return '0'
  return num.toLocaleString('en-US')
}

/**
 * 毫秒 → 可读耗时（Phase7 T7：定时任务耗时 / Redis 运行时长复用）。
 * @param {number|string} ms 毫秒数
 * @returns {string} 如 「820 ms」「1.5 s」「2 分 05 秒」；空值返回 '-'
 */
export function formatDuration(ms) {
  const num = Number(ms)
  if (ms === null || ms === undefined || Number.isNaN(num) || num < 0) return '-'
  if (num < 1000) return `${Math.round(num)} ms`
  const totalSeconds = num / 1000
  if (totalSeconds < 60) return `${totalSeconds.toFixed(1)} s`
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = Math.floor(totalSeconds % 60)
  if (minutes < 60) return `${minutes}m ${String(seconds).padStart(2, '0')}s`
  const hours = Math.floor(minutes / 60)
  const restMinutes = minutes % 60
  return `${hours}h ${String(restMinutes).padStart(2, '0')}m`
}

/**
 * 角色码 → 中文名。
 * @deprecated Phase6 起页面展示请改用 utils/i18nEnum.js 的 roleLabelI18n（支持中英切换）；
 *             此常量仅保留给非展示逻辑（比较 / 兜底），后续版本移除。
 */
export const ROLE_LABELS = {
  SUBMITTER: '提交者',
  DEVELOPER: '开发人员',
  TESTER: '测试人员',
  ADMIN: '管理员'
}

/**
 * 状态枚举（code → label）。
 * @deprecated Phase6 起下拉选项请改用 utils/i18nEnum.js 的 useStatusOptions()（响应式 i18n）；
 *             文案请用 statusLabelI18n。此常量仅保留 value 枚举参照。
 */
export const STATUS_OPTIONS = [
  { value: 0, label: '待处理' },
  { value: 1, label: '处理中' },
  { value: 2, label: '待验证' },
  { value: 3, label: '验证通过' },
  { value: 4, label: '已关闭' }
]

/**
 * 严重等级枚举。
 * @deprecated Phase6 起请改用 utils/i18nEnum.js 的 useSeverityOptions() / severityLabelI18n。
 */
export const SEVERITY_OPTIONS = [
  { value: 0, label: '致命' },
  { value: 1, label: '严重' },
  { value: 2, label: '一般' },
  { value: 3, label: '轻微' }
]

/** @deprecated Phase6 起请改用 utils/i18nEnum.js 的 statusLabelI18n。 */
export function statusLabel(code) {
  const item = STATUS_OPTIONS.find((s) => s.value === Number(code))
  return item ? item.label : '未知'
}

/** @deprecated Phase6 起请改用 utils/i18nEnum.js 的 severityLabelI18n。 */
export function severityLabel(code) {
  const item = SEVERITY_OPTIONS.find((s) => s.value === Number(code))
  return item ? item.label : '未知'
}

/** @deprecated Phase6 起请改用 utils/i18nEnum.js 的 roleLabelI18n。 */
export function roleLabel(code) {
  return ROLE_LABELS[code] || code || '未知'
}

/**
 * Phase9 T4 / ARCH §七.6：语义色单一事实来源。
 *
 * 与 styles/variables.css 的 --if-color-* 令牌逐一对应，二者必须同步修改：
 *   success    ↔ --if-color-success
 *   warning    ↔ --if-color-warning
 *   danger     ↔ --if-color-danger
 *   info       ↔ --if-color-info
 *   processing ↔ --if-color-processing
 *
 * 为什么这里保留十六进制字面量而不是 var(--if-color-*)：
 *   ECharts / canvas 无法解析 CSS 变量，图表着色必须拿到真实色值；
 *   同时 STATUS_COLORS 等常量已有 40+ 调用点，改成变量字符串会造成大面积回归（R1）。
 * 因此约定：CSS 侧消费令牌，JS 侧消费本常量，取值保持一致。
 *
 * 硬约束：语义色固定，不随 light/dark/blue/green 主题变化。
 * @type {{success: string, warning: string, danger: string, info: string, processing: string}}
 */
export const SEMANTIC_COLORS = {
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  info: '#909399',
  processing: '#409EFF'
}

/** 语义色兜底值（未知枚举一律落到 info 灰）。 */
const FALLBACK_COLOR = SEMANTIC_COLORS.info

// 状态 → el-tag 类型（Element Plus 支持 primary/success/info/warning/danger）
export const STATUS_TAG_TYPE = {
  0: 'info',
  1: 'primary',
  2: 'warning',
  3: 'success',
  4: 'info'
}

/**
 * 状态 → 语义键（Phase9 T4 新增，供 IfTag / 统计卡读取语义 CSS 令牌）。
 * 0 待处理=info / 1 处理中=processing / 2 待验证=warning / 3 验证通过=success / 4 已关闭=info
 * @type {Object.<number, string>}
 */
export const STATUS_SEMANTIC = {
  0: 'info',
  1: 'processing',
  2: 'warning',
  3: 'success',
  4: 'info'
}

// 状态 → 颜色（图表 / 自定义着色使用）
export const STATUS_COLORS = {
  0: SEMANTIC_COLORS.info,
  1: SEMANTIC_COLORS.processing,
  2: SEMANTIC_COLORS.warning,
  3: SEMANTIC_COLORS.success,
  4: SEMANTIC_COLORS.info
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
  0: SEMANTIC_COLORS.danger,
  1: SEMANTIC_COLORS.warning,
  2: SEMANTIC_COLORS.processing,
  3: SEMANTIC_COLORS.info
}

/**
 * 严重等级 → 语义键（Phase9 T4 新增）。
 * @type {Object.<number, string>}
 */
export const SEVERITY_SEMANTIC = {
  0: 'danger',
  1: 'warning',
  2: 'processing',
  3: 'info'
}

// 优先级 → el-tag 类型（Phase7 决策 B：0 高=danger / 1 中=warning / 2 低=info，
// 与 severity 同一渲染风格；ARCH §七.6 硬约束：色值固定，不随主题变化）
export const PRIORITY_TAG_TYPE = {
  0: 'danger',
  1: 'warning',
  2: 'info'
}

// 优先级 → 颜色（图表 / 自定义着色使用）
export const PRIORITY_COLORS = {
  0: SEMANTIC_COLORS.danger,
  1: SEMANTIC_COLORS.warning,
  2: SEMANTIC_COLORS.info
}

/**
 * 优先级 → 语义键（Phase9 T4 新增）。
 * @type {Object.<number, string>}
 */
export const PRIORITY_SEMANTIC = {
  0: 'danger',
  1: 'warning',
  2: 'info'
}

export function statusTagType(code) {
  return STATUS_TAG_TYPE[Number(code)] || 'info'
}

export function statusColor(code) {
  return STATUS_COLORS[Number(code)] || FALLBACK_COLOR
}

/**
 * 状态 → 语义键（Phase9 T4 新增，配合 IfTag 的 semantic 属性使用）。
 * @param {number|string} code 状态码 0-4
 * @returns {string} success | warning | danger | info | processing
 */
export function statusSemantic(code) {
  return STATUS_SEMANTIC[Number(code)] || 'info'
}

export function severityTagType(code) {
  return SEVERITY_TAG_TYPE[Number(code)] || 'info'
}

export function severityColor(code) {
  return SEVERITY_COLORS[Number(code)] || FALLBACK_COLOR
}

/**
 * 严重等级 → 语义键（Phase9 T4 新增）。
 * @param {number|string} code 0 致命 / 1 严重 / 2 一般 / 3 轻微
 * @returns {string} success | warning | danger | info | processing
 */
export function severitySemantic(code) {
  return SEVERITY_SEMANTIC[Number(code)] || 'info'
}

/**
 * 优先级 → el-tag 类型（Phase7-R4：0 高=danger / 1 中=warning / 2 低=info）。
 * @param {number|string} code 0 高 / 1 中 / 2 低
 * @returns {string} danger | warning | info
 */
export function priorityTagType(code) {
  return PRIORITY_TAG_TYPE[Number(code)] || 'info'
}

/**
 * 优先级 → 颜色值。
 * @param {number|string} code 0 高 / 1 中 / 2 低
 * @returns {string} 十六进制色值
 */
export function priorityColor(code) {
  return PRIORITY_COLORS[Number(code)] || FALLBACK_COLOR
}

/**
 * 优先级 → 语义键（Phase9 T4 新增）。
 * @param {number|string} code 0 高 / 1 中 / 2 低
 * @returns {string} success | warning | danger | info | processing
 */
export function prioritySemantic(code) {
  return PRIORITY_SEMANTIC[Number(code)] || 'info'
}

/**
 * 操作历史动作 → 中文。
 * @deprecated Phase6 起请改用 utils/i18nEnum.js 的 actionLabelI18n（支持中英切换）。
 */
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

/** @deprecated Phase6 起请改用 utils/i18nEnum.js 的 actionLabelI18n。 */
export function actionLabel(action) {
  return ACTION_LABELS[action] || action || '未知'
}

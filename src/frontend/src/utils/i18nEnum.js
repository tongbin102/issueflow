import { computed } from 'vue'
import { i18n } from '@/locales'

/**
 * 枚举 i18n 工厂（ARCH §七.7）。
 * 状态/严重等级/角色/动作文案一律走本模块 t('enum.xxx.' + code) 拼接；
 * utils/format.js 仅保留色值与 tag 类型映射。
 */

const t = (...args) => i18n.global.t(...args)

/** 状态码集合（与后端 IssueStatusEnum 对齐） */
export const STATUS_CODES = [0, 1, 2, 3, 4]

/** 严重等级码集合（与后端 SeverityEnum 对齐） */
export const SEVERITY_CODES = [0, 1, 2, 3]

/**
 * 状态文案。
 * @param {number|string} code 0-4
 * @returns {string}
 */
export function statusLabelI18n(code) {
  const n = Number(code)
  if (!STATUS_CODES.includes(n)) return t('enum.status.unknown')
  return t('enum.status.' + n)
}

/**
 * 严重等级文案。
 * @param {number|string} code 0-3
 * @returns {string}
 */
export function severityLabelI18n(code) {
  const n = Number(code)
  if (!SEVERITY_CODES.includes(n)) return t('enum.severity.unknown')
  return t('enum.severity.' + n)
}

/**
 * 角色文案（无命中回退角色码原值）。
 * @param {string} code SUBMITTER/DEVELOPER/TESTER/ADMIN
 * @returns {string}
 */
export function roleLabelI18n(code) {
  if (!code) return t('enum.status.unknown')
  const key = 'enum.role.' + code
  return i18n.global.te(key) ? t(key) : code
}

/**
 * 历史动作文案（无命中回退原值）。
 * @param {string} action CREATE/CLAIM/...
 * @returns {string}
 */
export function actionLabelI18n(action) {
  if (!action) return t('enum.status.unknown')
  const key = 'enum.action.' + action
  return i18n.global.te(key) ? t(key) : action
}

/**
 * 流程节点类型文案。
 * @param {number|string} type 1开始 2审核 3结束
 * @returns {string}
 */
export function nodeTypeLabelI18n(type) {
  const key = 'enum.nodeType.' + Number(type)
  return i18n.global.te(key) ? t(key) : String(type ?? '')
}

/**
 * 响应式状态下拉选项（语言切换自动更新）。
 * @returns {import('vue').ComputedRef<Array<{value:number,label:string}>>}
 */
export function useStatusOptions() {
  return computed(() =>
    STATUS_CODES.map((code) => ({ value: code, label: t('enum.status.' + code) }))
  )
}

/**
 * 响应式严重等级下拉选项。
 * @returns {import('vue').ComputedRef<Array<{value:number,label:string}>>}
 */
export function useSeverityOptions() {
  return computed(() =>
    SEVERITY_CODES.map((code) => ({ value: code, label: t('enum.severity.' + code) }))
  )
}

/**
 * 菜单 path → i18n key 映射表（T8.2）。
 * 数据库 menu.name 保持原值；前端展示时命中此表则翻译，否则回退原值。
 */
export const MENU_KEY_BY_PATH = {
  '/user': 'menu.user.dashboard',
  '/user/issue': 'menu.user.issueManage',
  '/user/my-issues': 'menu.user.myIssues',
  '/user/stats': 'menu.user.stats',
  '/admin/index': 'menu.admin.overview',
  '/admin/issues': 'menu.admin.issues',
  '/admin/issue-types': 'menu.admin.issueTypes',
  '/admin/project': 'menu.admin.projectGroup',
  '/admin/projects': 'menu.admin.projects',
  '/admin/modules': 'menu.admin.modules',
  '/admin/flow': 'menu.admin.flowGroup',
  '/admin/flow-monitor': 'menu.admin.flowMonitor',
  '/admin/flow-config': 'menu.admin.flowConfig',
  '/admin/system': 'menu.admin.system',
  '/admin/system/users': 'menu.admin.users',
  '/admin/system/organizations': 'menu.admin.organizations',
  '/admin/system/menus': 'menu.admin.menus',
  '/admin/system/roles': 'menu.admin.roles',
  '/admin/system/site': 'menu.admin.siteSettings',
  '/admin/system/settings': 'menu.admin.systemSettings'
}

/**
 * 菜单节点显示名：按 path 查表翻译，无命中回退数据库原值（ARCH §七.10）。
 * @param {{path?:string,name?:string}} node 菜单节点
 * @returns {string}
 */
export function menuLabelI18n(node) {
  if (!node) return ''
  const key = node.path ? MENU_KEY_BY_PATH[node.path] : null
  if (key && i18n.global.te(key)) return t(key)
  return node.name || ''
}

/**
 * 问题类型下拉项显示名：停用项追加「(已停用)」i18n 后缀（Q6）。
 * @param {{name?:string,enabled?:boolean}} row IssueTypeOptionVO
 * @returns {string}
 */
export function issueTypeLabelI18n(row) {
  if (!row) return ''
  const name = row.name || ''
  return row.enabled === false ? name + t('issue.filter.typeDisabledSuffix') : name
}

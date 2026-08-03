import { computed } from 'vue'
import { i18n } from '@/locales'
import { useDictStore } from '@/store/dict'

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

/** 优先级码集合（与后端 PriorityEnum 对齐：0 高 / 1 中 / 2 低） */
export const PRIORITY_CODES = [0, 1, 2]

/**
 * 默认优先级 = 中（与后端 PriorityEnum.DEFAULT_CODE 对齐）。
 *
 * @deprecated 【需求一 · 默认值红线】禁止用于任何表单初值 / 提交载荷兜底。
 *   「提交新问题」的优先级必须由用户显式选择，预选中值会导致大量问题被误标为「中」，
 *   统计失真。本常量仅保留给**列表展示端**在后端历史数据 priority 为 null 时
 *   做兜底渲染，新代码请勿引用；如需默认值请改由后端 DDL 的 DEFAULT 1 兜底。
 */
export const DEFAULT_PRIORITY = 1

/**
 * 字典类型编码常量（禁止组件内散落字符串字面量，ARCH §七.6）。
 * 值必须与 dict 表 dict_code / 后端 DictTypeCodeEnum 完全一致。
 */
export const DICT_TYPE = {
  // Phase9：问题类型由 issue_type 独立表迁入字典维护，
  // 值与后端 Constants.DICT_TYPE_ISSUE_TYPE 及迁移 SQL 的 dict_code 严格一致
  ISSUE_TYPE: 'ISSUE_TYPE',
  ISSUE_SOURCE: 'ISSUE_SOURCE',
  ISSUE_STATUS: 'ISSUE_STATUS',
  ISSUE_PRIORITY: 'ISSUE_PRIORITY',
  ISSUE_SEVERITY: 'ISSUE_SEVERITY'
}

/**
 * 默认来源编码（与后端 Constants / Phase7 种子一致：issue.source 为空时兜底为 SYSTEM）。
 */
export const DEFAULT_SOURCE_CODE = 'SYSTEM'

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
  '/user/profile': 'menu.user.profile',
  '/admin/index': 'menu.admin.overview',
  // Phase7：/admin/issues 由「问题管理」降级为业务管理下的「问题列表」子菜单
  '/admin/business': 'menu.admin.business',
  '/admin/issues': 'menu.admin.issueList',
  '/admin/dicts': 'menu.admin.dict',
  '/admin/field-configs': 'menu.admin.fieldConfigs',
  '/admin/infra': 'menu.admin.infra',
  '/admin/infra/file': 'menu.admin.infraFile',
  '/admin/infra/file/config': 'menu.admin.infraFileConfig',
  '/admin/infra/file/list': 'menu.admin.infraFileList',
  '/admin/infra/config': 'menu.admin.infraConfig',
  '/admin/infra/redis': 'menu.admin.infraRedis',
  '/admin/infra/job': 'menu.admin.infraJob',
  '/admin/project': 'menu.admin.projectGroup',
  '/admin/projects': 'menu.admin.projects',
  // Phase8 W1 #8：/admin/modules（模块配置）已下线，映射一并移除
  '/admin/flow': 'menu.admin.flowGroup',
  '/admin/flow-monitor': 'menu.admin.flowMonitor',
  '/admin/flow-config': 'menu.admin.flowConfig',
  '/admin/system': 'menu.admin.system',
  '/admin/system/users': 'menu.admin.users',
  '/admin/system/organizations': 'menu.admin.organizations',
  '/admin/system/menus': 'menu.admin.menus',
  '/admin/system/roles': 'menu.admin.roles',
  '/admin/system/site': 'menu.admin.siteSettings',
  // Phase10 需求三：原「备份设置」(/admin/system/settings) 更名为「数据管理」。
  // 菜单表 path 已由 scripts/V20260803_data_management.sql 改写，
  // 旧 path 的映射保留一行是为了兼容尚未执行迁移脚本的环境，
  // 两者都指向同一个 dataManagement.menu，侧边栏不会再出现「备份设置」。
  '/admin/system/data-management': 'dataManagement.menu',
  '/admin/system/settings': 'dataManagement.menu'
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
 * 优先级文案（Phase7 决策 B：固定枚举，与 severity 同步渲染，不走字典）。
 * @param {number|string} code 0 高 / 1 中 / 2 低
 * @returns {string}
 */
export function priorityLabelI18n(code) {
  const n = Number(code)
  if (!PRIORITY_CODES.includes(n)) return t('enum.priority.unknown')
  return t('enum.priority.' + n)
}

/**
 * 响应式优先级下拉选项（语言切换自动更新）。
 * @returns {import('vue').ComputedRef<Array<{value:number,label:string}>>}
 */
export function usePriorityOptions() {
  return computed(() =>
    PRIORITY_CODES.map((code) => ({ value: code, label: t('enum.priority.' + code) }))
  )
}

/**
 * 字典项显示名：优先 i18n 映射 t('dict.value.{typeCode}.{code}')，
 * 未命中回退数据库 name；停用项追加「(已停用)」后缀。
 * @param {string} typeCode 字典类型编码
 * @param {{name?:string,code?:string,enabled?:boolean}} item 字典项
 * @returns {string}
 */
export function dictLabelI18n(typeCode, item) {
  if (!item) return ''
  const key = typeCode && item.code ? `dict.value.${typeCode}.${item.code}` : null
  const label = key && i18n.global.te(key) ? t(key) : item.name || ''
  return item.enabled === false ? label + t('dict.disabledSuffix') : label
}

/**
 * 响应式字典下拉选项（全站唯一数据源，走 store/dict.js 分片缓存）。
 *
 * @deprecated Phase7 起请直接使用 {@link useDictCodeOptions}。
 * 本函数仅作向后兼容别名保留（当前全站 0 调用方），语义已与 useDictCodeOptions
 * 完全一致：value 取 **选项编码 code** 而非 id——issue.source 落库即为 item_code，
 * 保持下拉 value 与后端存储口径一致，避免提交前再做一次 id→code 转换。
 *
 * @param {string} typeCode 字典类型编码，如 ISSUE_SOURCE
 * @param {boolean} [includeDisabled=false] 是否包含停用项（筛选场景传 true）
 * @returns {import('vue').ComputedRef<Array<{value:string,label:string,enabled:boolean,id:number}>>}
 */
export function useDictOptions(typeCode, includeDisabled = false) {
  return useDictCodeOptions(typeCode, includeDisabled)
}

/**
 * 响应式字典下拉选项（**以 item_code 为 value**）。
 *
 * <p>Phase7 起 `issue.source` 落库的是 `dict_item.item_code`（如 SYSTEM / API_IMPORT）
 * 而非字典项 id，因此来源类下拉必须用本函数而非 {@link useDictOptions}。</p>
 *
 * @param {string} typeCode 字典类型编码，如 ISSUE_SOURCE
 * @param {boolean} [includeDisabled=false] 是否包含停用项（筛选场景传 true）
 * @returns {import('vue').ComputedRef<Array<{value:string,label:string,enabled:boolean,id:number}>>}
 */
export function useDictCodeOptions(typeCode, includeDisabled = false) {
  const store = useDictStore()
  if (includeDisabled) {
    store.fetchAllOptions(typeCode)
  } else {
    store.fetchOptions(typeCode)
  }
  return computed(() => {
    const rows = includeDisabled ? store.allOptionsOf(typeCode) : store.optionsOf(typeCode)
    return rows.map((row) => ({
      value: row.code,
      label: dictLabelI18n(typeCode, row),
      enabled: row.enabled !== false,
      id: row.id
    }))
  })
}

/**
 * 按 item_code 取字典项显示名：优先 i18n（t('dict.value.{typeCode}.{code}')），
 * 未命中回退 store 缓存里的数据库 name，再未命中回退 code 原值。
 *
 * <p>列表回显专用：后端 IssueVO 已带 `sourceDesc`，调用方应优先用 `sourceDesc`
 * 作为 fallback 传入，避免字典分片未加载时显示空白。</p>
 *
 * @param {string} typeCode 字典类型编码
 * @param {string} code 字典项编码
 * @param {string} [fallback] 后端返回的名称兜底
 * @returns {string}
 */
export function dictCodeLabelI18n(typeCode, code, fallback) {
  if (!code) return fallback || ''
  const key = `dict.value.${typeCode}.${code}`
  if (i18n.global.te(key)) return t(key)
  const store = useDictStore()
  const hit = store.optionByCode(typeCode, code)
  if (hit && hit.name) return hit.name
  return fallback || code
}

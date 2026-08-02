/**
 * 动态字段控件元数据与工具（纯常量 + 纯函数，无副作用、无请求）。
 *
 * <p>常量与后端保持逐字对齐：</p>
 * <ul>
 *   <li>{@link FIELD_TYPES} 对齐 {@code enums/FieldType.java}（大写枚举名）</li>
 *   <li>{@link DISPLAY_TYPES} 对齐 {@code field_config.display_type} 与
 *       {@code RefSourceService#listEnabled}（**小写** select / tree）</li>
 *   <li>{@link SYSTEM_FIELD_EDITABLE_ATTRS} 对齐 {@code FieldConfigService#update} 内置字段白名单</li>
 * </ul>
 */

/** 字段类型枚举（逐字对齐后端 FieldType.java） */
export const FIELD_TYPES = ['TEXT', 'NUMBER', 'DATE', 'DATETIME', 'DICT', 'REF']

/**
 * REF 展示形式。
 * <p>注意：后端落库与 ref-sources 兜底值均为**小写** select / tree
 * （见 V20260806_dynamic_field.sql 种子 与 RefSourceService 第 60 行），
 * 故此处使用小写；比较时一律走 {@link isTreeDisplay} 做大小写不敏感判定。</p>
 */
export const DISPLAY_TYPES = ['select', 'tree']

/**
 * 内置字段（system=true）允许编辑的属性白名单。
 * <p>与 {@code FieldConfigService#update} 的 system 分支逐字对齐：该分支仅放行
 * name / i18nKey / required / sort / placeholder / span / enabled 七项，
 * 其余（含 visibleInList、searchable）均硬拦截不落库。</p>
 */
export const SYSTEM_FIELD_EDITABLE_ATTRS = [
  'name',
  'i18nKey',
  'required',
  'placeholder',
  'span',
  'sort',
  'enabled'
]

/** 各字段类型的专属属性（用于配置抽屉动态显隐） */
export const TYPE_ATTRS = {
  TEXT: ['multiline', 'maxLength'],
  NUMBER: ['minVal', 'maxVal', 'decimalScale'],
  DATE: [],
  DATETIME: [],
  DICT: ['dictCode', 'multiSelect', 'dependsOn', 'dependsParam'],
  REF: ['refSource', 'displayType', 'multiSelect', 'dependsOn', 'dependsParam']
}

/** 提交请求中需由 Boolean 转 Integer(0/1) 的属性（后端 FieldConfigReq 为 Integer） */
export const BOOLEAN_INT_ATTRS = [
  'required',
  'multiline',
  'multiSelect',
  'enabled',
  'visibleInList',
  'searchable'
]

/** 与类型无关的通用可写属性 */
export const BASE_ATTRS = [
  'sectionId',
  'code',
  'name',
  'i18nKey',
  'type',
  'required',
  'placeholder',
  'defaultValue',
  'span',
  'typeScope',
  'sort',
  'enabled',
  'visibleInList',
  'searchable'
]

/**
 * 取某字段类型生效的专属属性列表。
 *
 * @param {string} type 字段类型（大小写不敏感）
 * @returns {string[]} 属性名数组；未知类型返回空数组
 */
export function attrsOfType(type) {
  if (!type) return []
  const key = String(type).trim().toUpperCase()
  return TYPE_ATTRS[key] ? [...TYPE_ATTRS[key]] : []
}

/**
 * 判断某属性对指定类型是否生效（通用属性恒为 true）。
 *
 * @param {string} type 字段类型
 * @param {string} attr 属性名
 * @returns {boolean}
 */
export function isAttrActive(type, attr) {
  if (BASE_ATTRS.includes(attr)) return true
  return attrsOfType(type).includes(attr)
}

/**
 * 判断 REF 字段是否以树形展示。
 * <p>优先取字段自身 displayType，为空时按引用源 registry 的兜底 displayType 判定。</p>
 *
 * @param {object} field 字段配置（FieldConfigVO）
 * @param {object} [refSourceMeta] 引用源元数据（RefSourceVO），可空
 * @returns {boolean} true 为树形
 */
export function isTreeDisplay(field, refSourceMeta) {
  if (!field) return false
  const own = field.displayType
  if (own) return String(own).trim().toLowerCase() === 'tree'
  if (refSourceMeta) {
    const fallback = refSourceMeta.displayType || refSourceMeta.queryType
    if (fallback) return String(fallback).trim().toLowerCase() === 'tree'
  }
  return false
}

/**
 * 判断字段是否为多选。
 *
 * @param {object} field 字段配置
 * @returns {boolean}
 */
export function isMultiSelect(field) {
  return !!(field && field.multiSelect === true)
}

/**
 * 校验触发时机：文本/数值走 blur，选择类走 change。
 *
 * @param {string} type 字段类型
 * @returns {'blur'|'change'}
 */
export function triggerOfType(type) {
  const key = String(type || '').trim().toUpperCase()
  return key === 'TEXT' || key === 'NUMBER' ? 'blur' : 'change'
}

/**
 * 由单个字段配置推导 Element Plus 校验规则数组。
 *
 * <p>推导来源全部来自 schema，不含任何硬编码字段名：</p>
 * <ul>
 *   <li>{@code required=true} → 必填规则</li>
 *   <li>TEXT + {@code maxLength} → 最大长度规则</li>
 *   <li>NUMBER + {@code minVal}/{@code maxVal} → 数值范围规则</li>
 * </ul>
 *
 * @param {object} field 字段配置（FieldConfigVO）
 * @param {Function} t vue-i18n 的 t 函数
 * @returns {Array<object>} Element Plus rules 数组（无规则时为空数组）
 */
export function buildRules(field, t) {
  const rules = []
  if (!field) return rules

  const type = String(field.type || '').trim().toUpperCase()
  const trigger = triggerOfType(type)
  const translate = typeof t === 'function' ? t : (key) => key

  if (field.required === true) {
    rules.push({
      required: true,
      message: translate('common.msg.required'),
      trigger: type === 'TEXT' || type === 'NUMBER' ? ['blur', 'change'] : 'change'
    })
  }

  if (type === 'TEXT' && field.maxLength != null && Number(field.maxLength) > 0) {
    const max = Number(field.maxLength)
    rules.push({
      max,
      message: translate('fieldConfig.rules.maxLengthExceed', { max }),
      trigger
    })
  }

  if (type === 'NUMBER') {
    if (field.minVal != null && field.minVal !== '') {
      const min = Number(field.minVal)
      if (!Number.isNaN(min)) {
        rules.push({
          type: 'number',
          min,
          message: translate('fieldConfig.rules.minValExceed', { min }),
          trigger
        })
      }
    }
    if (field.maxVal != null && field.maxVal !== '') {
      const max = Number(field.maxVal)
      if (!Number.isNaN(max)) {
        rules.push({
          type: 'number',
          max,
          message: translate('fieldConfig.rules.maxValExceed', { max }),
          trigger
        })
      }
    }
  }

  return rules
}

/**
 * 判断依赖候选是否合法（单级依赖铁律，与后端 DFS 环检测配合）。
 *
 * <p>合法条件：非自身 + 非多选 + 自身无 dependsOn + 类型为 DICT 或 REF。</p>
 *
 * @param {object} candidate 候选字段（FieldConfigVO）
 * @param {string} selfCode 当前编辑字段的 code
 * @returns {boolean}
 */
export function isValidDependsCandidate(candidate, selfCode) {
  if (!candidate || !candidate.code) return false
  if (selfCode && candidate.code === selfCode) return false
  if (candidate.multiSelect === true) return false
  if (candidate.dependsOn) return false
  const type = String(candidate.type || '').trim().toUpperCase()
  return type === 'DICT' || type === 'REF'
}

/**
 * 判断「上游值」是否为空（用于联动字段的 gating）。
 *
 * @param {*} value 上游字段当前值
 * @returns {boolean} true 表示为空
 */
export function isBlankParentValue(value) {
  if (value == null || value === '') return true
  return Array.isArray(value) && value.length === 0
}

/**
 * 【需求二】字段配置弹窗的「区块 → 字段」编排表。
 *
 * <p>把弹窗的信息架构从模板里抽出来，集中声明为纯数据，好处有三：</p>
 * <ul>
 *   <li>字段顺序 / 归属区块调整只改这一处，模板不动；</li>
 *   <li>每个属性的问号提示 key 与字段一一绑定，杜绝「加了字段忘了加提示」；</li>
 *   <li>可被单测直接引用，校验 i18n key 是否成对齐全。</li>
 * </ul>
 *
 * <p>约定：i18n key 前缀统一为 {@code fieldConfig.section.*} / {@code fieldConfig.label.*} /
 * {@code fieldConfig.tip.*}，zh-CN 与 en-US 必须成对存在。</p>
 */

/** 区块编码：基础属性 */
export const SECTION_BASE = 'base'
/** 区块编码：类型属性 */
export const SECTION_TYPE = 'type'
/** 区块编码：高级属性 */
export const SECTION_ADVANCED = 'advanced'

/**
 * 三大区块的展示顺序与文案 key。
 * <p>{@code descKey} 为区块下方的一句话说明，帮助管理员理解本区块管什么。</p>
 */
export const FIELD_FORM_SECTIONS = Object.freeze([
  Object.freeze({
    code: SECTION_BASE,
    titleKey: 'fieldConfig.section.base',
    descKey: 'fieldConfig.section.baseDesc'
  }),
  Object.freeze({
    code: SECTION_TYPE,
    titleKey: 'fieldConfig.section.type',
    descKey: 'fieldConfig.section.typeDesc'
  }),
  Object.freeze({
    code: SECTION_ADVANCED,
    titleKey: 'fieldConfig.section.advanced',
    descKey: 'fieldConfig.section.advancedDesc'
  })
])

/**
 * 字段 → 所属区块 + 文案 key 映射。
 *
 * <p>顺序即渲染顺序（模板按 {@link fieldsOfSection} 的返回顺序逐项渲染）。
 * 重排原则：</p>
 * <ul>
 *   <li><b>基础属性</b>：先「它是谁」（区域 / 名称 / 编码 / 国际化 Key），
 *       再「它长什么样」（类型 / 栅格 / 占位 / 默认值），最后「是否必填」；</li>
 *   <li><b>类型属性</b>：按 TYPE_ATTRS 的实际生效范围动态显隐，
 *       文本 → 数值 → 字典/引用 → 联动，从简单到复杂；</li>
 *   <li><b>高级属性</b>：排序与开关类（列表列 / 查询条件 / 启用状态）收口在最后。</li>
 * </ul>
 */
export const FIELD_FORM_ITEMS = Object.freeze([
  /* ---------------- 基础属性 ---------------- */
  Object.freeze({ code: 'sectionId', section: SECTION_BASE, prop: 'sectionId' }),
  Object.freeze({ code: 'name', section: SECTION_BASE, prop: 'name' }),
  Object.freeze({ code: 'code', section: SECTION_BASE, prop: 'code' }),
  Object.freeze({ code: 'i18nKey', section: SECTION_BASE, prop: 'i18nKey' }),
  Object.freeze({ code: 'type', section: SECTION_BASE, prop: 'type' }),
  Object.freeze({ code: 'span', section: SECTION_BASE, prop: 'span' }),
  Object.freeze({ code: 'placeholder', section: SECTION_BASE, prop: 'placeholder' }),
  Object.freeze({ code: 'defaultValue', section: SECTION_BASE, prop: 'defaultValue' }),
  Object.freeze({ code: 'required', section: SECTION_BASE, prop: 'required' }),

  /* ---------------- 类型属性（随 type 动态显隐） ---------------- */
  Object.freeze({ code: 'multiline', section: SECTION_TYPE, prop: 'multiline', typeAttr: true }),
  Object.freeze({ code: 'maxLength', section: SECTION_TYPE, prop: 'maxLength', typeAttr: true }),
  Object.freeze({ code: 'minVal', section: SECTION_TYPE, prop: 'minVal', typeAttr: true }),
  Object.freeze({ code: 'maxVal', section: SECTION_TYPE, prop: 'maxVal', typeAttr: true }),
  Object.freeze({
    code: 'decimalScale',
    section: SECTION_TYPE,
    prop: 'decimalScale',
    typeAttr: true
  }),
  Object.freeze({ code: 'dictCode', section: SECTION_TYPE, prop: 'dictCode', typeAttr: true }),
  Object.freeze({ code: 'refSource', section: SECTION_TYPE, prop: 'refSource', typeAttr: true }),
  Object.freeze({
    code: 'displayType',
    section: SECTION_TYPE,
    prop: 'displayType',
    typeAttr: true
  }),
  Object.freeze({
    code: 'multiSelect',
    section: SECTION_TYPE,
    prop: 'multiSelect',
    typeAttr: true
  }),
  Object.freeze({ code: 'dependsOn', section: SECTION_TYPE, prop: 'dependsOn', typeAttr: true }),
  Object.freeze({
    code: 'dependsParam',
    section: SECTION_TYPE,
    prop: 'dependsParam',
    typeAttr: true
  }),

  /* ---------------- 高级属性 ---------------- */
  Object.freeze({ code: 'sort', section: SECTION_ADVANCED, prop: 'sort' }),
  Object.freeze({ code: 'visibleInList', section: SECTION_ADVANCED, prop: 'visibleInList' }),
  Object.freeze({ code: 'searchable', section: SECTION_ADVANCED, prop: 'searchable' }),
  Object.freeze({ code: 'enabled', section: SECTION_ADVANCED, prop: 'enabled' })
])

/**
 * 取某属性的标签 i18n key。
 *
 * @param {string} code 属性名（同 fieldForm 的键）
 * @returns {string} 形如 fieldConfig.label.name
 */
export function labelKeyOf(code) {
  return `fieldConfig.label.${code}`
}

/**
 * 取某属性的问号提示 i18n key。
 *
 * @param {string} code 属性名（同 fieldForm 的键）
 * @returns {string} 形如 fieldConfig.tip.name
 */
export function tipKeyOf(code) {
  return `fieldConfig.tip.${code}`
}

/**
 * 取指定区块下的字段编排项（保持声明顺序）。
 *
 * @param {string} sectionCode 区块编码
 * @returns {Array<object>} 编排项数组
 */
export function fieldsOfSection(sectionCode) {
  return FIELD_FORM_ITEMS.filter((item) => item.section === sectionCode)
}

/**
 * 全部「类型属性」的属性名集合（用于类型切换时清理脏值）。
 *
 * @returns {Array<string>} 属性名数组
 */
export function typeAttrCodes() {
  return FIELD_FORM_ITEMS.filter((item) => item.typeAttr === true).map((item) => item.code)
}

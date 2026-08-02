import request from './request'
import {
  BOOLEAN_INT_ATTRS,
  BASE_ATTRS,
  SYSTEM_FIELD_EDITABLE_ATTRS,
  attrsOfType
} from '@/utils/fieldControls'

/**
 * 动态字段配置 API（/api/field-configs，9 接口）。
 *
 * <p>契约来源：{@code FieldConfigController.java}（T03 已冻结）。</p>
 * <p><b>类型陷阱</b>：响应 {@code FieldConfigVO} 的开关位为 Boolean，
 * 而请求 {@code FieldConfigReq} 的同名位为 Integer(0/1)；
 * 一律经 {@link normalizeFieldReq} 统一转换后再提交，禁止在组件里散落转换逻辑。</p>
 */

// ============================ 查询 ============================

/**
 * 表单渲染契约（登录即可）：GET /api/field-configs/schema?typeScope=
 *
 * @param {string} [typeScope] 生效范围，缺省由后端按 GLOBAL 兜底
 * @returns {Promise<object>} FieldSchemaVO
 */
export function getFieldSchema(typeScope) {
  const params = {}
  if (typeScope) params.typeScope = typeScope
  return request.get('/field-configs/schema', { params })
}

/**
 * REF 候选项（登录即可）：GET /api/field-configs/ref-options
 *
 * @param {string} refSource 引用源白名单编码（必填）
 * @param {string|number} [parentValue] 依赖源当前值（联动过滤）
 * @param {string} [keyword] 模糊搜索关键字
 * @returns {Promise<Array>} RefOptionVO[]（tree 类型含 children）
 */
export function getRefOptions(refSource, parentValue, keyword) {
  const params = { refSource }
  if (parentValue != null && parentValue !== '') params.parentValue = parentValue
  if (keyword) params.keyword = keyword
  return request.get('/field-configs/ref-options', { params })
}

/**
 * 管理页树形数据：GET /api/field-configs/tree
 *
 * <p><b>注意</b>：后端返回的是「区域行 + 其下字段行」交替的<b>扁平</b>数组（携带 parentId），
 * 并非嵌套 children 结构；调用方需自行组装（见 {@link buildFieldTree}）。</p>
 *
 * @returns {Promise<Array>} FieldNodeVO[] 扁平数组
 */
export function getFieldTree() {
  return request.get('/field-configs/tree')
}

/**
 * 引用源下拉：GET /api/field-configs/ref-sources
 *
 * @returns {Promise<Array>} RefSourceVO[]
 */
export function getRefSources() {
  return request.get('/field-configs/ref-sources')
}

/**
 * 字段详情（编辑回显）：GET /api/field-configs/{id}
 *
 * @param {number} id 字段 id
 * @returns {Promise<object>} FieldConfigVO
 */
export function getFieldConfig(id) {
  return request.get(`/field-configs/${id}`)
}

// ============================ 写操作 ============================

/**
 * 新增字段：POST /api/field-configs
 *
 * @param {object} data 已经过 {@link normalizeFieldReq} 处理的请求体
 * @returns {Promise<number>} 新字段 id
 */
export function createFieldConfig(data) {
  return request.post('/field-configs', data)
}

/**
 * 编辑字段：PUT /api/field-configs/{id}
 * <p>后端为「部分字段」语义（null 忽略），code/type 恒被忽略/校验。</p>
 *
 * @param {number} id 字段 id
 * @param {object} data 已经过 {@link normalizeFieldReq} 处理的请求体
 * @returns {Promise<void>}
 */
export function updateFieldConfig(id, data) {
  return request.put(`/field-configs/${id}`, data)
}

/**
 * 删除字段：DELETE /api/field-configs/{id}（内置字段后端硬拦截）
 *
 * @param {number} id 字段 id
 * @returns {Promise<void>}
 */
export function deleteFieldConfig(id) {
  return request.delete(`/field-configs/${id}`)
}

/**
 * 字段启停：POST /api/field-configs/{id}/toggle
 *
 * <p><b>契约修正</b>：后端签名为 {@code toggle(@PathVariable Long id, @Valid @RequestBody StatusToggleReq req)}，
 * 即<b>必须</b>携带 body {@code { enabled: Boolean }}，并非无参翻转；
 * 缺失 body 会触发 @NotNull 校验失败。</p>
 *
 * @param {number} id 字段 id
 * @param {boolean} enabled 目标状态（true 启用 / false 停用）
 * @returns {Promise<void>}
 */
export function toggleFieldConfig(id, enabled) {
  return request.post(`/field-configs/${id}/toggle`, { enabled: !!enabled })
}

// ============================ 工具 ============================

/**
 * Boolean → Integer(0/1)；已是数字则原样归一，null/undefined 透传为 undefined（后端按未传处理）。
 *
 * @param {*} value 原始值
 * @returns {number|undefined}
 */
function toInt01(value) {
  if (value == null || value === '') return undefined
  if (typeof value === 'boolean') return value ? 1 : 0
  return Number(value) ? 1 : 0
}

/**
 * 把表单模型规整为后端 {@code FieldConfigReq}。
 *
 * <p>处理三件事：</p>
 * <ol>
 *   <li>开关位 Boolean → Integer(0/1)（{@code required/multiline/multiSelect/enabled/visibleInList/searchable}）</li>
 *   <li>按 {@code type} 裁剪专属属性：不适用于当前类型的属性一律不提交，交由后端 sanitize 置空</li>
 *   <li>内置字段（{@code system=true}）仅提交白名单属性，避免触发后端硬拦截分支的无效写入</li>
 * </ol>
 *
 * @param {object} form 表单模型（可直接来自 FieldConfigVO 回显）
 * @param {object} [options] 选项
 * @param {boolean} [options.systemField=false] 是否为内置字段（走白名单裁剪）
 * @returns {object} 可直接提交的请求体
 */
export function normalizeFieldReq(form, options = {}) {
  const source = form || {}
  const { systemField = false } = options
  const payload = {}

  // 内置字段：仅回传后端白名单放行的 7 项
  if (systemField) {
    SYSTEM_FIELD_EDITABLE_ATTRS.forEach((key) => {
      const raw = source[key]
      if (raw === undefined) return
      if (BOOLEAN_INT_ATTRS.includes(key)) {
        const int01 = toInt01(raw)
        if (int01 !== undefined) payload[key] = int01
        return
      }
      payload[key] = raw
    })
    return payload
  }

  const type = String(source.type || '').trim().toUpperCase()
  const allowed = new Set([...BASE_ATTRS, ...attrsOfType(type)])

  allowed.forEach((key) => {
    const raw = source[key]
    if (raw === undefined) return

    if (BOOLEAN_INT_ATTRS.includes(key)) {
      const int01 = toInt01(raw)
      // 开关位缺省按 0 提交，避免后端 null 忽略导致「关不掉」
      payload[key] = int01 === undefined ? 0 : int01
      return
    }

    // 字符串属性：空串照常提交，用于清空既有配置（后端为 != null 语义）
    payload[key] = raw === null ? '' : raw
  })

  // type / typeScope 兜底
  if (payload.type) payload.type = type
  if (!payload.typeScope) payload.typeScope = source.typeScope || 'GLOBAL'

  return payload
}

/**
 * 把 {@link getFieldTree} 返回的扁平数组组装为 el-table 需要的 children 树。
 *
 * <p>区域为父节点（{@code nodeType==='section'}，parentId 为 null），
 * 字段按 {@code parentId} 归入所属区域；行主键使用 {@code `${nodeType}-${id}`} 防父子撞号。</p>
 *
 * @param {Array} nodes FieldNodeVO 扁平数组
 * @returns {Array} 嵌套树（每个节点附加 rowKey 字段）
 */
export function buildFieldTree(nodes) {
  const list = Array.isArray(nodes) ? nodes : []
  const sections = []
  const sectionMap = new Map()

  list.forEach((node) => {
    if (node && node.nodeType === 'section') {
      const section = { ...node, rowKey: `section-${node.id}`, children: [] }
      sections.push(section)
      sectionMap.set(node.id, section)
    }
  })

  list.forEach((node) => {
    if (!node || node.nodeType === 'section') return
    const parent = sectionMap.get(node.parentId)
    const child = { ...node, rowKey: `field-${node.id}` }
    if (parent) {
      parent.children.push(child)
    } else {
      // 孤儿字段（区域被删/数据异常）：置于顶层，保证不丢数据
      sections.push(child)
    }
  })

  // el-table 树形要求无子节点时不下发空数组，否则会渲染多余展开箭头
  sections.forEach((section) => {
    if (Array.isArray(section.children) && section.children.length === 0) {
      delete section.children
    }
  })

  return sections
}

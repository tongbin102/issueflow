/**
 * 列偏好管理 composable（单例模式）。
 *
 * <p>管理问题列表页的列显隐与排序偏好，持久化到 localStorage
 * （key: {@code issueflow:column-preferences}）。</p>
 *
 * <p>采用模块级单例状态，确保 IssueTable（列渲染）与
 * UserIssueList / ColumnConfigDrawer（偏好编辑）共享同一份响应式数据。</p>
 *
 * <p>偏好语义：</p>
 * <ul>
 *   <li>{@code visibleKeys} 为空数组 → 默认全部显示（首次使用 / 重置后）</li>
 *   <li>{@code visibleKeys} 非空 → 仅显示数组中包含的列</li>
 *   <li>{@code orderKeys} 为空数组 → 按内置列默认顺序 + 自定义列追加顺序</li>
 *   <li>{@code orderKeys} 非空 → 按数组顺序排列，未包含的列追加到末尾</li>
 * </ul>
 */
import { ref } from 'vue'

/** localStorage 存储键 */
const STORAGE_KEY = 'issueflow:column-preferences'

/**
 * 内置列定义（与 IssueTable.vue 模板中的列一一对应）。
 * labelKey 为 vue-i18n 的翻译路径，由消费方自行 t() 解析。
 */
export const BUILTIN_COLUMN_DEFS = [
  { key: 'issueNo', labelKey: 'issue.list.col.issueNo', isCustom: false },
  { key: 'title', labelKey: 'issue.list.col.title', isCustom: false },
  { key: 'type', labelKey: 'issue.list.col.type', isCustom: false },
  { key: 'source', labelKey: 'issue.list.col.source', isCustom: false },
  { key: 'priority', labelKey: 'issue.list.col.priority', isCustom: false },
  { key: 'severity', labelKey: 'issue.list.col.severity', isCustom: false },
  { key: 'status', labelKey: 'issue.list.col.status', isCustom: false },
  { key: 'project', labelKey: 'issue.list.col.project', isCustom: false },
  { key: 'reporter', labelKey: 'issue.list.col.reporter', isCustom: false },
  { key: 'assignee', labelKey: 'issue.list.col.assignee', isCustom: false },
  { key: 'createdAt', labelKey: 'issue.list.col.createdAt', isCustom: false }
]

/** 内置列 key 集合（快速查找） */
export const BUILTIN_COLUMN_KEYS = BUILTIN_COLUMN_DEFS.map((c) => c.key)

/**
 * 从 localStorage 读取列偏好。
 * @returns {{ visibleColumns: string[], columnOrder: string[] } | null}
 */
function loadFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    return {
      visibleColumns: Array.isArray(parsed.visibleColumns) ? parsed.visibleColumns : [],
      columnOrder: Array.isArray(parsed.columnOrder) ? parsed.columnOrder : []
    }
  } catch (e) {
    return null
  }
}

/**
 * 将列偏好写入 localStorage。
 * @param {{ visibleColumns: string[], columnOrder: string[] }} prefs
 */
function saveToStorage(prefs) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
  } catch (e) {
    // localStorage 不可用（隐私模式等），静默失败
  }
}

// ============================ 模块级单例状态 ============================

const _stored = loadFromStorage()
/** 可见列 key 数组（空 = 全部可见） */
const visibleKeys = ref(_stored ? _stored.visibleColumns : [])
/** 列排序 key 数组（空 = 默认顺序） */
const orderKeys = ref(_stored ? _stored.columnOrder : [])

// ============================ Composable ============================

/**
 * 列偏好管理 composable。
 *
 * @returns {{
 *   visibleKeys: import('vue').Ref<string[]>,
 *   orderKeys: import('vue').Ref<string[]>,
 *   isColumnVisible: (key: string) => boolean,
 *   getOrderedKeys: (allKeys: string[]) => string[],
 *   apply: (visible: string[], order: string[]) => void,
 *   reset: () => void
 * }}
 */
export function useColumnPreferences() {
  /**
   * 判断某列是否可见。
   * @param {string} key 列 key
   * @returns {boolean}
   */
  function isColumnVisible(key) {
    if (visibleKeys.value.length === 0) return true
    return visibleKeys.value.includes(key)
  }

  /**
   * 根据全部可用列 key 和用户排序偏好，返回排好序的 key 数组。
   * 未出现在 orderKeys 中的列追加到末尾（保持原始顺序）。
   *
   * @param {string[]} allKeys 全部可用列 key
   * @returns {string[]} 排好序的列 key 数组
   */
  function getOrderedKeys(allKeys) {
    if (orderKeys.value.length === 0) return [...allKeys]
    const ordered = []
    const seen = new Set()
    // 按用户偏好顺序添加（仅包含当前仍存在的列）
    for (const k of orderKeys.value) {
      if (allKeys.includes(k)) {
        ordered.push(k)
        seen.add(k)
      }
    }
    // 追加未在排序列表中的新列
    for (const k of allKeys) {
      if (!seen.has(k)) {
        ordered.push(k)
      }
    }
    return ordered
  }

  /**
   * 应用新的列偏好并持久化。
   * @param {string[]} visible 可见列 key 数组
   * @param {string[]} order 列排序 key 数组
   */
  function apply(visible, order) {
    visibleKeys.value = [...visible]
    orderKeys.value = [...order]
    saveToStorage({
      visibleColumns: visibleKeys.value,
      columnOrder: orderKeys.value
    })
  }

  /**
   * 重置为默认（全部可见、默认顺序）。
   */
  function reset() {
    visibleKeys.value = []
    orderKeys.value = []
    saveToStorage({ visibleColumns: [], columnOrder: [] })
  }

  return {
    visibleKeys,
    orderKeys,
    isColumnVisible,
    getOrderedKeys,
    apply,
    reset
  }
}

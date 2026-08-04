/**
 * 本地缓存清理工具（Local cache cleaner）。
 *
 * <p>职责：集中封装「清除本平台在当前 origin 下的浏览器缓存数据」的完整链路，
 * 供登录页「清除缓存」按钮调用，也可被后续的「重置偏好」类入口复用。</p>
 *
 * <p>清理范围（精准清理，<b>不使用</b> localStorage.clear()）：</p>
 * <ul>
 *   <li>localStorage 中所有以 {@code if_} <b>或</b> {@code if-} 前缀开头的键
 *       —— if_token / if_user / if_theme / if_app / if_admin_style / if_locale /
 *       if_org_density / if_org_columns / if_project_columns / if-menu-closed-type1；</li>
 *   <li>额外的无 if 前缀业务键 —— {@code issueflow:column-preferences}（表格列偏好）；</li>
 *   <li>整个 sessionStorage；</li>
 *   <li>（可选）传入的 userStore 实例的内存态（token / userInfo / roles / permissions）。</li>
 * </ul>
 *
 * <p>设计约束：本模块<b>不 import store</b>，userStore 以参数注入，
 * 目的有二 —— (1) 彻底规避 utils ↔ store 循环依赖；(2) 逻辑纯函数化，便于单测。</p>
 *
 * <p>与 userStore.logout() 的差异：logout() 会额外发起后端登出请求写 Redis 黑名单，
 * 而登录页「清除缓存」场景下用户往往<b>尚未登录或 token 已失效</b>，
 * 发请求只会带来无谓的网络往返与控制台报错，故此处仅做本地清理。</p>
 */
import { removeToken } from '@/utils/auth'

/**
 * 本平台 localStorage 键前缀集合。
 *
 * <p>历史原因，仓库内实际存在<b>两种</b>前缀写法，二者都属于本平台键，必须同时覆盖：</p>
 * <ul>
 *   <li>{@code if_}（下划线，ARCH §七.2 规范写法）—— if_token / if_user / if_theme /
 *       if_app / if_admin_style / if_locale / if_org_density / if_org_columns /
 *       if_project_columns；</li>
 *   <li>{@code if-}（连字符，历史遗留写法）—— 目前仅
 *       {@code if-menu-closed-type1}（SideMenu.vue 侧边菜单折叠态，属「布局偏好」，
 *       登录页清除缓存的确认文案已向用户承诺清理该类偏好）。</li>
 * </ul>
 *
 * <p>此处按「前缀类」而非「单键白名单」修复：新增任一写法的持久化键都能被自动覆盖，
 * 避免后人再次踩到连字符键漏删的坑。</p>
 */
export const APP_STORAGE_PREFIXES = ['if_', 'if-']

/**
 * 兼容旧引用的单前缀常量（等价于 APP_STORAGE_PREFIXES[0]）。
 * @deprecated 请改用 {@link APP_STORAGE_PREFIXES}，单前缀无法覆盖 {@code if-} 连字符键。
 */
export const APP_STORAGE_PREFIX = APP_STORAGE_PREFIXES[0]

/**
 * 前缀规范之外、仍需一并清理的业务键白名单。
 * 新增此类「无 if 前缀」的持久化键时，请同步登记到这里。
 */
export const EXTRA_STORAGE_KEYS = ['issueflow:column-preferences']

/**
 * 判断浏览器 Storage 是否可用（隐私模式 / 禁用 Cookie 时可能抛异常）。
 * @returns {boolean} 可用返回 true
 */
function isStorageAvailable() {
  try {
    return typeof window !== 'undefined' && !!window.localStorage
  } catch (e) {
    return false
  }
}

/**
 * 判断某个键是否属于本平台（命中任一前缀即算）。
 * @param {string} key localStorage 键名
 * @returns {boolean} 属于本平台返回 true
 */
export function isAppStorageKey(key) {
  if (!key) return false
  return APP_STORAGE_PREFIXES.some((prefix) => key.indexOf(prefix) === 0)
}

/**
 * 收集当前 localStorage 中所有属于本平台的键。
 * <p>先收集再删除，避免遍历过程中因索引位移而漏删。</p>
 * @returns {string[]} 待清理的键名数组（已去重）
 */
export function collectAppCacheKeys() {
  if (!isStorageAvailable()) return []
  const hits = []
  try {
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i)
      if (isAppStorageKey(key)) {
        hits.push(key)
      }
    }
  } catch (e) {
    return []
  }
  EXTRA_STORAGE_KEYS.forEach((key) => {
    if (hits.indexOf(key) === -1 && localStorage.getItem(key) !== null) {
      hits.push(key)
    }
  })
  return hits
}

/**
 * 清除 localStorage 中本平台的全部键。
 * @returns {string[]} 实际被删除的键名数组
 */
export function clearAppStorage() {
  const keys = collectAppCacheKeys()
  if (!keys.length) return []
  const removed = []
  keys.forEach((key) => {
    try {
      localStorage.removeItem(key)
      removed.push(key)
    } catch (e) {
      /* 单键删除失败不阻断其余键的清理 */
    }
  })
  return removed
}

/**
 * 清空 sessionStorage（本 origin 全量，会话级数据无保留价值）。
 * @returns {boolean} 成功返回 true
 */
export function clearSessionStorage() {
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      sessionStorage.clear()
      return true
    }
  } catch (e) {
    /* 隐私模式下静默降级 */
  }
  return false
}

/**
 * 重置 userStore 的内存态（不触发任何网络请求）。
 * <p>字段与 store/user.js 的 state 定义保持一致；store 未传入时安全跳过。</p>
 * @param {object|null} userStore Pinia userStore 实例
 * @returns {boolean} 实际执行了重置返回 true
 */
export function resetUserStoreState(userStore) {
  if (!userStore) return false
  userStore.token = ''
  userStore.userInfo = {}
  userStore.roles = []
  userStore.permissions = []
  userStore.avatarVersion = 0
  return true
}

/**
 * 一键清除本平台全部本地缓存与登录态。
 *
 * @param {object|null} [userStore=null] Pinia userStore 实例；传入则同步重置其内存态
 * @returns {{removedKeys: string[], count: number, sessionCleared: boolean, userReset: boolean}}
 *          清理结果摘要，便于调用方做日志或提示
 */
export function clearAppCache(userStore = null) {
  // 1) 先快照待清理键。必须早于 removeToken()：否则 if_token 会先被删掉，
  //    后续收集不到它，导致返回摘要少算一个键（日志/提示口径失真）。
  const pendingKeys = collectAppCacheKeys()

  // 2) 显式移除登录 token（即便后续遍历因异常中断，登录态也已失效）
  try {
    removeToken()
  } catch (e) {
    /* ignore */
  }

  // 3) 清除 localStorage 中的 if_* / if-* 与额外业务键
  clearAppStorage()

  // 4) 清空 sessionStorage
  const sessionCleared = clearSessionStorage()

  // 5) 重置 userStore 内存态，保证当前页面立即反映「未登录」
  const userReset = resetUserStoreState(userStore)

  // 6) 按「实际已不存在」回算清除结果，而非假定删除成功——
  //    个别键因浏览器限制删除失败时，摘要能如实反映。
  const removedKeys = pendingKeys.filter((key) => {
    try {
      return localStorage.getItem(key) === null
    } catch (e) {
      return false
    }
  })

  return {
    removedKeys,
    count: removedKeys.length,
    sessionCleared,
    userReset
  }
}

export default clearAppCache

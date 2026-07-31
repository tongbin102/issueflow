import request from './request'
import { getConfig as getSysConfigMap, setConfig as setSysConfigItem } from './sysConfig'
import { getSiteConfig } from './site'
import { getFileConfig } from './fileManage'

/**
 * 配置管理 API（Phase7 T6，ARCH §7 Q11 决策：与「网站设置」「文件配置」<b>同源不同视图</b>）。
 *
 * <p>后端没有独立的「配置管理」端点，本模块负责把散落的三处配置源
 * （sys_config 公共键 / site.* / file.*）聚合成统一的条目列表供页面表格渲染；
 * 写操作仍回落到各自的原生接口，避免出现第二个真源。</p>
 *
 * 条目结构：
 * <pre>
 * {
 *   key: 'theme_color',      // 配置键（与后端 Constants 一致）
 *   value: '#409EFF',        // 字符串化后的配置值
 *   group: 'SYS',            // SYS / FLOW / SITE / FILE
 *   editable: true,          // 是否支持在本页内联编辑
 *   builtin: true            // 内置键：禁止删除（本页不提供删除能力）
 * }
 * </pre>
 */

/** 分组常量：与 i18n infra.config.group.* 一一对应 */
export const CONFIG_GROUP = {
  SYS: 'SYS',
  FLOW: 'FLOW',
  SITE: 'SITE',
  FILE: 'FILE'
}

/** 流程开关键名（后端 Constants.CFG_FLOW_*） */
const FLOW_KEYS = {
  rejectEnabled: 'flow_reject_enabled',
  reopenEnabled: 'flow_reopen_enabled'
}

/**
 * 值序列化：对象 / 布尔 / null 统一转成可展示、可回写的字符串。
 * @param {*} value 原始值
 * @returns {string}
 */
function stringify(value) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch (e) {
      return String(value)
    }
  }
  return String(value)
}

/**
 * 聚合读取全部配置条目（settings:update 才可编辑，读取仅需登录后台）。
 *
 * <p>三个来源任一失败都不阻断整体渲染：失败的分组直接跳过，
 * 页面仍能展示其余分组（配置管理页不应因为某一处接口抖动而白屏）。</p>
 *
 * @returns {Promise<Array<{key:string,value:string,group:string,editable:boolean,builtin:boolean}>>}
 */
export async function listConfigEntries() {
  const entries = []

  // 1) sys_config 公共键 + 流程开关
  try {
    const sys = (await getSysConfigMap()) || {}
    entries.push({
      key: 'theme_color',
      value: stringify(sys.themeColor),
      group: CONFIG_GROUP.SYS,
      editable: true,
      builtin: true
    })
    entries.push({
      key: 'layout',
      value: stringify(sys.layout),
      group: CONFIG_GROUP.SYS,
      editable: true,
      builtin: true
    })
    entries.push({
      key: 'menu_config',
      value: stringify(sys.menuConfig),
      group: CONFIG_GROUP.SYS,
      editable: true,
      builtin: true
    })
    const flow = sys.flow || {}
    entries.push({
      key: FLOW_KEYS.rejectEnabled,
      value: stringify(flow.rejectEnabled === true),
      group: CONFIG_GROUP.FLOW,
      editable: true,
      builtin: true
    })
    entries.push({
      key: FLOW_KEYS.reopenEnabled,
      value: stringify(flow.reopenEnabled === true),
      group: CONFIG_GROUP.FLOW,
      editable: true,
      builtin: true
    })
  } catch (e) {
    // 静默跳过该分组
  }

  // 2) site.*（编辑入口在「系统管理 > 网站设置」，本页只读展示）
  try {
    const site = (await getSiteConfig()) || {}
    Object.keys(site).forEach((key) => {
      entries.push({
        key,
        value: stringify(site[key]),
        group: CONFIG_GROUP.SITE,
        editable: false,
        builtin: true
      })
    })
  } catch (e) {
    // 静默跳过该分组
  }

  // 3) file.*（编辑入口在「基础设施 > 文件管理 > 文件配置」，本页只读展示）
  try {
    const file = (await getFileConfig()) || {}
    entries.push({
      key: 'file.storage_type',
      value: stringify(file.storageType),
      group: CONFIG_GROUP.FILE,
      editable: false,
      builtin: true
    })
    entries.push({
      key: 'file.storage_root',
      value: stringify(file.storageRoot),
      group: CONFIG_GROUP.FILE,
      editable: false,
      builtin: true
    })
    entries.push({
      key: 'file.max_size_mb',
      value: stringify(file.maxSizeMb),
      group: CONFIG_GROUP.FILE,
      editable: false,
      builtin: true
    })
    entries.push({
      key: 'file.allowed_exts',
      value: stringify(file.allowedExts),
      group: CONFIG_GROUP.FILE,
      editable: false,
      builtin: true
    })
  } catch (e) {
    // 静默跳过该分组
  }

  return entries
}

/**
 * 保存单个可编辑配置项（settings:update）：走 PUT /api/sys/config。
 * @param {string} configKey 配置键
 * @param {string} configValue 配置值
 * @returns {Promise<void>}
 */
export function saveConfigEntry(configKey, configValue) {
  return setSysConfigItem({ configKey, configValue })
}

/**
 * 读取原始 sys_config Map（供其它页面复用，避免重复实现）。
 * @returns {Promise<Object>}
 */
export function getRawSysConfig() {
  return request.get('/sys/config')
}

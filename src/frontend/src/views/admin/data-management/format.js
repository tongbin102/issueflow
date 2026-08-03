/**
 * 数据管理页共用的格式化工具（Phase10 需求三）。
 *
 * 抽成独立模块而不是各组件自己写一遍：备份体积与耗时在列表、
 * 恢复确认、详情三处都要展示，重复实现迟早会出现「列表显示 1.5 GB、
 * 详情显示 1536 MB」这种前后不一致。
 */

/** 体积单位阶梯 */
const SIZE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB']

/**
 * 把字节数格式化为人类可读体积。
 *
 * @param {number|string|null|undefined} bytes 字节数
 * @returns {string} 形如 `12.34 MB`；无效值返回 `-`
 */
export function formatSize(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 0) return '-'
  if (value === 0) return '0 B'

  let size = value
  let index = 0
  while (size >= 1024 && index < SIZE_UNITS.length - 1) {
    size /= 1024
    index += 1
  }
  // 字节级别不保留小数，看着更干净
  const fixed = index === 0 ? String(Math.round(size)) : size.toFixed(2)
  return `${fixed} ${SIZE_UNITS[index]}`
}

/**
 * 把毫秒耗时格式化为 `HH:mm:ss` / `mm:ss`。
 *
 * @param {number|string|null|undefined} ms 毫秒
 * @returns {string} 形如 `02:35`；无效或为 0 时返回 `-`
 */
export function formatDuration(ms) {
  const value = Number(ms)
  if (!Number.isFinite(value) || value <= 0) return '-'

  const totalSeconds = Math.floor(value / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (num) => String(num).padStart(2, '0')

  return hours > 0
    ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(minutes)}:${pad(seconds)}`
}

/**
 * 从 Content-Disposition 响应头中提取文件名。
 *
 * 优先取 RFC 5987 的 `filename*=UTF-8''xxx`（中文名不会乱码），
 * 退而求其次取普通 `filename="xxx"`。
 *
 * @param {string} disposition Content-Disposition 头
 * @param {string} fallback 提取失败时的兜底文件名
 * @returns {string} 文件名
 */
export function parseFileName(disposition, fallback = 'backup.zip') {
  if (!disposition || typeof disposition !== 'string') return fallback

  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim())
    } catch (e) {
      // 编码异常时继续尝试普通 filename
    }
  }

  const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
  if (plainMatch && plainMatch[1]) {
    return plainMatch[1].trim()
  }

  return fallback
}

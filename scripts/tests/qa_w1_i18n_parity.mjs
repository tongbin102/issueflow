/**
 * QA Phase8 W1 门禁脚本：zh-CN / en-US 语言包键一一对应校验
 * 用法: node scripts/tests/qa_w1_i18n_parity.mjs
 * 退出码: 0 = 全部对应, 1 = 存在缺失键
 */
import { pathToFileURL } from 'node:url'
import path from 'node:path'

const FILES = ['menu.js', 'site.js', 'system.js']
const BASE = path.resolve(process.cwd(), 'src/frontend/src/locales')

/** 递归展平成点分路径键集合（只收叶子节点） */
function flatten(obj, prefix = '', out = new Set()) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v, key, out)
    else out.add(key)
  }
  return out
}

let failed = 0
for (const file of FILES) {
  const zhMod = await import(pathToFileURL(path.join(BASE, 'zh-CN', file)).href)
  const enMod = await import(pathToFileURL(path.join(BASE, 'en-US', file)).href)
  const zh = flatten(zhMod.default)
  const en = flatten(enMod.default)

  const missingInEn = [...zh].filter((k) => !en.has(k))
  const missingInZh = [...en].filter((k) => !zh.has(k))

  const ok = missingInEn.length === 0 && missingInZh.length === 0
  if (!ok) failed = 1
  console.log(
    `[${ok ? 'PASS' : 'FAIL'}] ${file.padEnd(10)} zh=${String(zh.size).padStart(3)} en=${String(en.size).padStart(3)}`
  )
  if (missingInEn.length) console.log(`   en-US 缺失 (${missingInEn.length}): ${missingInEn.join(', ')}`)
  if (missingInZh.length) console.log(`   zh-CN 缺失 (${missingInZh.length}): ${missingInZh.join(', ')}`)
}

console.log(failed ? '\n=== I18N PARITY: FAIL ===' : '\n=== I18N PARITY: PASS ===')
process.exit(failed)

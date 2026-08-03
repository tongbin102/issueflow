/**
 * QA 独立验证：代码中实际引用的 i18n key 是否都已定义（只读）。
 *
 * 扫描前端源码里的字符串字面量 'dataManagement.xxx' / 'fieldConfig.xxx'，
 * 与 zh-CN / en-US 词条比对，输出「用了但没定义」的缺口。
 *
 * 用法： node tests/qa-verify/i18n-usage-check.mjs
 */
import { pathToFileURL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'

const FE = path.resolve(process.cwd(), 'src/frontend/src')
const imp = (p) => import(pathToFileURL(path.join(FE, p)).href).then((m) => m.default ?? m)

const zh = { dataManagement: await imp('locales/zh-CN/dataManagement.js'), fieldConfig: await imp('locales/zh-CN/fieldConfig.js') }
const en = { dataManagement: await imp('locales/en-US/dataManagement.js'), fieldConfig: await imp('locales/en-US/fieldConfig.js') }

function get(root, dotted) {
  return dotted.split('.').reduce((o, k) => (o == null ? undefined : o[k]), root)
}

/** 递归收集源码文件 */
function walk(dir, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, acc)
    else if (/\.(vue|js)$/.test(e.name) && !p.includes(`${path.sep}locales${path.sep}`)) acc.push(p)
  }
  return acc
}

const RE = /['"`](dataManagement\.[A-Za-z0-9_.]+|fieldConfig\.[A-Za-z0-9_.]+)['"`]/g
const used = new Map()
for (const file of walk(FE)) {
  const src = fs.readFileSync(file, 'utf8')
  let m
  while ((m = RE.exec(src)) !== null) {
    const key = m[1]
    if (key.endsWith('.')) continue
    if (!used.has(key)) used.set(key, [])
    const line = src.slice(0, m.index).split('\n').length
    used.get(key).push(`${path.relative(FE, file).replace(/\\/g, '/')}:${line}`)
  }
}

const missing = []
for (const [key, where] of [...used.entries()].sort()) {
  const zhVal = get(zh, key)
  const enVal = get(en, key)
  if (typeof zhVal !== 'string' || typeof enVal !== 'string') {
    missing.push({ key, where, zh: typeof zhVal === 'string', en: typeof enVal === 'string' })
  }
}

console.log('===== 代码引用的 i18n key 定义核查 =====')
console.log(`扫描到静态引用 key: ${used.size} 个`)
if (missing.length === 0) {
  console.log('RESULT: PASS —— 全部已在 zh-CN 与 en-US 定义')
} else {
  console.log(`RESULT: FAIL —— ${missing.length} 个 key 未定义`)
  missing.forEach((m) =>
    console.log(`  ${m.key}   [zh=${m.zh ? 'OK' : '缺'} en=${m.en ? 'OK' : '缺'}]  用于 ${m.where.join(', ')}`)
  )
}
process.exit(missing.length === 0 ? 0 : 1)

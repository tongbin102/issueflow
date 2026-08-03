/**
 * QA 独立验证脚本：i18n 中英 key 成对性核查（只读，不修改业务代码）。
 *
 * 用法： node tests/qa-verify/i18n-pair-check.mjs
 * 退出码：0 全部成对；1 存在缺失
 */
import { pathToFileURL } from 'node:url'
import path from 'node:path'

const ROOT = path.resolve(process.cwd(), 'src/frontend/src/locales')

/** 递归提取扁平 key 路径（叶子为 string/number/function） */
function flatten(obj, prefix = '', out = []) {
  for (const [k, v] of Object.entries(obj || {})) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v, key, out)
    else out.push(key)
  }
  return out
}

async function load(locale, file) {
  const p = path.join(ROOT, locale, file)
  const mod = await import(pathToFileURL(p).href)
  return mod.default
}

function diff(aName, a, bName, b) {
  const sa = new Set(a)
  const sb = new Set(b)
  const onlyA = a.filter((k) => !sb.has(k))
  const onlyB = b.filter((k) => !sa.has(k))
  return { onlyA, onlyB, aName, bName }
}

let failed = 0

async function check(label, file, filterPrefix) {
  const zh = flatten(await load('zh-CN', file))
  const en = flatten(await load('en-US', file))
  const fz = filterPrefix ? zh.filter((k) => k.startsWith(filterPrefix)) : zh
  const fe = filterPrefix ? en.filter((k) => k.startsWith(filterPrefix)) : en
  const d = diff('zh-CN', fz, 'en-US', fe)
  console.log(`\n===== ${label} (${file}${filterPrefix ? ' / prefix=' + filterPrefix : ''}) =====`)
  console.log(`zh-CN keys: ${fz.length}   en-US keys: ${fe.length}`)
  if (d.onlyA.length === 0 && d.onlyB.length === 0) {
    console.log('RESULT: PASS  (逐 key 完全成对)')
  } else {
    failed++
    console.log('RESULT: FAIL')
    if (d.onlyA.length) console.log('  仅 zh-CN 有 (en-US 缺失):\n    - ' + d.onlyA.join('\n    - '))
    if (d.onlyB.length) console.log('  仅 en-US 有 (zh-CN 缺失):\n    - ' + d.onlyB.join('\n    - '))
  }
  return { zh: fz, en: fe }
}

const dm = await check('B1 dataManagement.*', 'dataManagement.js')
await check('B2 fieldConfig.tip.*', 'fieldConfig.js', 'tip.')
await check('B3 fieldConfig.* (全量参考)', 'fieldConfig.js')

// 附：打印 dataManagement 顶层分组，便于人工核对覆盖面
const groups = [...new Set(dm.zh.map((k) => k.split('.')[0]))]
console.log('\ndataManagement 顶层分组: ' + groups.join(', '))

console.log(`\n==== 总结: ${failed === 0 ? 'ALL PAIRED (PASS)' : failed + ' 组不成对 (FAIL)'} ====`)
process.exit(failed === 0 ? 0 : 1)

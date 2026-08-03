/**
 * QA 独立验证：需求二「字段配置弹窗 Schema 驱动」的 i18n key 落地核查（只读）。
 *
 * 校验 fieldConfigSchema.js 声明的 key 是否真的存在于 zh-CN / en-US 的 fieldConfig 词条中：
 *   - 区块标题 / 说明： section.titleKey / section.descKey
 *   - 每个属性标签：    fieldConfig.label.<code>   （labelKeyOf）
 *   - 每个属性问号提示：fieldConfig.tip.<code>     （tipKeyOf）
 *
 * 用法： node tests/qa-verify/fieldconfig-key-check.mjs
 */
import { pathToFileURL } from 'node:url'
import path from 'node:path'

const FE = path.resolve(process.cwd(), 'src/frontend/src')
const imp = (p) => import(pathToFileURL(path.join(FE, p)).href).then((m) => m.default ?? m)

const schema = await import(pathToFileURL(path.join(FE, 'utils/fieldConfigSchema.js')).href)
const zh = await imp('locales/zh-CN/fieldConfig.js')
const en = await imp('locales/en-US/fieldConfig.js')

/** 按 "a.b.c" 取值（相对 fieldConfig 根，需去掉前缀 fieldConfig.） */
function get(root, dotted) {
  const rel = dotted.startsWith('fieldConfig.') ? dotted.slice('fieldConfig.'.length) : dotted
  return rel.split('.').reduce((o, k) => (o == null ? undefined : o[k]), root)
}

const rows = []
function probe(kind, key) {
  rows.push({
    kind,
    key,
    zh: typeof get(zh, key) === 'string',
    en: typeof get(en, key) === 'string'
  })
}

schema.FIELD_FORM_SECTIONS.forEach((s) => {
  probe('section.title', s.titleKey)
  probe('section.desc', s.descKey)
})
schema.FIELD_FORM_ITEMS.forEach((item) => {
  probe('label', schema.labelKeyOf(item.code))
  probe('tip', schema.tipKeyOf(item.code))
})

const missZh = rows.filter((r) => !r.zh)
const missEn = rows.filter((r) => !r.en)

const byKind = {}
rows.forEach((r) => {
  byKind[r.kind] = byKind[r.kind] || { total: 0, okZh: 0, okEn: 0 }
  byKind[r.kind].total++
  if (r.zh) byKind[r.kind].okZh++
  if (r.en) byKind[r.kind].okEn++
})

console.log('===== 需求二 · Schema 声明 key 的实际落地情况 =====')
console.log('区块数:', schema.FIELD_FORM_SECTIONS.length, ' 属性项数:', schema.FIELD_FORM_ITEMS.length)
console.log('\n类别            总数  zh-CN命中  en-US命中')
Object.entries(byKind).forEach(([k, v]) => {
  console.log(`${k.padEnd(15)} ${String(v.total).padEnd(5)} ${String(v.okZh).padEnd(10)} ${v.okEn}`)
})

if (missZh.length) {
  console.log(`\n---- zh-CN 缺失 ${missZh.length} 个 key ----`)
  missZh.forEach((r) => console.log(`  [${r.kind}] ${r.key}`))
}
if (missEn.length) {
  console.log(`\n---- en-US 缺失 ${missEn.length} 个 key ----`)
  missEn.forEach((r) => console.log(`  [${r.kind}] ${r.key}`))
}

console.log('\n---- fieldConfig 现有 tip.* 词条（实际内容）----')
console.log('  ' + Object.keys(zh.tip || {}).join('\n  '))

console.log(
  `\n==== 结论: ${missZh.length === 0 && missEn.length === 0 ? 'PASS' : 'FAIL —— Schema 引用的 key 未在词条中定义'} ====`
)
process.exit(missZh.length === 0 && missEn.length === 0 ? 0 : 1)

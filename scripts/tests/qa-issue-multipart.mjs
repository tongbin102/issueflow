/**
 * QA 回归验证：前台 createIssue 的 multipart 提交是否真正以 multipart/form-data 发出。
 *
 * 背景：src/api/request.js 的 axios 实例设置了默认 headers
 *       { 'Content-Type': 'application/json' }。
 * axios 1.x 的 transformRequest 中有一条关键分支：
 *       if (isFormData) return hasJSONContentType ? JSON.stringify(formDataToJSON(data)) : data;
 * 若请求最终 Content-Type 仍为 application/json，FormData 会被「降级」序列化成 JSON，
 * 导致后端 consumes=multipart/form-data 的接口收到 application/json → 415。
 *
 * 本脚本用自定义 adapter 拦截，断言最终真实发出的 Content-Type 与 body 类型。
 * 运行方式：node scripts/tests/qa-issue-multipart.mjs
 */
// 直接指向前端工程安装的 axios，保证测的就是前端实际使用的版本
import axios from '../../src/frontend/node_modules/axios/index.js'

const results = []
function record(name, pass, detail) {
  results.push({ name, pass, detail })
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${name}`)
  console.log(`      ${detail}`)
}

/** 构造与前台 IssueCreate.vue onSubmit 完全一致的 FormData */
function buildFormData() {
  const data = {
    title: '回归验证-提交新问题',
    typeId: 1,
    description: 'QA 回归',
    severity: 2,
    priority: 1
  }
  const fd = new FormData()
  fd.append('issue', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  fd.append('files', new File(['hello'], 'a.txt', { type: 'text/plain' }))
  return fd
}

/** 复刻 request.js 的实例配置 */
function makeInstance(extraDefaults = {}) {
  const captured = {}
  const instance = axios.create({
    baseURL: '/api',
    timeout: 15000,
    headers: { 'Content-Type': 'application/json' },
    ...extraDefaults
  })
  instance.interceptors.request.use((config) => {
    config.headers.Authorization = 'Bearer faketoken'
    return config
  })
  // 自定义 adapter：在 transformRequest 之后拿到最终 data / headers
  instance.defaults.adapter = async (config) => {
    captured.headers = config.headers
    captured.data = config.data
    return { data: { code: 200, data: {} }, status: 200, statusText: 'OK', headers: {}, config }
  }
  return { instance, captured }
}

function describeBody(body) {
  if (typeof body === 'string') return `string(${body.length}) => ${body.slice(0, 120)}`
  if (body instanceof FormData) return 'FormData (multipart, 浏览器会自动写 boundary)'
  return Object.prototype.toString.call(body)
}

console.log('==================================================')
console.log('用例 1：当前修复后的写法 —— createIssue 显式 multipart/form-data')
console.log('==================================================')
{
  const { instance, captured } = makeInstance()
  await instance.post('/issues', buildFormData(), {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  const ct = captured.headers.getContentType()
  record(
    '用例1 请求体应保持 FormData',
    captured.data instanceof FormData,
    `最终 Content-Type = ${ct} ; body = ${describeBody(captured.data)}`
  )
}

console.log('')
console.log('==================================================')
console.log('用例 2：负向对照 —— 不传 headers 时 FormData 会被降级为 JSON')
console.log('==================================================')
{
  const { instance, captured } = makeInstance()
  await instance.post('/issues', buildFormData())
  const ct = captured.headers.getContentType()
  const isDowngraded = typeof captured.data === 'string'
  record(
    '用例2 负向对照：未覆盖 Content-Type 时请求体被降级为字符串',
    isDowngraded,
    `最终 Content-Type = ${ct} ; body = ${describeBody(captured.data)}`
  )
}

console.log('')
console.log('==================================================')
console.log('用例 3：候选修复 —— 显式 Content-Type: undefined（让浏览器自动写 boundary）')
console.log('==================================================')
{
  const { instance, captured } = makeInstance()
  await instance.post('/issues', buildFormData(), {
    headers: { 'Content-Type': undefined }
  })
  const ct = captured.headers.getContentType()
  record(
    '用例3 请求体应保持 FormData 且 Content-Type 被清空',
    captured.data instanceof FormData,
    `最终 Content-Type = ${ct} ; body = ${describeBody(captured.data)}`
  )
}

console.log('')
console.log('==================================================')
console.log('用例 4：候选修复 —— 实例不设默认 Content-Type（axios 自动推断）')
console.log('==================================================')
{
  const { instance, captured } = makeInstance({ headers: {} })
  await instance.post('/issues', buildFormData())
  const ct = captured.headers.getContentType()
  record(
    '用例4 请求体应保持 FormData',
    captured.data instanceof FormData,
    `最终 Content-Type = ${ct} ; body = ${describeBody(captured.data)}`
  )
}

console.log('')
console.log('==================== 汇总 ====================')
const failed = results.filter((r) => !r.pass)
console.log(`总计 ${results.length} 用例，通过 ${results.length - failed.length}，失败 ${failed.length}`)
if (failed.length) {
  console.log('\n失败用例：')
  failed.forEach((f) => console.log(`  - ${f.name}\n    ${f.detail}`))
  process.exitCode = 1
}

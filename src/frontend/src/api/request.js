import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'

export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

const instance = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// ============ 请求拦截：注入 Bearer token ============
instance.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 防止重复跳转
let redirectingLogin = false
let redirecting403 = false

function redirectLogin() {
  if (redirectingLogin) return
  redirectingLogin = true
  import('@/router')
    .then(({ default: router }) => {
      const cur = router.currentRoute.value
      if (cur.name !== 'login') {
        router.replace({ path: '/login', query: { redirect: cur.fullPath } })
      }
    })
    .catch(() => {
      window.location.href = '/login'
    })
    .finally(() => {
      setTimeout(() => {
        redirectingLogin = false
      }, 600)
    })
}

function redirect403() {
  if (redirecting403) return
  redirecting403 = true
  import('@/router')
    .then(({ default: router }) => {
      if (router.currentRoute.value.name !== 'forbidden') {
        router.replace({ path: '/403' })
      }
    })
    .catch(() => {})
    .finally(() => {
      setTimeout(() => {
        redirecting403 = false
      }, 600)
    })
}

// ============ 响应拦截：解包 Result ============
instance.interceptors.response.use(
  (response) => {
    // Phase7 T8：显式声明 rawResponse:true 的请求返回完整响应（含 headers），
    // 供备份导出按 Content-Type 判定「二进制文件」还是「JSON 错误体」。
    if (response.config && response.config.rawResponse) {
      return response
    }
    const res = response.data
    // 非 Result 结构（如文件流 blob）原样返回
    if (!res || typeof res !== 'object' || !('code' in res)) {
      return res
    }
    if (res.code === 200) {
      return res.data
    }
    // 业务异常
    if (res.code === 401) {
      removeToken()
      redirectLogin()
    } else if (res.code === 403) {
      redirect403()
    } else {
      ElMessage.error(res.message || '请求失败')
    }
    return Promise.reject(
      Object.assign(new Error(res.message || 'error'), {
        code: res.code,
        data: res.data
      })
    )
  },
  (error) => {
    const res = error.response && error.response.data
    if (res && typeof res === 'object' && 'code' in res && res.code !== 200) {
      if (res.code === 401) {
        removeToken()
        redirectLogin()
      } else if (res.code === 403) {
        redirect403()
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(
        Object.assign(new Error(res.message || 'error'), { code: res.code })
      )
    }
    ElMessage.error((error && error.message) || '网络错误')
    return Promise.reject(error)
  }
)

export default instance

import { watch } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * 判断当前登录用户是否拥有给定角色之一。
 * @param {string|string[]} required 需要的角色码（如 'ADMIN' 或 ['ADMIN','DEVELOPER']）
 * @returns {boolean}
 */
export function hasPermission(required) {
  const roles = (useUserStore().roles || []).slice()
  if (!required || (Array.isArray(required) && required.length === 0)) return true
  const list = Array.isArray(required) ? required : [required]
  return list.some((r) => roles.includes(r))
}

function apply(el, binding) {
  const ok = hasPermission(binding.value)
  el.style.display = ok ? '' : 'none'
}

/**
 * 全局指令 v-permission：按角色码控制元素显隐（按钮级）。
 * 用法：<el-button v-permission="['ADMIN']">删除</el-button>
 * 角色为异步加载，监听 roles 变化刷新显隐。
 */
const permissionDirective = {
  mounted(el, binding) {
    apply(el, binding)
    el.__permStop = watch(
      () => useUserStore().roles,
      () => apply(el, binding),
      { deep: true }
    )
  },
  updated(el, binding) {
    apply(el, binding)
  },
  unmounted(el) {
    if (el.__permStop) el.__permStop()
  }
}

export default permissionDirective

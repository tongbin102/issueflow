/**
 * 登录页文案（zh-CN）
 */
export default {
  title: '登录',
  subtitle: '缺陷记录与验证管理平台',
  tip: '默认管理员：admin / admin123',
  /** 清除缓存按钮文案 */
  clearCache: '清除缓存',
  /** 清除缓存二次确认弹窗 */
  clearCacheConfirm: {
    title: '清除本地缓存',
    content: '将清除本地登录状态及浏览器缓存数据（主题、语言、布局等偏好设置），确定继续吗？'
  },
  /** 品牌面板：价值主张 */
  brandDesc: '统一的项目问题提报入口，让每一条问题都可追踪、可协作、可度量。',
  /** 品牌面板：特性点（与 en-US 逐项对应） */
  features: ['流程跟踪', '协作提报', '数据统计'],
  field: {
    username: '账号',
    password: '密码',
    remember: '记住我'
  },
  action: {
    submit: '登 录'
  },
  msg: {
    usernameRequired: '请输入账号',
    passwordRequired: '请输入密码',
    success: '登录成功',
    failed: '账号或密码错误',
    cacheCleared: '缓存已清除，登录状态已重置'
  }
}

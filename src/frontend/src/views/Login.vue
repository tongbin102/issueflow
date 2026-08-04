<template>
  <!--
    登录页（主题深度定制）
    - 根层 .login-page 使用 position: fixed; inset: 0 铺满视口，
      覆盖 BlankLayout 自带的浅蓝渐变（BlankLayout 为 403/404 共用，禁止改动）。
    - 背景与装饰全部由 CSS 渐变 + 内联 SVG 实现，不引入任何外部图片资源。
    - 所有色值/间距/阴影均消费设计令牌（--theme-color / --if-*），组件层不硬编码。
  -->
  <div class="login-page">
    <!-- ① 背景层：多层渐变 + 径向高光 + 点阵网格 -->
    <div class="login-bg" aria-hidden="true"></div>

    <!-- ② 装饰层：节点连线工作流线框（问题提报流转）+ 漂浮任务卡剪影 -->
    <div class="login-decor" aria-hidden="true">
      <svg
        class="decor-net"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
        focusable="false"
      >
        <g class="decor-net__lines">
          <path d="M110 706 L318 566 L524 628 L762 432 L986 502 L1258 306" />
          <path d="M168 236 L424 322 L648 208 L902 302 L1172 186" />
          <path d="M318 566 L424 322" />
          <path d="M762 432 L648 208" />
          <path d="M986 502 L902 302" />
          <path d="M524 628 L648 208" />
          <path d="M110 706 L168 236" />
          <path d="M1258 306 L1172 186" />
        </g>
        <g class="decor-net__nodes">
          <circle cx="110" cy="706" r="7" />
          <circle cx="318" cy="566" r="5" />
          <circle cx="524" cy="628" r="9" />
          <circle cx="762" cy="432" r="6" />
          <circle cx="986" cy="502" r="5" />
          <circle cx="1258" cy="306" r="8" />
          <circle cx="168" cy="236" r="6" />
          <circle cx="424" cy="322" r="9" />
          <circle cx="648" cy="208" r="5" />
          <circle cx="902" cy="302" r="7" />
          <circle cx="1172" cy="186" r="5" />
        </g>
        <g class="decor-net__rings">
          <circle cx="524" cy="628" r="22" />
          <circle cx="424" cy="322" r="20" />
          <circle cx="1258" cy="306" r="26" />
        </g>
      </svg>

      <!-- 漂浮任务卡剪影 -->
      <span class="decor-card decor-card--a"></span>
      <span class="decor-card decor-card--b"></span>
      <span class="decor-card decor-card--c"></span>
    </div>

    <!-- ③ 内容层 -->
    <div class="login-shell">
      <!-- 左侧品牌面板（≥768px 显示） -->
      <section class="login-brand">
        <div class="brand-mark">
          <svg class="brand-mark__icon" viewBox="0 0 48 48" focusable="false" aria-hidden="true">
            <path class="brand-mark__line" d="M14 34 L24 24 L34 14" />
            <path class="brand-mark__line" d="M14 34 L34 34" />
            <circle class="brand-mark__dot" cx="14" cy="34" r="5" />
            <circle class="brand-mark__dot" cx="24" cy="24" r="4" />
            <circle class="brand-mark__dot brand-mark__dot--accent" cx="34" cy="14" r="6" />
          </svg>
          <span class="brand-mark__tag">IssueFlow</span>
        </div>

        <h1 class="brand-title">{{ appStore.siteName }}</h1>
        <p class="brand-subtitle">{{ appStore.siteSubtitle || t('login.subtitle') }}</p>
        <p class="brand-desc">{{ t('login.brandDesc') }}</p>

        <ul class="brand-features">
          <li v-for="item in features" :key="item" class="brand-features__item">
            <svg class="brand-features__icon" viewBox="0 0 24 24" focusable="false" aria-hidden="true">
              <circle class="brand-features__ring" cx="12" cy="12" r="10" />
              <path class="brand-features__check" d="M7.5 12.5 L10.5 15.5 L16.5 9" />
            </svg>
            <span>{{ item }}</span>
          </li>
        </ul>

        <!-- 品牌插画：问题提报流转（卡片 → 卡片 → 完成） -->
        <div class="brand-art">
          <svg viewBox="0 0 440 190" focusable="false" aria-hidden="true">
            <g class="brand-art__flow">
              <rect class="brand-art__card" x="6" y="66" width="118" height="82" rx="14" />
              <path class="brand-art__row" d="M26 92 L92 92" />
              <path class="brand-art__row" d="M26 108 L74 108" />
              <path class="brand-art__row" d="M26 124 L84 124" />

              <rect class="brand-art__card" x="161" y="40" width="118" height="82" rx="14" />
              <path class="brand-art__row" d="M181 66 L247 66" />
              <path class="brand-art__row" d="M181 82 L229 82" />
              <path class="brand-art__row" d="M181 98 L239 98" />

              <rect class="brand-art__card brand-art__card--accent" x="316" y="72" width="118" height="82" rx="14" />
              <path class="brand-art__check" d="M348 114 L368 134 L404 96" />

              <path class="brand-art__link" d="M124 100 C 142 100, 144 74, 161 74" />
              <path class="brand-art__link" d="M279 82 C 297 82, 299 108, 316 108" />

              <circle class="brand-art__pin" cx="124" cy="100" r="4" />
              <circle class="brand-art__pin" cx="161" cy="74" r="4" />
              <circle class="brand-art__pin" cx="279" cy="82" r="4" />
              <circle class="brand-art__pin" cx="316" cy="108" r="4" />
            </g>
          </svg>
        </div>
      </section>

      <!-- 右侧登录面板 -->
      <section class="login-panel">
        <!-- 移动端紧凑头部（<768px 显示，替代收起的品牌面板） -->
        <header class="login-mobile-head">
          <h1 class="login-mobile-head__title">{{ appStore.siteName }}</h1>
          <p class="login-mobile-head__subtitle">
            {{ appStore.siteSubtitle || t('login.subtitle') }}
          </p>
        </header>

        <el-card class="login-card" shadow="always">
          <div class="login-header">
            <h2 class="login-title">{{ t('login.title') }}</h2>
            <span class="login-title__bar"></span>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            @submit.prevent="handleSubmit"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                :placeholder="t('login.field.username')"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                :placeholder="t('login.field.password')"
                :prefix-icon="Lock"
                show-password
                @keyup.enter="handleSubmit"
              />
            </el-form-item>
            <div class="login-options">
              <el-checkbox v-model="remember">{{ t('login.field.remember') }}</el-checkbox>
              <LocaleSwitch />
            </div>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleSubmit"
            >
              {{ t('login.action.submit') }}
            </el-button>
          </el-form>

          <!-- 清除缓存：低调的文字按钮，二次确认后清本地缓存与登录态 -->
          <div class="login-extra">
            <el-button
              link
              type="info"
              class="clear-cache-btn"
              :icon="Delete"
              :loading="clearing"
              @click="handleClearCache"
            >
              {{ t('login.clearCache') }}
            </el-button>
          </div>

          <p class="login-tip">{{ t('login.tip') }}</p>
        </el-card>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Lock, Delete } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import LocaleSwitch from '@/components/LocaleSwitch.vue'
import { clearAppCache } from '@/utils/cache'

const { t, tm, rt } = useI18n()
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref()
const loading = ref(false)
const clearing = ref(false)
const remember = ref(true)
const form = reactive({ username: '', password: '' })

const rules = computed(() => ({
  username: [{ required: true, message: t('login.msg.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.msg.passwordRequired'), trigger: 'blur' }]
}))

/**
 * 品牌面板特性点。
 * i18n 数组需用 tm() 取原始消息，逐项 rt() 解析为字符串（vue-i18n 9 Composition 模式）。
 * @type {import('vue').ComputedRef<string[]>}
 */
const features = computed(() => {
  const raw = tm('login.features')
  if (!Array.isArray(raw)) return []
  return raw.map((item) => (typeof item === 'string' ? item : rt(item)))
})

/**
 * 提交登录：校验 → 调用 userStore.login → 按 redirect 或角色默认首页跳转。
 * @returns {Promise<void>}
 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form.username, form.password)
      ElMessage.success(t('login.msg.success'))
      const redirect = route.query.redirect
      if (redirect) {
        router.replace(redirect)
      } else {
        router.replace(userStore.defaultHomePath())
      }
    } catch (e) {
      ElMessage.error((e && e.message) || t('login.msg.failed'))
    } finally {
      loading.value = false
    }
  })
}

/**
 * 清除缓存：二次确认 → 清本地登录态与浏览器缓存 → 成功提示 → 轻量重载。
 *
 * 清理范围由 utils/cache.js 的 clearAppCache() 统一封装：
 * localStorage 全部 if_* 键 + issueflow:column-preferences + sessionStorage + userStore 内存态。
 * 不调用 userStore.logout()，避免在未登录场景下发起无谓的后端登出请求。
 * @returns {Promise<void>}
 */
async function handleClearCache() {
  try {
    await ElMessageBox.confirm(
      t('login.clearCacheConfirm.content'),
      t('login.clearCacheConfirm.title'),
      {
        type: 'warning',
        confirmButtonText: t('common.action.confirm'),
        cancelButtonText: t('common.action.cancel')
      }
    )
  } catch (e) {
    // 用户取消（ElMessageBox reject 'cancel' / 'close'），静默返回
    return
  }

  clearing.value = true
  try {
    clearAppCache(userStore)
    // 同步重置表单，避免浏览器自动填充残留造成「已清除但仍有内容」的错觉
    form.username = ''
    form.password = ''
    if (formRef.value) formRef.value.clearValidate()
    ElMessage.success(t('login.msg.cacheCleared'))
    // 轻量重载：刷新后主题 / 语言 / 布局等偏好回落默认值
    setTimeout(() => {
      window.location.reload()
    }, 600)
  } catch (e) {
    clearing.value = false
    ElMessage.error((e && e.message) || t('login.msg.failed'))
  }
}
</script>

<style scoped>
/* ==========================================================================
   ① 根容器：铺满视口并覆盖 BlankLayout 的浅蓝渐变
   ========================================================================== */
.login-page {
  position: fixed;
  inset: 0;
  z-index: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  padding: var(--if-space-lg) var(--if-space-md);
  background-color: var(--if-login-bg-to);
}

/* ==========================================================================
   ② 背景层：线性渐变 + 双径向高光 + 点阵网格
   ========================================================================== */
.login-bg {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(900px 520px at 14% 16%, var(--if-login-glow-strong), transparent 62%),
    radial-gradient(760px 520px at 88% 86%, var(--if-login-decor-accent), transparent 60%),
    linear-gradient(
      135deg,
      var(--if-login-bg-from) 0%,
      var(--if-login-bg-via) 46%,
      var(--if-login-bg-to) 100%
    );
}

/* 点阵网格：自上而下淡出，避免抢卡片焦点 */
.login-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--if-login-grid-dot) 1px, transparent 1px);
  background-size: 26px 26px;
  -webkit-mask-image: var(--if-login-dot-mask);
  mask-image: var(--if-login-dot-mask);
}

/* 右下角青色氛围光 */
.login-bg::after {
  content: '';
  position: absolute;
  right: -12%;
  bottom: -20%;
  width: 62vw;
  height: 62vw;
  max-width: 900px;
  max-height: 900px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--if-login-bg-accent) 0%, transparent 68%);
  opacity: 0.28;
}

/* ==========================================================================
   ③ 装饰层：节点连线网络 + 漂浮任务卡
   ========================================================================== */
.login-decor {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.decor-net {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.55;
}

.decor-net__lines path {
  fill: none;
  stroke: var(--if-login-decor-stroke);
  stroke-width: 1.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.decor-net__nodes circle {
  fill: var(--if-login-decor-fill);
  stroke: var(--if-login-decor-stroke);
  stroke-width: 1.4;
}

.decor-net__rings circle {
  fill: none;
  stroke: var(--if-login-decor-accent);
  stroke-width: 1.2;
  stroke-dasharray: 4 6;
}

.decor-card {
  position: absolute;
  display: block;
  border: 1px solid var(--if-login-chip-border);
  border-radius: var(--if-radius);
  background: var(--if-login-glow-soft);
  animation: decor-float 9s ease-in-out infinite;
}

.decor-card--a {
  top: 16%;
  left: 8%;
  width: 132px;
  height: 84px;
}

.decor-card--b {
  right: 10%;
  bottom: 18%;
  width: 108px;
  height: 72px;
  animation-delay: -3s;
}

.decor-card--c {
  top: 62%;
  left: 26%;
  width: 88px;
  height: 60px;
  animation-delay: -6s;
}

@keyframes decor-float {
  0%,
  100% {
    transform: translateY(0) rotate(-2deg);
  }
  50% {
    transform: translateY(-14px) rotate(2deg);
  }
}

/* ==========================================================================
   ④ 内容骨架：桌面左右两栏
   ========================================================================== */
.login-shell {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--if-space-xl);
  width: 100%;
  max-width: var(--if-login-shell-max);
  margin: auto; /* 内容超高时避免 flex 居中裁掉顶部 */
}

/* ---------- 左侧品牌面板 ---------- */
.login-brand {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--if-login-on-brand);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: var(--if-space-sm);
  padding: var(--if-space-xs) var(--if-space-md) var(--if-space-xs) var(--if-space-sm);
  border: 1px solid var(--if-login-chip-border);
  border-radius: var(--if-radius-pill);
  background: var(--if-login-chip-bg);
}

.brand-mark__icon {
  width: 26px;
  height: 26px;
}

.brand-mark__line {
  fill: none;
  stroke: var(--if-login-on-brand);
  stroke-width: 2.4;
  stroke-linecap: round;
}

.brand-mark__dot {
  fill: var(--if-login-on-brand);
}

.brand-mark__dot--accent {
  fill: var(--if-login-decor-accent);
  stroke: var(--if-login-on-brand);
  stroke-width: 1.6;
}

.brand-mark__tag {
  font-size: var(--if-font-xs);
  font-weight: var(--if-weight-bold);
  letter-spacing: 1.4px;
  text-transform: uppercase;
}

.brand-title {
  margin: var(--if-space-lg) 0 0;
  font-size: 40px;
  line-height: var(--if-line-tight);
  font-weight: var(--if-weight-bold);
  letter-spacing: 1px;
}

.brand-subtitle {
  margin: var(--if-space-sm) 0 0;
  font-size: var(--if-font-h3);
  font-weight: var(--if-weight-medium);
  color: var(--if-login-on-brand-muted);
}

.brand-desc {
  margin: var(--if-space-md) 0 0;
  max-width: 420px;
  font-size: var(--if-font-base);
  line-height: var(--if-line-base);
  color: var(--if-login-on-brand-muted);
}

.brand-features {
  display: flex;
  flex-wrap: wrap;
  gap: var(--if-space-sm) var(--if-space-md);
  margin: var(--if-space-lg) 0 0;
  padding: 0;
  list-style: none;
}

.brand-features__item {
  display: inline-flex;
  align-items: center;
  gap: var(--if-space-xs);
  padding: var(--if-space-xs) var(--if-space-md);
  border: 1px solid var(--if-login-chip-border);
  border-radius: var(--if-radius-pill);
  background: var(--if-login-chip-bg);
  font-size: var(--if-font-sm);
  font-weight: var(--if-weight-medium);
  transition: background var(--if-transition-base);
}

.brand-features__item:hover {
  background: var(--if-login-glow-strong);
}

.brand-features__icon {
  width: 16px;
  height: 16px;
  flex: none;
}

.brand-features__ring {
  fill: none;
  stroke: var(--if-login-decor-accent);
  stroke-width: 1.6;
}

.brand-features__check {
  fill: none;
  stroke: var(--if-login-on-brand);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.brand-art {
  margin: var(--if-space-xl) 0 0;
  max-width: 440px;
}

.brand-art svg {
  display: block;
  width: 100%;
  height: auto;
}

.brand-art__card {
  fill: var(--if-login-decor-fill);
  stroke: var(--if-login-decor-stroke);
  stroke-width: 1.6;
}

.brand-art__card--accent {
  stroke: var(--if-login-decor-accent);
}

.brand-art__row {
  stroke: var(--if-login-on-brand-muted);
  stroke-width: 3;
  stroke-linecap: round;
  opacity: 0.5;
}

.brand-art__check {
  fill: none;
  stroke: var(--if-login-decor-accent);
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.brand-art__link {
  fill: none;
  stroke: var(--if-login-decor-stroke);
  stroke-width: 1.6;
  stroke-dasharray: 5 5;
}

.brand-art__pin {
  fill: var(--if-login-on-brand);
  opacity: 0.75;
}

/* ---------- 右侧登录面板 ---------- */
.login-panel {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: var(--if-login-card-width);
  max-width: 100%;
}

.login-mobile-head {
  display: none;
  width: 100%;
  margin-bottom: var(--if-space-lg);
  text-align: center;
  color: var(--if-login-on-brand);
}

.login-mobile-head__title {
  margin: 0;
  font-size: var(--if-font-h1);
  font-weight: var(--if-weight-bold);
  letter-spacing: 1px;
}

.login-mobile-head__subtitle {
  margin: var(--if-space-xs) 0 0;
  font-size: var(--if-font-sm);
  color: var(--if-login-on-brand-muted);
}

.login-card {
  width: 100%;
  border: none;
  border-radius: var(--if-radius);
  box-shadow: var(--if-shadow-lg);
  background: var(--if-login-card-bg);
}

.login-card :deep(.el-card__body) {
  padding: var(--if-space-xl) var(--if-space-lg) var(--if-space-lg);
}

.login-header {
  margin-bottom: var(--if-space-lg);
  text-align: center;
}

.login-title {
  margin: 0;
  font-size: var(--if-font-h2);
  font-weight: var(--if-weight-bold);
  color: var(--text-primary);
  letter-spacing: 1px;
}

.login-title__bar {
  display: block;
  width: 40px;
  height: 3px;
  margin: var(--if-space-sm) auto 0;
  border-radius: var(--if-radius-pill);
  background: var(--theme-color);
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--if-space-md);
}

.login-btn {
  width: 100%;
  font-weight: var(--if-weight-medium);
  letter-spacing: 2px;
  transition: box-shadow var(--if-transition-base);
}

.login-btn:hover {
  box-shadow: var(--if-shadow-md);
}

.login-extra {
  display: flex;
  justify-content: center;
  margin-top: var(--if-space-md);
}

.clear-cache-btn {
  font-size: var(--if-font-xs);
}

.login-tip {
  margin: var(--if-space-sm) 0 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--if-font-xs);
}

/* ==========================================================================
   ⑤ 响应式：<768px 单栏，品牌面板收起为紧凑头部
   ========================================================================== */
@media (max-width: 768px) {
  .login-page {
    align-items: flex-start;
    padding: var(--if-space-xl) var(--if-space-md);
  }

  .login-shell {
    flex-direction: column;
    gap: 0;
    max-width: 440px;
  }

  /* 桌面品牌面板整体收起，装饰 SVG 一并隐藏 */
  .login-brand {
    display: none;
  }

  .login-mobile-head {
    display: block;
  }

  .login-panel {
    width: 100%;
  }

  .login-card :deep(.el-card__body) {
    padding: var(--if-space-lg) var(--if-space-md);
  }

  /* 移动端简化装饰层，保留背景氛围即可 */
  .decor-card,
  .decor-net__rings {
    display: none;
  }

  .decor-net {
    opacity: 0.35;
  }

  /* 触控热区：登录按钮不小于 44px */
  .login-btn {
    min-height: var(--if-touch-size);
  }

  .clear-cache-btn {
    min-height: var(--if-touch-size);
  }
}

/* 减少动态效果偏好：关闭漂浮动画 */
@media (prefers-reduced-motion: reduce) {
  .decor-card {
    animation: none;
  }
}
</style>

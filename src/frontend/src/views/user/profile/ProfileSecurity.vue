<template>
  <!-- Tab2 账户安全：改密行 + 手机/邮箱绑定行，各自开 FormDrawer(sm)（ARCH §2.5-98） -->
  <div class="profile-security">
    <!-- 登录密码 -->
    <div class="sec-row">
      <div class="sec-row__main">
        <div class="sec-row__title">
          <el-icon><Lock /></el-icon>
          <span>{{ t('profile.security.password') }}</span>
        </div>
        <div class="sec-row__desc">{{ t('profile.security.passwordDesc') }}</div>
        <div class="sec-row__meta">
          {{
            profile.pwdUpdatedAt
              ? t('profile.security.lastChanged', { time: profile.pwdUpdatedAt })
              : t('profile.security.neverChanged')
          }}
        </div>
      </div>
      <div class="sec-row__action">
        <el-button type="primary" plain @click="openPassword">
          {{ t('profile.security.changePassword') }}
        </el-button>
      </div>
    </div>

    <el-divider />

    <div class="sec-group-title">{{ t('profile.security.bindingTitle') }}</div>

    <!-- 手机绑定 -->
    <div class="sec-row">
      <div class="sec-row__main">
        <div class="sec-row__title">
          <el-icon><Iphone /></el-icon>
          <span>{{ t('profile.security.bindingPhone') }}</span>
          <el-tag :type="profile.phoneRaw ? 'success' : 'info'" size="small" effect="light">
            {{ profile.phoneRaw ? t('profile.security.bound') : t('profile.security.unbound') }}
          </el-tag>
        </div>
        <div class="sec-row__desc">{{ t('profile.security.bindingPhoneDesc') }}</div>
        <div class="sec-row__meta">{{ profile.phone || t('profile.summary.unset') }}</div>
      </div>
      <div class="sec-row__action">
        <el-button plain @click="openBinding('PHONE')">
          {{ profile.phoneRaw ? t('profile.security.change') : t('profile.security.bind') }}
        </el-button>
      </div>
    </div>

    <!-- 邮箱绑定 -->
    <div class="sec-row">
      <div class="sec-row__main">
        <div class="sec-row__title">
          <el-icon><Message /></el-icon>
          <span>{{ t('profile.security.bindingEmail') }}</span>
          <el-tag :type="profile.emailRaw ? 'success' : 'info'" size="small" effect="light">
            {{ profile.emailRaw ? t('profile.security.bound') : t('profile.security.unbound') }}
          </el-tag>
        </div>
        <div class="sec-row__desc">{{ t('profile.security.bindingEmailDesc') }}</div>
        <div class="sec-row__meta">{{ profile.email || t('profile.summary.unset') }}</div>
      </div>
      <div class="sec-row__action">
        <el-button plain @click="openBinding('EMAIL')">
          {{ profile.emailRaw ? t('profile.security.change') : t('profile.security.bind') }}
        </el-button>
      </div>
    </div>

    <!-- 修改密码抽屉 -->
    <FormDrawer
      v-model="pwdVisible"
      :title="t('profile.security.drawerPasswordTitle')"
      size="sm"
      :loading="pwdLoading"
      @confirm="submitPassword"
      @closed="resetPasswordForm"
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-position="top"
      >
        <el-form-item :label="t('profile.security.oldPassword')" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
            :placeholder="t('profile.placeholder.oldPassword')"
          />
        </el-form-item>
        <el-form-item :label="t('profile.security.newPassword')" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="t('profile.placeholder.newPassword')"
          />
          <div class="pwd-meter">
            <el-progress
              :percentage="strengthPercent"
              :stroke-width="6"
              :show-text="false"
              :color="strengthColor"
            />
            <span class="pwd-meter__label" :style="{ color: strengthColor }">
              {{ t('profile.security.strength') }}: {{ strengthLabel }}
            </span>
          </div>
          <div class="field-tip">{{ t('profile.security.passwordRule') }}</div>
        </el-form-item>
        <el-form-item :label="t('profile.security.confirmPassword')" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="t('profile.placeholder.confirmPassword')"
          />
        </el-form-item>
      </el-form>
    </FormDrawer>

    <!-- 绑定变更抽屉 -->
    <FormDrawer
      v-model="bindVisible"
      :title="bindTitle"
      size="sm"
      :loading="bindLoading"
      @confirm="submitBinding"
      @closed="resetBindingForm"
    >
      <el-form
        ref="bindFormRef"
        :model="bindForm"
        :rules="bindRules"
        label-position="top"
      >
        <el-form-item :label="bindValueLabel" prop="value">
          <el-input
            v-model="bindForm.value"
            :maxlength="bindForm.type === 'PHONE' ? 11 : 100"
            :placeholder="bindValuePlaceholder"
          />
        </el-form-item>
        <el-form-item :label="t('profile.security.currentPassword')" prop="currentPassword">
          <el-input
            v-model="bindForm.currentPassword"
            type="password"
            show-password
            autocomplete="current-password"
            :placeholder="t('profile.placeholder.currentPassword')"
          />
          <div class="field-tip">{{ t('profile.security.currentPasswordTip') }}</div>
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Lock, Iphone, Message } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import { useUserStore } from '@/store/user'
import { changePassword, changeBinding } from '@/api/profile'

const props = defineProps({
  /** 父级传入的 ProfileVO */
  profile: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['updated'])

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const EMAIL_REGEX = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/
const PHONE_REGEX = /^1[3-9]\d{9}$/
/** 与后端 PasswordChangeReq 完全一致：≥8 且同时含字母与数字 */
const PWD_REGEX = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/
/** 改密成功后强制登出倒计时（秒），ARCH T5 实现要点 3 */
const RELOGIN_DELAY = 3

/* ---------------- 修改密码 ---------------- */
const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
/** 倒计时定时器句柄，组件卸载时清理防止内存泄漏 */
let reloginTimer = null

/** 密码强度评分 0-4：长度、大小写、数字、符号各计一分 */
const strengthScore = computed(() => {
  const value = pwdForm.newPassword || ''
  if (!value) return 0
  let score = 0
  if (value.length >= 8) score += 1
  if (value.length >= 12) score += 1
  if (/[A-Za-z]/.test(value) && /\d/.test(value)) score += 1
  if (/[^A-Za-z0-9]/.test(value)) score += 1
  return score
})

const strengthPercent = computed(() => strengthScore.value * 25)

const strengthLabel = computed(() => {
  if (strengthScore.value <= 1) return t('profile.security.strengthWeak')
  if (strengthScore.value <= 2) return t('profile.security.strengthMedium')
  return t('profile.security.strengthStrong')
})

const strengthColor = computed(() => {
  if (strengthScore.value <= 1) return '#f56c6c'
  if (strengthScore.value <= 2) return '#e6a23c'
  return '#67c23a'
})

function validateNewPassword(rule, value, callback) {
  if (!value) {
    callback(new Error(t('profile.msg.newPasswordRequired')))
    return
  }
  if (!PWD_REGEX.test(value)) {
    callback(new Error(t('profile.msg.passwordWeak')))
    return
  }
  if (pwdForm.oldPassword && value === pwdForm.oldPassword) {
    callback(new Error(t('profile.msg.passwordSameAsOld')))
    return
  }
  callback()
}

function validateConfirmPassword(rule, value, callback) {
  if (!value) {
    callback(new Error(t('profile.msg.confirmPasswordRequired')))
    return
  }
  if (value !== pwdForm.newPassword) {
    callback(new Error(t('profile.msg.passwordMismatch')))
    return
  }
  callback()
}

const pwdRules = computed(() => ({
  oldPassword: [
    { required: true, message: t('profile.msg.oldPasswordRequired'), trigger: 'blur' }
  ],
  newPassword: [{ required: true, validator: validateNewPassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }]
}))

function openPassword() {
  pwdVisible.value = true
}

function resetPasswordForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  if (pwdFormRef.value) pwdFormRef.value.clearValidate()
}

/** 改密成功：后端已把当前 token 拉黑，前端倒计时后清态跳登录 */
function forceRelogin() {
  let seconds = RELOGIN_DELAY
  ElMessage.success(t('profile.msg.passwordChanged'))
  ElMessage.warning(t('profile.msg.reloginCountdown', { seconds }))
  reloginTimer = setInterval(() => {
    seconds -= 1
    if (seconds <= 0) {
      clearInterval(reloginTimer)
      reloginTimer = null
      userStore.logout()
      router.replace('/login')
    }
  }, 1000)
}

async function submitPassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
      confirmPassword: pwdForm.confirmPassword
    })
    pwdVisible.value = false
    forceRelogin()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    pwdLoading.value = false
  }
}

/* ---------------- 绑定变更 ---------------- */
const bindVisible = ref(false)
const bindLoading = ref(false)
const bindFormRef = ref(null)
const bindForm = reactive({ type: 'PHONE', value: '', currentPassword: '' })

const bindTitle = computed(() =>
  bindForm.type === 'PHONE'
    ? t('profile.security.drawerPhoneTitle')
    : t('profile.security.drawerEmailTitle')
)

const bindValueLabel = computed(() =>
  bindForm.type === 'PHONE' ? t('profile.security.newPhone') : t('profile.security.newEmail')
)

const bindValuePlaceholder = computed(() =>
  bindForm.type === 'PHONE' ? t('profile.placeholder.newPhone') : t('profile.placeholder.newEmail')
)

function validateBindValue(rule, value, callback) {
  if (!value) {
    callback(new Error(t('profile.msg.bindingValueRequired')))
    return
  }
  if (bindForm.type === 'PHONE' && !PHONE_REGEX.test(value)) {
    callback(new Error(t('profile.msg.phoneInvalid')))
    return
  }
  if (bindForm.type === 'EMAIL' && !EMAIL_REGEX.test(value)) {
    callback(new Error(t('profile.msg.emailInvalid')))
    return
  }
  callback()
}

const bindRules = computed(() => ({
  value: [{ required: true, validator: validateBindValue, trigger: 'blur' }],
  currentPassword: [
    { required: true, message: t('profile.msg.currentPasswordRequired'), trigger: 'blur' }
  ]
}))

/**
 * 打开绑定抽屉并回填原值（脱敏值不可用于编辑）。
 * @param {'PHONE'|'EMAIL'} type 绑定类型
 */
function openBinding(type) {
  bindForm.type = type
  bindForm.value =
    type === 'PHONE' ? props.profile.phoneRaw || '' : props.profile.emailRaw || ''
  bindForm.currentPassword = ''
  bindVisible.value = true
}

function resetBindingForm() {
  bindForm.value = ''
  bindForm.currentPassword = ''
  if (bindFormRef.value) bindFormRef.value.clearValidate()
}

async function submitBinding() {
  if (!bindFormRef.value) return
  try {
    await bindFormRef.value.validate()
  } catch (e) {
    return
  }
  bindLoading.value = true
  try {
    const vo = await changeBinding({
      type: bindForm.type,
      value: bindForm.value,
      currentPassword: bindForm.currentPassword
    })
    ElMessage.success(t('profile.msg.bindingChanged'))
    bindVisible.value = false
    emit('updated', vo)
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    bindLoading.value = false
  }
}

onBeforeUnmount(() => {
  if (reloginTimer) {
    clearInterval(reloginTimer)
    reloginTimer = null
  }
})
</script>

<style scoped>
.sec-group-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.sec-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color);
}

.sec-row:last-child {
  border-bottom: none;
}

.sec-row__main {
  flex: 1;
  min-width: 0;
}

.sec-row__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.sec-row__desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.sec-row__meta {
  font-size: 13px;
  color: var(--text-regular);
  margin-top: 6px;
  word-break: break-all;
}

.sec-row__action {
  flex-shrink: 0;
}

.pwd-meter {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-top: 6px;
}

.pwd-meter :deep(.el-progress) {
  flex: 1;
}

.pwd-meter__label {
  font-size: 12px;
  flex-shrink: 0;
}

.field-tip {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .sec-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .sec-row__action {
    width: 100%;
  }

  .sec-row__action :deep(.el-button) {
    width: 100%;
    min-height: 40px;
  }
}
</style>

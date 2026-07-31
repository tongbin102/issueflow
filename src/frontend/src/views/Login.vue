<template>
  <el-card class="login-card" shadow="always">
    <div class="login-header">
      <h2 class="login-title">{{ appStore.siteName }}</h2>
      <p class="login-subtitle">{{ appStore.siteSubtitle || t('login.subtitle') }}</p>
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
    <p class="login-tip">{{ t('login.tip') }}</p>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import LocaleSwitch from '@/components/LocaleSwitch.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref()
const loading = ref(false)
const remember = ref(true)
const form = reactive({ username: '', password: '' })

const rules = computed(() => ({
  username: [{ required: true, message: t('login.msg.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.msg.passwordRequired'), trigger: 'blur' }]
}))

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
</script>

<style scoped>
.login-card {
  width: 380px;
  max-width: 90vw;
  border-radius: var(--border-radius-base);
}
.login-header {
  text-align: center;
  margin-bottom: 20px;
}
.login-title {
  margin: 0;
  font-size: 26px;
  color: var(--theme-color);
  letter-spacing: 1px;
}
.login-subtitle {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}
.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.login-btn {
  width: 100%;
}
.login-tip {
  margin: 12px 0 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
}
</style>

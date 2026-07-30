<template>
  <el-card class="login-card" shadow="always">
    <div class="login-header">
      <h2 class="login-title">issueFlow</h2>
      <p class="login-subtitle">缺陷记录与验证管理平台</p>
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
          placeholder="账号"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="handleSubmit"
        />
      </el-form-item>
      <div class="login-options">
        <el-checkbox v-model="remember">记住我</el-checkbox>
      </div>
      <el-button
        type="primary"
        class="login-btn"
        :loading="loading"
        @click="handleSubmit"
      >
        登 录
      </el-button>
    </el-form>
    <p class="login-tip">默认管理员：admin / admin123</p>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const remember = ref(true)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form.username, form.password)
      ElMessage.success('登录成功')
      const redirect = route.query.redirect
      if (redirect) {
        router.replace(redirect)
      } else {
        router.replace(userStore.defaultHomePath())
      }
    } catch (e) {
      ElMessage.error((e && e.message) || '登录失败')
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

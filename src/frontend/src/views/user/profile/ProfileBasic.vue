<template>
  <!-- Tab1 基本信息：头像上传 + 昵称/姓名/邮箱/手机 编辑保存（ARCH §2.5-97） -->
  <div class="profile-basic">
    <!-- 头像区 -->
    <div class="avatar-row">
      <UserAvatar
        :user-id="profile.id"
        :avatar="profile.avatar"
        :name="displayName"
        :size="88"
        :version="userStore.avatarVersion"
      />
      <div class="avatar-actions">
        <el-upload
          :show-file-list="false"
          :auto-upload="true"
          :before-upload="beforeAvatarUpload"
          :http-request="doUploadAvatar"
          accept="image/png,image/jpeg,image/gif,image/webp"
        >
          <el-button :loading="avatarLoading" :icon="Upload">
            {{ t('profile.basic.changeAvatar') }}
          </el-button>
        </el-upload>
        <div class="avatar-tip">{{ t('profile.basic.avatarTip') }}</div>
      </div>
    </div>

    <el-divider />

    <!-- 资料表单 -->
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="profile-form"
    >
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item :label="t('profile.basic.username')">
            <el-input v-model="form.username" disabled />
            <div class="field-tip">{{ t('profile.basic.usernameTip') }}</div>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item :label="t('profile.basic.nickname')" prop="nickname">
            <el-input
              v-model="form.nickname"
              maxlength="50"
              show-word-limit
              :placeholder="t('profile.placeholder.nickname')"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item :label="t('profile.basic.realName')" prop="realName">
            <el-input
              v-model="form.realName"
              maxlength="50"
              :placeholder="t('profile.placeholder.realName')"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item :label="t('profile.basic.email')" prop="email">
            <el-input
              v-model="form.email"
              maxlength="100"
              :placeholder="t('profile.placeholder.email')"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item :label="t('profile.basic.phone')" prop="phone">
            <el-input
              v-model="form.phone"
              maxlength="11"
              :placeholder="t('profile.placeholder.phone')"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <div class="form-actions">
        <el-button @click="resetForm">{{ t('common.action.reset') }}</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ t('common.action.save') }}
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUserStore } from '@/store/user'
import { updateProfile, uploadAvatar } from '@/api/profile'

const props = defineProps({
  /** 父级传入的 ProfileVO（email/phone 为脱敏值，emailRaw/phoneRaw 为编辑原值） */
  profile: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['updated'])

const { t } = useI18n()
const userStore = useUserStore()

/** 头像上限 2MB，与后端 FileConfigService 校验保持一致 */
const AVATAR_MAX_SIZE = 2 * 1024 * 1024
const AVATAR_MIME = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/webp']
const EMAIL_REGEX = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/
const PHONE_REGEX = /^1[3-9]\d{9}$/

const formRef = ref(null)
const saving = ref(false)
const avatarLoading = ref(false)

const form = reactive({
  username: '',
  nickname: '',
  realName: '',
  email: '',
  phone: ''
})

const displayName = computed(
  () => props.profile.nickname || props.profile.realName || props.profile.username || ''
)

/** 邮箱：允许留空（后端 @Email 对空串放行），非空则校验格式 */
function validateEmail(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  if (!EMAIL_REGEX.test(value)) {
    callback(new Error(t('profile.msg.emailInvalid')))
    return
  }
  callback()
}

/** 手机：允许留空，非空则必须为 11 位中国大陆号码（与后端正则一致） */
function validatePhone(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  if (!PHONE_REGEX.test(value)) {
    callback(new Error(t('profile.msg.phoneInvalid')))
    return
  }
  callback()
}

const rules = computed(() => ({
  nickname: [
    { required: true, message: t('profile.msg.nicknameRequired'), trigger: 'blur' },
    { max: 50, message: t('profile.msg.nicknameRequired'), trigger: 'blur' }
  ],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }]
}))

/** 用 ProfileVO 回填表单：邮箱/手机取 Raw 原值，不取脱敏值 */
function fillForm(source) {
  const vo = source || {}
  form.username = vo.username || ''
  form.nickname = vo.nickname || ''
  form.realName = vo.realName || ''
  form.email = vo.emailRaw !== undefined && vo.emailRaw !== null ? vo.emailRaw : ''
  form.phone = vo.phoneRaw !== undefined && vo.phoneRaw !== null ? vo.phoneRaw : ''
}

function resetForm() {
  fillForm(props.profile)
  if (formRef.value) formRef.value.clearValidate()
}

watch(
  () => props.profile,
  (value) => {
    fillForm(value)
  },
  { immediate: true, deep: true }
)

/** 上传前校验：类型 + 大小，任一不过返回 false 阻止上传 */
function beforeAvatarUpload(file) {
  if (!AVATAR_MIME.includes(file.type)) {
    ElMessage.error(t('profile.msg.avatarTypeError'))
    return false
  }
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.error(t('profile.msg.avatarSizeError'))
    return false
  }
  return true
}

/**
 * 自定义上传：走 api/profile.uploadAvatar，成功后同步 store 让顶栏头像即时刷新。
 * @param {{file: File}} options el-upload 注入的上传上下文
 */
async function doUploadAvatar(options) {
  avatarLoading.value = true
  try {
    const path = await uploadAvatar(options.file)
    ElMessage.success(t('profile.msg.avatarUploaded'))
    // 同步顶栏：写 store 并自增版本号，UserAvatar 监听到即重新拉流
    userStore.setAvatar(path || '')
    emit('updated', { ...props.profile, avatar: path || '' })
  } catch (e) {
    // 具体原因由 request 拦截器统一提示
  } finally {
    avatarLoading.value = false
  }
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const vo = await updateProfile({
      nickname: form.nickname,
      realName: form.realName,
      email: form.email,
      phone: form.phone
    })
    ElMessage.success(t('profile.msg.saved'))
    emit('updated', vo)
    // 顶栏展示名可能变化，刷新登录态用户信息
    await userStore.refreshUserInfo()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.avatar-row {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.avatar-tip {
  font-size: 12px;
  color: var(--text-secondary);
}

.field-tip {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.profile-form {
  max-width: 720px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .avatar-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-actions {
    justify-content: stretch;
  }

  .form-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>

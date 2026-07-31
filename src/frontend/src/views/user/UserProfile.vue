<template>
  <!-- R1 个人中心壳：左概要卡（桌面 280px）+ 右三 Tab；移动端上下堆叠、Tab 横向滚动
       路由 /user/profile，挂在 UserLayout 下，主题随前台自动继承（ARCH §2.5-96） -->
  <div class="user-profile" v-loading="loading">
    <div class="up-layout">
      <!-- 左：概要卡 -->
      <el-card class="up-summary" shadow="never" :body-style="{ padding: '20px' }">
        <div class="up-summary__avatar">
          <UserAvatar
            :user-id="profile.id"
            :avatar="profile.avatar"
            :name="displayName"
            :size="72"
            :version="userStore.avatarVersion"
          />
        </div>
        <div class="up-summary__name">{{ displayName || '-' }}</div>
        <div v-if="profile.roleName" class="up-summary__role">
          <el-tag size="small" effect="light">{{ profile.roleName }}</el-tag>
        </div>
        <el-divider class="up-summary__divider" />
        <ul class="up-summary__list">
          <li>
            <span class="k">{{ t('profile.summary.username') }}</span>
            <span class="v">{{ profile.username || '-' }}</span>
          </li>
          <li>
            <span class="k">{{ t('profile.summary.org') }}</span>
            <span class="v">{{ profile.orgName || t('profile.summary.unset') }}</span>
          </li>
          <li>
            <span class="k">{{ t('profile.basic.email') }}</span>
            <span class="v">{{ profile.email || t('profile.summary.unset') }}</span>
          </li>
          <li>
            <span class="k">{{ t('profile.basic.phone') }}</span>
            <span class="v">{{ profile.phone || t('profile.summary.unset') }}</span>
          </li>
          <li>
            <span class="k">{{ t('profile.summary.joinedAt') }}</span>
            <span class="v">{{ profile.createdAt || '-' }}</span>
          </li>
        </ul>
      </el-card>

      <!-- 右：三 Tab -->
      <el-card class="up-main" shadow="never" :body-style="{ padding: '8px 20px 20px' }">
        <el-tabs v-model="activeTab" class="up-tabs">
          <el-tab-pane :label="t('profile.tab.basic')" name="basic">
            <ProfileBasic :profile="profile" @updated="onProfileUpdated" />
          </el-tab-pane>
          <el-tab-pane :label="t('profile.tab.security')" name="security" lazy>
            <ProfileSecurity :profile="profile" @updated="onProfileUpdated" />
          </el-tab-pane>
          <el-tab-pane :label="t('profile.tab.activity')" name="activity" lazy>
            <ProfileActivity />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import UserAvatar from '@/components/UserAvatar.vue'
import ProfileBasic from './profile/ProfileBasic.vue'
import ProfileSecurity from './profile/ProfileSecurity.vue'
import ProfileActivity from './profile/ProfileActivity.vue'
import { useUserStore } from '@/store/user'
import { getProfile } from '@/api/profile'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('basic')
/** 当前用户 ProfileVO，作为三个 Tab 的唯一数据源 */
const profile = ref({})

const displayName = computed(
  () => profile.value.nickname || profile.value.realName || profile.value.username || ''
)

/**
 * 子组件保存成功后回传最新 VO；部分场景（头像上传）只回传增量，做浅合并。
 * @param {Object} vo 最新 ProfileVO 或增量对象
 */
function onProfileUpdated(vo) {
  if (!vo) {
    loadProfile()
    return
  }
  profile.value = { ...profile.value, ...vo }
}

async function loadProfile() {
  loading.value = true
  try {
    profile.value = (await getProfile()) || {}
  } catch (e) {
    ElMessage.error(t('profile.msg.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.up-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.up-summary {
  width: 280px;
  flex-shrink: 0;
  text-align: center;
}

.up-summary__avatar {
  display: flex;
  justify-content: center;
}

.up-summary__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 12px;
  word-break: break-all;
}

.up-summary__role {
  margin-top: 6px;
}

.up-summary__divider {
  margin: 16px 0;
}

.up-summary__list {
  list-style: none;
  margin: 0;
  padding: 0;
  text-align: left;
}

.up-summary__list li {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  line-height: 2;
}

.up-summary__list .k {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.up-summary__list .v {
  color: var(--text-regular);
  text-align: right;
  word-break: break-all;
}

.up-main {
  flex: 1;
  min-width: 0;
}

/* 移动端：概要卡置顶堆叠，Tab 头横向可滚动 */
@media (max-width: 768px) {
  .up-layout {
    flex-direction: column;
  }

  .up-summary {
    width: 100%;
  }

  .up-tabs :deep(.el-tabs__nav-wrap) {
    overflow-x: auto;
  }

  .up-tabs :deep(.el-tabs__nav) {
    white-space: nowrap;
  }
}
</style>

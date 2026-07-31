<template>
  <el-result icon="error" title="403" :sub-title="t('error.403.desc')">
    <template #extra>
      <el-button type="primary" @click="goHome">{{ t('error.action.backHome') }}</el-button>
    </template>
  </el-result>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()
function goHome() {
  router.replace('/')
}
</script>

<template>
  <el-result icon="error" title="403" sub-title="抱歉，您没有权限访问该页面">
    <template #extra>
      <el-button type="primary" @click="goHome">返回首页</el-button>
    </template>
  </el-result>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function goHome() {
  router.replace('/')
}
</script>

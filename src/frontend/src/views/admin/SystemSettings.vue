<template>
  <div class="system-settings">
    <el-card class="page-card" shadow="never">
      <template #header><span>系统设置</span></template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="以下配置仅管理员可见；主题色切换即时生效并持久化到后端。"
        style="margin-bottom: 16px"
      />
      <ThemeConfigPanel />
    </el-card>
  </div>
</template>

<script setup>
import ThemeConfigPanel from '@/components/ThemeConfigPanel.vue'
</script>

<style scoped>
.system-settings {
  max-width: 720px;
}
</style>

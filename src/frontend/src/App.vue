<template>
  <router-view />
</template>

<script setup>
/**
 * 根组件：仅承载路由出口。
 * 全局 ElMessage / ElLoading / ElMessageBox 由 Element Plus 自动 teleport 到 body，
 * 无需在此显式声明容器。
 */
</script>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
}
</style>

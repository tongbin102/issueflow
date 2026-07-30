<template>
  <div class="blank-layout">
    <router-view />
  </div>
</template>

<script setup>
/**
 * BlankLayout：登录 / 错误页外壳。
 * 居中卡片容器，承载 Login / NotFound / Forbidden 等页面。
 */
</script>

<style scoped>
.blank-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: linear-gradient(135deg, #eef4ff 0%, #f5f7fa 100%);
}
</style>

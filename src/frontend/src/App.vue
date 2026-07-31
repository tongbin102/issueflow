<template>
  <!-- Phase6：el-config-provider 注入 Element Plus 语言包，随 locale store 响应式切换 -->
  <el-config-provider :locale="localeStore.elLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
/**
 * 根组件：承载路由出口 + Element Plus 语言注入 + 浏览器标题同步。
 * 全局 ElMessage / ElLoading / ElMessageBox 由 Element Plus 自动 teleport 到 body，
 * 无需在此显式声明容器。
 */
import { watch } from 'vue'
import { ElConfigProvider } from 'element-plus'
import { useLocaleStore } from '@/store/locale'
import { useAppStore } from '@/store/app'

const localeStore = useLocaleStore()
const appStore = useAppStore()

// 语言 / 站点配置变化时同步浏览器标题（网站名称 - 副标题）
watch(
  () => [localeStore.locale, appStore.siteName, appStore.siteSubtitle],
  () => {
    const name = appStore.siteName || 'issueFlow'
    const subtitle = appStore.siteSubtitle
    document.title = subtitle ? `${name} - ${subtitle}` : name
  },
  { immediate: true }
)
</script>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
}
</style>

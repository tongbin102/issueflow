<template>
  <!-- 前台顶栏主题切换（仅 UserLayout 使用，后台不放）：调色板图标 + 色块预览 + 打勾 -->
  <el-dropdown trigger="click" @command="onCommand">
    <span class="if-theme-switch" :title="t('theme.action.switch')">
      <el-icon><Brush /></el-icon>
      <span class="if-theme-switch__label">{{ t('theme.name.' + themeStore.frontTheme) }}</span>
      <el-icon class="if-theme-switch__arrow"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="item in THEME_ITEMS" :key="item.key" :command="item.key">
          <span class="if-theme-switch__item">
            <span class="if-theme-switch__swatch" :style="{ background: item.color }" />
            <span class="if-theme-switch__name">{{ t('theme.name.' + item.key) }}</span>
            <el-icon v-if="themeStore.frontTheme === item.key"><Check /></el-icon>
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/store/theme'

const { t } = useI18n()
const themeStore = useThemeStore()

/** 4 套主题（Q2 决策）：key 与 themes.css 的 data-if-theme 值一致 */
const THEME_ITEMS = [
  { key: 'light', color: '#409EFF' },
  { key: 'dark', color: '#1E1E20' },
  { key: 'blue', color: '#1E6FFF' },
  { key: 'green', color: '#17A97C' }
]

function onCommand(key) {
  themeStore.setFrontTheme(key)
}
</script>

<style scoped>
.if-theme-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 13px;
  color: inherit;
  outline: none;
}

.if-theme-switch__arrow {
  font-size: 12px;
}

.if-theme-switch__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}

.if-theme-switch__swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  flex-shrink: 0;
}

.if-theme-switch__name {
  flex: 1;
}
</style>

<template>
  <!-- 顶栏语言切换：地球图标 + 当前语言名，下拉项带打勾 -->
  <el-dropdown trigger="click" @command="onCommand">
    <span class="if-locale-switch" :title="t('locale.action.switch')">
      <el-icon><Position /></el-icon>
      <span class="if-locale-switch__label">{{ currentName }}</span>
      <el-icon class="if-locale-switch__arrow"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="item in LOCALE_ITEMS"
          :key="item.value"
          :command="item.value"
        >
          <span class="if-locale-switch__item">
            <span>{{ item.label }}</span>
            <el-icon v-if="localeStore.locale === item.value"><Check /></el-icon>
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleStore } from '@/store/locale'

const { t } = useI18n()
const localeStore = useLocaleStore()

/** 语言列表：语言名固定母语显示，不随当前语言翻译 */
const LOCALE_ITEMS = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' }
]

const currentName = computed(() => {
  const hit = LOCALE_ITEMS.find((item) => item.value === localeStore.locale)
  return hit ? hit.label : localeStore.locale
})

function onCommand(key) {
  localeStore.setLocale(key)
}
</script>

<style scoped>
.if-locale-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 13px;
  color: inherit;
  outline: none;
}

.if-locale-switch__label {
  max-width: 80px;
  overflow: hidden;
  white-space: nowrap;
}

.if-locale-switch__arrow {
  font-size: 12px;
}

.if-locale-switch__item {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 96px;
}
</style>

<template>
  <el-drawer
    :model-value="modelValue"
    title="整体风格设置"
    direction="rtl"
    size="360px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div class="style-drawer">
      <el-divider content-position="left">主题模式</el-divider>
      <el-radio-group v-model="local.themeMode" @change="onChange">
        <el-radio-button label="亮色" value="light" />
        <el-radio-button label="暗色" value="dark" />
      </el-radio-group>

      <el-divider content-position="left">主题色</el-divider>
      <div class="color-dots">
        <button
          v-for="c in ADMIN_THEME_COLORS"
          :key="c.value"
          type="button"
          class="color-dot"
          :class="{ active: isActiveColor(c.value) }"
          :style="{ background: c.value }"
          :title="c.label"
          @click="selectColor(c.value)"
        />
      </div>

      <el-divider content-position="left">侧边菜单类型</el-divider>
      <el-radio-group v-model="local.sidebarType" @change="onChange">
        <el-radio-button label="深色" value="dark" />
        <el-radio-button label="浅色" value="light" />
      </el-radio-group>

      <el-divider content-position="left">内容区域宽度</el-divider>
      <el-select v-model="local.contentWidth" @change="onChange" style="width: 100%">
        <el-option label="流式" value="fluid" />
        <el-option label="固定 1200px" value="fixed" />
      </el-select>

      <el-divider content-position="left">布局</el-divider>
      <div class="switch-row">
        <span>固定 Header</span>
        <el-switch v-model="local.fixedHeader" @change="onChange" />
      </div>
      <div class="switch-row">
        <span>固定侧边菜单</span>
        <el-switch v-model="local.fixedSidebar" @change="onChange" />
      </div>
      <div class="switch-row">
        <span>色弱模式</span>
        <el-switch v-model="local.colorWeak" @change="onChange" />
      </div>

      <el-divider />
      <el-button text type="primary" @click="resetDefault">恢复默认</el-button>
    </div>
  </el-drawer>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { applyAdminStyleVars } from '@/utils/theme'
import {
  ADMIN_THEME_COLORS,
  DEFAULT_ADMIN_STYLE,
  saveAdminStyle
} from '@/utils/adminStyle'

const props = defineProps({
  /** 抽屉可见性（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 当前后台风格对象 */
  state: { type: Object, default: () => ({ ...DEFAULT_ADMIN_STYLE }) }
})
const emit = defineEmits(['update:modelValue', 'change'])

const local = reactive({ ...props.state })

watch(
  () => props.state,
  (v) => {
    if (v) Object.assign(local, v)
  },
  { deep: true, immediate: true }
)

function isActiveColor(value) {
  return (
    local.themeColor &&
    String(local.themeColor).toLowerCase() === String(value).toLowerCase()
  )
}

function selectColor(value) {
  local.themeColor = value
  onChange()
}

/**
 * 任一控件变更：即时应用 + 持久化 + 通知父组件。
 */
function onChange() {
  const next = { ...local }
  const rootEl = document.querySelector('.if-layout--admin')
  if (rootEl) applyAdminStyleVars(next, rootEl)
  saveAdminStyle(next)
  emit('change', next)
}

function resetDefault() {
  Object.assign(local, DEFAULT_ADMIN_STYLE)
  onChange()
}
</script>

<style scoped>
.style-drawer {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.color-dots {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.color-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  outline: none;
  transition: transform 0.12s ease;
}
.color-dot:hover {
  transform: scale(1.12);
}
.color-dot.active {
  border-color: var(--el-text-color-primary);
  box-shadow: 0 0 0 2px #fff inset;
}
.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
}
</style>

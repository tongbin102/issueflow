<template>
  <div class="theme-config-panel">
    <el-form label-width="96px" label-position="right">
      <el-form-item label="主题色">
        <el-color-picker v-model="themeColor" @change="onThemeColorChange" />
        <span class="hint">{{ themeColor }}</span>
      </el-form-item>

      <el-form-item label="布局模式">
        <el-radio-group v-model="layout" @change="onLayoutChange">
          <el-radio-button value="side">侧边</el-radio-button>
          <el-radio-button value="top">顶部</el-radio-button>
          <el-radio-button value="mix">混合</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-divider content-position="left">菜单配置</el-divider>

      <el-form-item label="显示统计卡">
        <el-switch v-model="menuConfig.showStats" @change="applyMenuConfig" />
      </el-form-item>
      <el-form-item label="显示流程监控">
        <el-switch v-model="menuConfig.showFlow" @change="applyMenuConfig" />
      </el-form-item>
      <el-form-item label="菜单 JSON">
        <el-input
          v-model="menuJson"
          type="textarea"
          :rows="5"
          placeholder='{"showStats":true,"showFlow":true}'
          @change="onJsonChange"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useThemeStore } from '@/store/theme'
import { getConfig, setConfig } from '@/api/sysConfig'

const themeStore = useThemeStore()

const themeColor = ref(themeStore.themeColor)
const layout = ref(themeStore.layout || 'side')
const menuConfig = reactive({ showStats: true, showFlow: true })
const menuJson = ref('')

function syncJson() {
  menuJson.value = JSON.stringify({ ...menuConfig }, null, 2)
}

function applyMenuConfig() {
  syncJson()
  themeStore.setTheme({ menuConfig: { ...menuConfig } })
  persistConfig('menu_config', JSON.stringify(menuConfig))
}

function onJsonChange(val) {
  try {
    const obj = JSON.parse(val || '{}')
    Object.assign(menuConfig, obj)
    themeStore.setTheme({ menuConfig: { ...menuConfig } })
    persistConfig('menu_config', JSON.stringify(menuConfig))
  } catch (e) {
    ElMessage.error('菜单 JSON 格式不合法')
  }
}

function onThemeColorChange(color) {
  if (!color) return
  themeStore.setThemeColor(color)
  persistConfig('theme_color', color)
}

function onLayoutChange(value) {
  if (!value) return
  themeStore.setTheme({ layout: value })
  persistConfig('layout', value)
}

function persistConfig(configKey, configValue) {
  setConfig({ configKey, configValue })
    .then(() => ElMessage.success('已保存'))
    .catch(() => {})
}

onMounted(async () => {
  try {
    const map = await getConfig()
    if (map && typeof map === 'object') {
      if (map.theme_color) {
        themeColor.value = map.theme_color
        themeStore.setThemeColor(map.theme_color)
      }
      if (map.layout) {
        layout.value = map.layout
        themeStore.setTheme({ layout: map.layout })
      }
      if (map.menu_config) {
        const obj =
          typeof map.menu_config === 'string'
            ? JSON.parse(map.menu_config || '{}')
            : map.menu_config
        Object.assign(menuConfig, obj || {})
        syncJson()
      }
    }
  } catch (e) {
    /* 配置读取失败时用本地默认值 */
  }
  syncJson()
})
</script>

<style scoped>
.theme-config-panel {
  padding: 4px;
}
.hint {
  margin-left: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}
</style>

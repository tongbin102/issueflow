<template>
  <!-- T6：网站设置（site.* 七键：名称/简称/副标题/默认主题/默认语言/版权/备案号） -->
  <div class="site-settings">
    <el-card class="page-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="head">
          <span>{{ t('site.page.title') }}</span>
          <div>
            <el-button @click="onRestoreDefault">{{ t('site.action.restoreDefault') }}</el-button>
            <el-button type="primary" :loading="saving" @click="onSave">{{
              t('common.action.save')
            }}</el-button>
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
        class="site-form"
      >
        <!-- 基础信息 -->
        <el-divider content-position="left">{{ t('site.group.basic') }}</el-divider>
        <el-form-item :label="t('site.form.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('site.rules.nameRequired')" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('site.form.shortName')" prop="shortName">
          <el-input
            v-model="form.shortName"
            :placeholder="t('site.rules.shortNameRequired')"
            maxlength="20"
            show-word-limit
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item :label="t('site.form.subtitle')" prop="subtitle">
          <el-input v-model="form.subtitle" maxlength="100" show-word-limit />
        </el-form-item>

        <!-- 外观默认值 -->
        <el-divider content-position="left">{{ t('site.group.appearance') }}</el-divider>
        <el-form-item :label="t('site.form.defaultTheme')" prop="defaultTheme">
          <el-radio-group v-model="form.defaultTheme">
            <el-radio-button v-for="item in THEME_ITEMS" :key="item.key" :value="item.key">
              <span class="theme-option">
                <span class="theme-dot" :style="{ backgroundColor: item.color }" />
                {{ t('theme.name.' + item.key) }}
              </span>
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('site.form.defaultLocale')" prop="defaultLocale">
          <el-radio-group v-model="form.defaultLocale">
            <el-radio-button value="zh-CN">{{ t('locale.name.zhCN') }}</el-radio-button>
            <el-radio-button value="en-US">{{ t('locale.name.enUS') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 页脚信息 -->
        <el-divider content-position="left">{{ t('site.group.footer') }}</el-divider>
        <el-form-item :label="t('site.form.copyright')" prop="copyright">
          <el-input v-model="form.copyright" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('site.form.icp')" prop="icp">
          <el-input v-model="form.icp" maxlength="50" style="width: 320px" />
        </el-form-item>
      </el-form>

      <!-- T8：数据维护区（与顶部保存区视觉分离：独立分组 + 右侧独立按钮组 + plain 下载图标） -->
      <el-divider content-position="left">{{ t('backup.entry.group') }}</el-divider>
      <div class="maintenance">
        <div class="maintenance__desc">{{ t('backup.entry.desc') }}</div>
        <div class="maintenance__actions">
          <el-button v-perm="'system:backup:export'" plain :icon="Download" @click="openBackup">
            {{ t('backup.action.open') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 备份确认抽屉（FormDrawer sm） -->
    <BackupDrawer v-model="backupVisible" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getSiteConfig, saveSiteConfig } from '@/api/site'
import { useAppStore } from '@/store/app'
import BackupDrawer from '@/components/BackupDrawer.vue'

const { t } = useI18n()
const appStore = useAppStore()

/** 主题选项（与 ThemeSwitch / themes.css 保持一致的 4 主题与预览色） */
const THEME_ITEMS = [
  { key: 'light', color: '#409EFF' },
  { key: 'dark', color: '#1E1E20' },
  { key: 'blue', color: '#1E6FFF' },
  { key: 'green', color: '#17A97C' }
]

/** 前端默认值（与后端 SiteConfigService.defaults() 镜像一致） */
const DEFAULTS = {
  name: 'issueFlow',
  shortName: 'IF',
  subtitle: '问题跟踪与流程管理平台',
  defaultTheme: 'light',
  defaultLocale: 'zh-CN',
  copyright: '(c) 2026 issueFlow',
  icp: ''
}

const loading = ref(false)
const saving = ref(false)
const formRef = ref(null)

/** T8：备份抽屉显隐 */
const backupVisible = ref(false)

/** 打开备份确认抽屉（抽屉内部自动拉取预估） */
function openBackup() {
  backupVisible.value = true
}

const form = reactive({ ...DEFAULTS })

const rules = computed(() => ({
  name: [{ required: true, message: t('site.rules.nameRequired'), trigger: 'blur' }],
  shortName: [{ required: true, message: t('site.rules.shortNameRequired'), trigger: 'blur' }]
}))

/** 后端返回的 site.* 扁平 Map → 表单字段 */
function applyConfig(cfg) {
  if (!cfg) return
  form.name = cfg['site.name'] ?? DEFAULTS.name
  form.shortName = cfg['site.short_name'] ?? DEFAULTS.shortName
  form.subtitle = cfg['site.subtitle'] ?? DEFAULTS.subtitle
  form.defaultTheme = cfg['site.default_theme'] ?? DEFAULTS.defaultTheme
  form.defaultLocale = cfg['site.default_locale'] ?? DEFAULTS.defaultLocale
  form.copyright = cfg['site.copyright'] ?? DEFAULTS.copyright
  form.icp = cfg['site.icp'] ?? DEFAULTS.icp
}

async function load() {
  loading.value = true
  try {
    const cfg = await getSiteConfig()
    applyConfig(cfg)
  } catch (e) {
    ElMessage.warning(t('site.msg.loadError'))
  } finally {
    loading.value = false
  }
}

function onRestoreDefault() {
  Object.assign(form, { ...DEFAULTS })
  ElMessage.info(t('site.msg.restoreTip'))
}

function onSave() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveSiteConfig({
        name: form.name,
        shortName: form.shortName,
        subtitle: form.subtitle,
        defaultTheme: form.defaultTheme,
        defaultLocale: form.defaultLocale,
        copyright: form.copyright,
        icp: form.icp
      })
      ElMessage.success(t('site.msg.saveSuccess'))
      // 本地同步 app store，登录页 / 标题即时生效，无需刷新
      appStore.setSiteConfig({
        'site.name': form.name,
        'site.short_name': form.shortName,
        'site.subtitle': form.subtitle,
        'site.default_theme': form.defaultTheme,
        'site.default_locale': form.defaultLocale,
        'site.copyright': form.copyright,
        'site.icp': form.icp
      })
    } catch (e) {
      // 错误提示由 request 拦截器统一处理
    } finally {
      saving.value = false
    }
  })
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.site-form {
  max-width: 720px;
}

.theme-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.theme-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.15);
}

/* 数据维护区：左说明右按钮，窄屏换行堆叠 */
.maintenance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  max-width: 720px;
}

.maintenance__desc {
  flex: 1;
  min-width: 220px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.maintenance__actions {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .maintenance__actions {
    width: 100%;
  }

  .maintenance__actions .el-button {
    width: 100%;
  }
}
</style>

<template>
  <!-- Phase7 T6：基础设施 > 文件管理 > 文件配置（GET / PUT /api/admin/files/config） -->
  <div class="file-config">
    <el-card class="page-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="head">
          <span>{{ t('infra.file.config.title') }}</span>
          <div class="head__actions">
            <el-button :icon="Refresh" @click="fetchData">{{ t('common.action.refresh') }}</el-button>
            <el-button v-perm="'file:config'" type="primary" :icon="Edit" @click="openEdit">
              {{ t('infra.file.config.edit') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        :title="t('infra.file.config.desc')"
      />

      <!-- 目录不可写：显式告警（上传必失败，属运维问题） -->
      <el-alert
        v-if="config.writable === false"
        class="tip"
        type="warning"
        :closable="false"
        show-icon
        :title="t('infra.file.config.writableWarn')"
      />

      <!-- 配置项 -->
      <el-descriptions class="desc-block" :column="descColumn" border>
        <el-descriptions-item :label="t('infra.file.config.storageType')">
          <el-tag type="info" effect="plain">{{ config.storageType || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('infra.file.config.maxSizeMb')">
          {{ config.maxSizeMb ? config.maxSizeMb + ' ' + t('infra.file.config.unitMb') : '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('infra.file.config.storageRoot')" :span="descColumn">
          <span class="code-text">{{ config.storageRoot || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('infra.file.config.allowedExts')" :span="descColumn">
          <div class="ext-tags">
            <el-tag v-for="ext in extList" :key="ext" size="small" effect="plain">{{ ext }}</el-tag>
            <span v-if="!extList.length">-</span>
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 存储概览 -->
      <div class="stat-title">{{ t('infra.file.config.statTitle') }}</div>
      <el-row :gutter="12" class="stat-row">
        <el-col :xs="24" :sm="8">
          <div class="stat-card">
            <div class="stat-card__label">{{ t('infra.file.config.usedSize') }}</div>
            <div class="stat-card__value">{{ formatFileSize(config.usedSize) }}</div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="8">
          <div class="stat-card">
            <div class="stat-card__label">{{ t('infra.file.config.fileCount') }}</div>
            <div class="stat-card__value">
              {{ t('infra.file.config.countUnit', { count: config.fileCount ?? 0 }) }}
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="8">
          <div class="stat-card">
            <div class="stat-card__label">{{ t('infra.file.config.writable') }}</div>
            <div class="stat-card__value">
              <el-tag :type="config.writable === false ? 'danger' : 'success'" effect="light">
                {{
                  config.writable === false
                    ? t('infra.file.config.writableNo')
                    : t('infra.file.config.writableYes')
                }}
              </el-tag>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 编辑抽屉（R3 统一 FormDrawer） -->
    <FormDrawer
      v-model="drawerVisible"
      :title="t('infra.file.config.drawerTitle')"
      size="md"
      :loading="saving"
      @confirm="onSave"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="right">
        <el-form-item :label="t('infra.file.config.storageType')" prop="storageType">
          <el-select v-model="form.storageType" style="width: 100%">
            <el-option label="LOCAL" value="LOCAL" />
          </el-select>
          <div class="form-tip">{{ t('infra.file.config.typeTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('infra.file.config.storageRoot')" prop="storageRoot">
          <el-input v-model="form.storageRoot" maxlength="255" show-word-limit />
          <div class="form-tip">{{ t('infra.file.config.rootTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('infra.file.config.maxSizeMb')" prop="maxSizeMb">
          <el-input-number v-model="form.maxSizeMb" :min="1" :max="100" />
          <span class="unit-text">{{ t('infra.file.config.unitMb') }}</span>
        </el-form-item>
        <el-form-item :label="t('infra.file.config.allowedExts')" prop="allowedExts">
          <el-input
            v-model="form.allowedExts"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
          <div class="form-tip">{{ t('infra.file.config.extsTip') }}</div>
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Edit, Refresh } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import { getFileConfig, saveFileConfig } from '@/api/fileManage'
import { formatFileSize } from '@/utils/format'
import { useAppStore } from '@/store/app'

const { t } = useI18n()
const appStore = useAppStore()

const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const formRef = ref(null)

/** 当前配置（含只读统计字段 usedSize / fileCount / writable） */
const config = reactive({
  storageRoot: '',
  maxSizeMb: 20,
  allowedExts: '',
  storageType: 'LOCAL',
  usedSize: 0,
  fileCount: 0,
  writable: true
})

/** 编辑表单（只提交后端 FileConfigReq 的 4 个字段） */
const form = reactive({
  storageRoot: '',
  maxSizeMb: 20,
  allowedExts: '',
  storageType: 'LOCAL'
})

/** 移动端描述列表压成单列 */
const descColumn = computed(() => (appStore.isMobile ? 1 : 2))

/** 扩展名字符串 → 标签数组 */
const extList = computed(() =>
  (config.allowedExts || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
)

const rules = computed(() => ({
  storageRoot: [
    { required: true, message: t('infra.file.config.rules.rootRequired'), trigger: 'blur' }
  ],
  maxSizeMb: [
    { required: true, message: t('infra.file.config.rules.maxSizeRequired'), trigger: 'change' },
    {
      type: 'number',
      min: 1,
      max: 100,
      message: t('infra.file.config.rules.maxSizeRange'),
      trigger: 'change'
    }
  ],
  allowedExts: [
    { required: true, message: t('infra.file.config.rules.extsRequired'), trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9,\s]+$/,
      message: t('infra.file.config.rules.extsPattern'),
      trigger: 'blur'
    }
  ],
  storageType: [
    { required: true, message: t('common.msg.required'), trigger: 'change' }
  ]
}))

async function fetchData() {
  loading.value = true
  try {
    const data = (await getFileConfig()) || {}
    config.storageRoot = data.storageRoot || ''
    config.maxSizeMb = data.maxSizeMb ?? 20
    config.allowedExts = data.allowedExts || ''
    config.storageType = data.storageType || 'LOCAL'
    config.usedSize = data.usedSize ?? 0
    config.fileCount = data.fileCount ?? 0
    config.writable = data.writable !== false
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function openEdit() {
  form.storageRoot = config.storageRoot
  form.maxSizeMb = config.maxSizeMb
  form.allowedExts = config.allowedExts
  form.storageType = config.storageType || 'LOCAL'
  drawerVisible.value = true
}

function resetForm() {
  if (formRef.value) formRef.value.clearValidate()
}

function onSave() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        storageRoot: form.storageRoot.trim(),
        maxSizeMb: form.maxSizeMb,
        // 归一化：去空格、转小写，避免 「PNG, jpg」 这类写法导致后端校验漏判
        allowedExts: form.allowedExts
          .split(',')
          .map((item) => item.trim().toLowerCase())
          .filter(Boolean)
          .join(','),
        storageType: form.storageType
      }
      await saveFileConfig(payload)
      ElMessage.success(t('infra.file.config.saveSuccess'))
      drawerVisible.value = false
      fetchData()
    } catch (e) {
      // 校验类错误由 request 拦截器提示
    } finally {
      saving.value = false
    }
  })
}

onMounted(fetchData)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.head__actions {
  display: flex;
  gap: 8px;
}

.tip {
  margin-bottom: 12px;
}

.desc-block {
  margin-bottom: 20px;
}

.code-text {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  background: var(--if-code-bg, var(--el-fill-color-light));
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}

.ext-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.stat-title {
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.stat-row {
  row-gap: 12px;
}

.stat-card {
  background: var(--if-stat-card-bg, var(--el-fill-color-lighter));
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--if-radius, 4px);
  padding: 16px;
  height: 100%;
}

.stat-card__label {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-card__value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.form-tip {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.unit-text {
  margin-left: 8px;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .head__actions {
    width: 100%;
  }
}
</style>

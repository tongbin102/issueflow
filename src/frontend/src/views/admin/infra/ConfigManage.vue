<template>
  <!-- Phase7 T6：基础设施 > 配置管理
       ARCH §7 Q11 决策「同源不同视图」：后端没有独立的配置管理端点，
       本页聚合 sys_config / site.* / file.* 三处配置源做统一只读视图，
       其中 sys / flow 分组支持就地改值（PUT /api/sys/config），
       site / file 分组打「只读」tag 并给出跳转入口，避免出现第二个写入真源。 -->
  <div class="config-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('infra.config.title') }}</span>
          <div class="head__actions">
            <el-button :icon="Refresh" @click="fetchData">{{ t('common.action.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <el-alert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        :title="t('infra.config.desc')"
      />

      <!-- 筛选 + 快捷入口 -->
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item :label="t('common.field.keyword')">
          <el-input
            v-model="keyword"
            :placeholder="t('common.placeholder.search')"
            clearable
            class="filter-input"
          />
        </el-form-item>
        <el-form-item :label="t('infra.config.col.group')">
          <el-select v-model="group" class="filter-select">
            <el-option :label="t('infra.config.group.all')" value="" />
            <el-option
              v-for="key in GROUP_KEYS"
              :key="key"
              :label="t('infra.config.group.' + key)"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button link type="primary" @click="goSite">{{ t('infra.config.gotoSite') }}</el-button>
          <el-button link type="primary" @click="goFileConfig">
            {{ t('infra.config.gotoFile') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="table-wrap">
        <el-table v-loading="loading" :data="filteredList" border stripe style="width: 100%">
          <el-table-column :label="t('infra.config.col.group')" width="130">
            <template #default="{ row }">
              <el-tag :type="groupTagType(row.group)" size="small" effect="light">
                {{ t('infra.config.group.' + row.group) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="key"
            :label="t('infra.config.col.key')"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span class="code-text">{{ row.key }}</span>
            </template>
          </el-table-column>
          <el-table-column
            prop="value"
            :label="t('infra.config.col.value')"
            min-width="260"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span v-if="row.value === ''" class="text-muted">
                {{ t('infra.config.emptyValue') }}
              </span>
              <span v-else class="code-text">{{ row.value }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.config.col.source')" width="140" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.builtin" type="info" size="small" effect="plain">
                {{ t('infra.config.builtin') }}
              </el-tag>
              <el-tag v-if="!row.editable" type="warning" size="small" effect="plain" class="ml-4">
                {{ t('infra.config.readonly') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.config.col.actions')" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.editable"
                v-perm="'settings:update'"
                link
                type="primary"
                size="small"
                @click="openEdit(row)"
              >
                {{ t('common.action.edit') }}
              </el-button>
              <el-button
                v-else-if="row.group === 'SITE'"
                link
                type="primary"
                size="small"
                @click="goSite"
              >
                {{ t('infra.config.gotoSite') }}
              </el-button>
              <el-button
                v-else-if="row.group === 'FILE'"
                link
                type="primary"
                size="small"
                @click="goFileConfig"
              >
                {{ t('infra.config.gotoFile') }}
              </el-button>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="t('infra.config.empty')" :image-size="60" />
          </template>
        </el-table>
      </div>

      <div class="foot-tip text-muted">{{ t('infra.config.builtinTip') }}</div>
    </el-card>

    <!-- 编辑抽屉（R3 统一 FormDrawer） -->
    <FormDrawer
      v-model="drawerVisible"
      :title="t('infra.config.editTitle')"
      size="md"
      :loading="saving"
      @confirm="onSave"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="right">
        <el-form-item :label="t('infra.config.keyLabel')">
          <el-input v-model="form.key" disabled />
        </el-form-item>
        <el-form-item :label="t('infra.config.valueLabel')" prop="value">
          <!-- 布尔型开关（流程分组）用 switch，其余用文本域 -->
          <el-switch
            v-if="isBooleanValue"
            v-model="form.boolValue"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
          />
          <el-input v-else v-model="form.value" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item v-if="editingGroup === 'SITE'">
          <el-alert type="warning" :closable="false" show-icon :title="t('infra.config.siteTip')" />
        </el-form-item>
        <el-form-item v-if="editingGroup === 'FILE'">
          <el-alert type="warning" :closable="false" show-icon :title="t('infra.config.fileTip')" />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import { listConfigEntries, saveConfigEntry, CONFIG_GROUP } from '@/api/configManage'

const { t } = useI18n()
const router = useRouter()

/** 分组枚举顺序（与 i18n infra.config.group.* 对应） */
const GROUP_KEYS = [CONFIG_GROUP.SYS, CONFIG_GROUP.FLOW, CONFIG_GROUP.SITE, CONFIG_GROUP.FILE]

const loading = ref(false)
const saving = ref(false)
const entries = ref([])
const keyword = ref('')
const group = ref('')

const drawerVisible = ref(false)
const formRef = ref(null)
const editingGroup = ref('')

const form = reactive({
  key: '',
  value: '',
  boolValue: false
})

/** 流程开关等布尔配置：用 switch 编辑，落库仍是 'true'/'false' 字符串 */
const isBooleanValue = computed(() => editingGroup.value === CONFIG_GROUP.FLOW)

const rules = computed(() => ({
  value: isBooleanValue.value
    ? []
    : [{ required: true, message: t('infra.config.valueRequired'), trigger: 'blur' }]
}))

/** 前端筛选：条目总量很小（十余条），无需服务端分页 */
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return entries.value.filter((item) => {
    if (group.value && item.group !== group.value) return false
    if (!kw) return true
    return (
      item.key.toLowerCase().includes(kw) || String(item.value).toLowerCase().includes(kw)
    )
  })
})

/**
 * 分组 → el-tag 类型。
 * @param {string} value SYS / FLOW / SITE / FILE
 * @returns {string}
 */
function groupTagType(value) {
  const map = { SYS: 'primary', FLOW: 'success', SITE: 'warning', FILE: 'info' }
  return map[value] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    entries.value = (await listConfigEntries()) || []
  } catch (e) {
    entries.value = []
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  editingGroup.value = row.group
  form.key = row.key
  form.value = row.value || ''
  form.boolValue = String(row.value).toLowerCase() === 'true'
  drawerVisible.value = true
}

function resetForm() {
  editingGroup.value = ''
  form.key = ''
  form.value = ''
  form.boolValue = false
  if (formRef.value) formRef.value.clearValidate()
}

function onSave() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const value = isBooleanValue.value ? String(form.boolValue) : form.value
      await saveConfigEntry(form.key, value)
      ElMessage.success(t('infra.config.saveSuccess'))
      drawerVisible.value = false
      fetchData()
    } catch (e) {
      // 错误提示由 request 拦截器统一处理
    } finally {
      saving.value = false
    }
  })
}

function goSite() {
  router.push('/admin/system/site')
}

function goFileConfig() {
  router.push('/admin/infra/file/config')
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

.filter-form {
  margin-bottom: 4px;
}

.filter-input {
  width: 220px;
}

.filter-select {
  width: 150px;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.code-text {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  background: var(--if-code-bg, var(--el-fill-color-light));
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}

.ml-4 {
  margin-left: 4px;
}

.foot-tip {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 768px) {
  .filter-input,
  .filter-select {
    width: 100%;
  }
  .filter-form :deep(.el-form-item) {
    display: block;
    margin-right: 0;
  }
}
</style>

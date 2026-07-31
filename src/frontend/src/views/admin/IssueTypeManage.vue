<template>
  <!-- T6：问题类型管理（Q5：与问题管理同级平铺的兄弟菜单页面） -->
  <div class="issue-type-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('issueType.page.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">{{
            t('common.action.create')
          }}</el-button>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item :label="t('common.field.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('common.placeholder.search')"
            clearable
            style="width: 200px"
            @keyup.enter="fetchData"
          />
        </el-form-item>
        <el-form-item :label="t('common.field.status')">
          <el-select
            v-model="query.enabled"
            :placeholder="t('common.status.all')"
            clearable
            style="width: 130px"
          >
            <el-option :label="t('common.status.enabled')" :value="1" />
            <el-option :label="t('common.status.disabled')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">{{
            t('common.action.search')
          }}</el-button>
          <el-button @click="onReset">{{ t('common.action.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column prop="name" :label="t('issueType.col.name')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="code" :label="t('issueType.col.code')" width="130" />
        <el-table-column prop="description" :label="t('issueType.col.description')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('issueType.col.sort')" width="80" align="center" />
        <el-table-column :label="t('issueType.col.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">
              {{ row.enabled ? t('common.status.enabled') : t('common.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('issueType.col.issueCount')" width="90" align="center">
          <template #default="{ row }">{{ row.issueCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('issueType.col.updatedAt')" width="170" />
        <el-table-column :label="t('issueType.col.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">{{
              t('common.action.edit')
            }}</el-button>
            <el-button
              link
              :type="row.enabled ? 'warning' : 'success'"
              size="small"
              @click="onToggle(row)"
              >{{ row.enabled ? t('common.action.disable') : t('common.action.enable') }}</el-button
            >
            <el-button link type="danger" size="small" @click="onDelete(row)">{{
              t('common.action.delete')
            }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑抽屉（R3 统一 FormDrawer） -->
    <FormDrawer
      v-model="drawerVisible"
      :title="isEdit ? t('issueType.drawer.editTitle') : t('issueType.drawer.createTitle')"
      size="sm"
      :loading="saving"
      @confirm="onSave"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="96px"
        label-position="right"
      >
        <el-form-item :label="t('issueType.form.name')" prop="name">
          <el-input
            v-model="form.name"
            :placeholder="t('issueType.placeholder.name')"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('issueType.form.code')" prop="code">
          <!-- 编码创建后不可改（作为存量数据契约，如 OTHER 兜底） -->
          <el-input
            v-model="form.code"
            :placeholder="t('issueType.placeholder.code')"
            maxlength="50"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('issueType.form.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :placeholder="t('issueType.placeholder.description')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('issueType.form.sort')" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('issueType.form.status')" prop="enabled">
          <el-switch
            v-model="form.enabled"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
          />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import {
  listIssueTypes,
  createIssueType,
  updateIssueType,
  toggleIssueTypeStatus,
  deleteIssueType
} from '@/api/issueType'
import { useIssueTypeStore } from '@/store/issueType'

const { t } = useI18n()
const issueTypeStore = useIssueTypeStore()

const loading = ref(false)
const list = ref([])
const query = reactive({ keyword: '', enabled: null })

const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const editingId = ref(null)
const isEdit = computed(() => editingId.value != null)

const form = reactive({
  name: '',
  code: '',
  description: '',
  sort: 0,
  enabled: true
})

/** 校验规则（i18n 响应式） */
const rules = computed(() => ({
  name: [{ required: true, message: t('issueType.rules.nameRequired'), trigger: 'blur' }],
  code: [
    { required: true, message: t('issueType.rules.codeRequired'), trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]*$/,
      message: t('issueType.rules.codePattern'),
      trigger: 'blur'
    }
  ]
}))

async function fetchData() {
  loading.value = true
  try {
    const params = {}
    if (query.keyword) params.keyword = query.keyword
    if (query.enabled !== null && query.enabled !== undefined && query.enabled !== '')
      params.enabled = query.enabled
    list.value = (await listIssueTypes(params)) || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onReset() {
  query.keyword = ''
  query.enabled = null
  fetchData()
}

function openCreate() {
  editingId.value = null
  drawerVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.name = row.name || ''
  form.code = row.code || ''
  form.description = row.description || ''
  form.sort = row.sort ?? 0
  form.enabled = row.enabled !== false
  drawerVisible.value = true
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { name: '', code: '', description: '', sort: 0, enabled: true })
  if (formRef.value) formRef.value.clearValidate()
}

function onSave() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        name: form.name,
        code: form.code,
        description: form.description,
        sort: form.sort,
        enabled: form.enabled
      }
      if (isEdit.value) {
        await updateIssueType(editingId.value, payload)
        ElMessage.success(t('issueType.msg.updateSuccess'))
      } else {
        await createIssueType(payload)
        ElMessage.success(t('issueType.msg.createSuccess'))
      }
      drawerVisible.value = false
      issueTypeStore.invalidate()
      fetchData()
    } catch (e) {
      // 错误提示（如编码重复 40061）由 request 拦截器统一处理
    } finally {
      saving.value = false
    }
  })
}

async function onToggle(row) {
  const next = !row.enabled
  try {
    await toggleIssueTypeStatus(row.id, next)
    ElMessage.success(
      next ? t('issueType.msg.switchToEnabled') : t('issueType.msg.switchToDisabled')
    )
    issueTypeStore.invalidate()
    fetchData()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

function onDelete(row) {
  ElMessageBox.confirm(
    t('issueType.msg.deleteConfirm', { name: row.name }),
    t('common.msg.warning'),
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteIssueType(row.id)
        ElMessage.success(t('issueType.msg.deleteSuccess'))
        issueTypeStore.invalidate()
        fetchData()
      } catch (e) {
        // 被引用阻断（40062）等错误由 request 拦截器提示
      }
    })
    .catch(() => {})
}

fetchData()
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  margin-bottom: 4px;
}
</style>

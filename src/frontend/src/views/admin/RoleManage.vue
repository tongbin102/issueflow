<template>
  <div class="role-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('role.page.title') }}</span>
          <div class="head-actions">
            <el-button :loading="refreshing" @click="onRefresh">{{ t('role.action.refreshCache') }}</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('role.action.create') }}</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="roles" border stripe row-key="id">
        <el-table-column prop="code" :label="t('role.col.code')" min-width="140" />
        <el-table-column prop="name" :label="t('role.col.name')" min-width="140" />
        <el-table-column prop="description" :label="t('role.col.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="permissionCount" :label="t('role.col.permissionCount')" width="90" align="center" />
        <el-table-column :label="t('role.col.builtin')" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.builtin" type="info" size="small">{{ t('role.col.builtin') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.action.operation')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPerm(row)">{{ t('role.action.assignPerm') }}</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.action.edit') }}</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.builtin"
              @click="onDelete(row)"
            >{{ t('common.action.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑角色（R3 统一抽屉） -->
    <FormDrawer
      v-model="dialogVisible"
      :title="form.id ? t('role.drawer.editTitle') : t('role.drawer.createTitle')"
      size="md"
      :loading="saving"
      @confirm="onSubmit"
      @closed="onDrawerClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item :label="t('role.col.code')" prop="code">
          <el-input
            v-model="form.code"
            :disabled="form.id && form.builtin"
            maxlength="50"
            show-word-limit
            :placeholder="t('role.placeholder.code')"
          />
        </el-form-item>
        <el-form-item :label="t('role.col.name')" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('role.col.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
    </FormDrawer>

    <!-- 分配权限（独立组件：前后台分栏 + 权限树 + 已选复核 + 变更确认） -->
    <AssignPermissionDialog
      v-model="permDialogVisible"
      :role="currentRole"
      @saved="fetchRoles"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  listRoles,
  createRole,
  updateRole,
  deleteRole,
  refreshPermissions
} from '@/api/role'
import { listPermissions } from '@/api/permission'
import FormDrawer from '@/components/FormDrawer.vue'
import AssignPermissionDialog from '@/components/AssignPermissionDialog.vue'

const { t } = useI18n()

const loading = ref(false)
const roles = ref([])
const refreshing = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, code: '', name: '', description: '', builtin: false })

const rules = computed(() => ({
  code: [{ required: true, message: t('role.msg.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('role.msg.nameRequired'), trigger: 'blur' }]
}))

/** 分配权限抽屉：显隐 + 当前角色，其余状态全部由 AssignPermissionDialog 内部自治 */
const permDialogVisible = ref(false)
const currentRole = ref(null)
/** 权限目录（预热缓存，供页面其他统计使用；抽屉内部会自行拉取最新目录） */
const allPermissions = ref([])

async function fetchRoles() {
  loading.value = true
  try {
    roles.value = (await listRoles()) || []
  } catch (e) {
    roles.value = []
  } finally {
    loading.value = false
  }
}

async function fetchPermissionsCatalog() {
  try {
    allPermissions.value = (await listPermissions()) || []
  } catch (e) {
    allPermissions.value = []
  }
}

function openCreate() {
  Object.assign(form, { id: null, code: '', name: '', description: '', builtin: false })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code || '',
    name: row.name || '',
    description: row.description || '',
    builtin: !!row.builtin
  })
  dialogVisible.value = true
}

function onSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    const payload = { code: form.code, name: form.name, description: form.description }
    try {
      if (form.id) {
        await updateRole(form.id, payload)
        ElMessage.success(t('role.msg.updateSuccess'))
      } else {
        await createRole(payload)
        ElMessage.success(t('role.msg.createSuccess'))
      }
      dialogVisible.value = false
      fetchRoles()
    } catch (e) {
      // 业务异常（角色码重复等）由拦截器统一提示
    } finally {
      saving.value = false
    }
  })
}

function onDelete(row) {
  ElMessageBox.confirm(t('role.msg.deleteConfirm', { name: row.name }), t('common.msg.tip'), { type: 'warning' })
    .then(async () => {
      try {
        await deleteRole(row.id)
        ElMessage.success(t('role.msg.deleteSuccess'))
        fetchRoles()
      } catch (e) {
        // 内置角色受保护等异常由拦截器提示
      }
    })
    .catch(() => {})
}

async function onRefresh() {
  refreshing.value = true
  try {
    await refreshPermissions()
    ElMessage.success(t('role.msg.cacheRefreshed'))
  } catch (e) {
  } finally {
    refreshing.value = false
  }
}

/** 抽屉关闭后重置表单与校验状态 */
function onDrawerClosed() {
  Object.assign(form, { id: null, code: '', name: '', description: '', builtin: false })
  formRef.value && formRef.value.clearValidate()
}

// ===== 权限分配：仅负责「选中角色 + 打开抽屉」，加载/保存由子组件自治 =====
function openPerm(row) {
  currentRole.value = row
  permDialogVisible.value = true
}

onMounted(() => {
  fetchRoles()
  fetchPermissionsCatalog()
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-actions {
  display: flex;
  gap: 8px;
}
</style>

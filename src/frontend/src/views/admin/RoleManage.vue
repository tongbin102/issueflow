<template>
  <div class="role-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>角色管理</span>
          <div class="head-actions">
            <el-button :loading="refreshing" @click="onRefresh">刷新权限缓存</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建角色</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="roles" border stripe row-key="id">
        <el-table-column prop="code" label="角色码" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="permissionCount" label="权限数" width="90" align="center" />
        <el-table-column label="内置" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.builtin" type="info" size="small">内置</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPerm(row)">分配权限</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.builtin"
              @click="onDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 / 编辑角色 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑角色' : '新建角色'"
      width="460px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="form.id && form.builtin"
            maxlength="50"
            show-word-limit
            placeholder="如 CUSTOM_ROLE"
          />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog
      v-model="permDialogVisible"
      title="分配权限"
      width="640px"
      append-to-body
      @open="onPermDialogOpen"
    >
      <div v-loading="permLoading" class="perm-dialog">
        <el-alert
          v-if="currentRole && currentRole.builtin"
          type="warning"
          :closable="false"
          title="内置角色权限可调整，但角色码不可修改、角色不可删除。"
          style="margin-bottom: 12px"
        />
        <el-input
          v-model="permKeyword"
          placeholder="搜索权限名称 / 编码"
          clearable
          style="margin-bottom: 12px"
        />
        <el-scrollbar height="360px">
          <el-checkbox-group v-model="checkedPerms">
            <div v-for="group in groupedPermissions" :key="group.module" class="perm-group">
              <div class="perm-group__title">{{ group.moduleLabel }}</div>
              <el-checkbox
                v-for="p in group.items"
                :key="p.id"
                :value="p.code"
                class="perm-checkbox"
              >{{ p.name }} <span class="perm-code">{{ p.code }}</span></el-checkbox>
            </div>
          </el-checkbox-group>
        </el-scrollbar>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="onSavePerm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listRoles,
  createRole,
  updateRole,
  deleteRole,
  getRolePermissions,
  assignRolePermissions,
  refreshPermissions
} from '@/api/role'
import { listPermissions } from '@/api/permission'

const loading = ref(false)
const roles = ref([])
const refreshing = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, code: '', name: '', description: '', builtin: false })

const rules = {
  code: [{ required: true, message: '请输入角色码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const permDialogVisible = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const currentRole = ref(null)
const allPermissions = ref([])
const checkedPerms = ref([])
const permKeyword = ref('')

const MODULE_LABELS = {
  dashboard: '仪表盘',
  issue: '问题',
  project: '项目',
  user: '用户',
  organization: '组织',
  menu: '菜单',
  role: '角色',
  settings: '设置',
  flow: '流程'
}

// 按模块分组并支持关键字过滤
const groupedPermissions = computed(() => {
  const kw = (permKeyword.value || '').trim().toLowerCase()
  const map = new Map()
  ;(allPermissions.value || []).forEach((p) => {
    if (
      kw &&
      !(p.name || '').toLowerCase().includes(kw) &&
      !(p.code || '').toLowerCase().includes(kw)
    ) {
      return
    }
    const key = p.module || 'other'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(p)
  })
  return Array.from(map.entries()).map(([module, items]) => ({
    module,
    moduleLabel: MODULE_LABELS[module] || module,
    items
  }))
})

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
        ElMessage.success('已更新')
      } else {
        await createRole(payload)
        ElMessage.success('已创建')
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
  ElMessageBox.confirm(`确认删除角色 ${row.name}？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deleteRole(row.id)
        ElMessage.success('已删除')
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
    ElMessage.success('权限缓存已刷新')
  } catch (e) {
  } finally {
    refreshing.value = false
  }
}

// ===== 权限分配 =====
function openPerm(row) {
  currentRole.value = row
  permDialogVisible.value = true
}

async function onPermDialogOpen() {
  if (!currentRole.value) return
  permLoading.value = true
  permKeyword.value = ''
  try {
    checkedPerms.value = (await getRolePermissions(currentRole.value.id)) || []
  } catch (e) {
    checkedPerms.value = []
  } finally {
    permLoading.value = false
  }
}

async function onSavePerm() {
  if (!currentRole.value) return
  permSaving.value = true
  try {
    await assignRolePermissions(currentRole.value.id, checkedPerms.value)
    ElMessage.success('权限已保存')
    permDialogVisible.value = false
    fetchRoles()
  } catch (e) {
  } finally {
    permSaving.value = false
  }
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
.perm-group {
  margin-bottom: 12px;
}
.perm-group__title {
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
}
.perm-checkbox {
  margin-right: 16px;
}
.perm-code {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>

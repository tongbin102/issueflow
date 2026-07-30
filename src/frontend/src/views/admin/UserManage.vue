<template>
  <div class="user-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>用户管理</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="username" label="账号" width="140" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">{{ roleName(row.roleId) }}</template>
        </el-table-column>
        <el-table-column label="上级领导" width="120">
          <template #default="{ row }">{{ row.leaderName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchData"
          @size-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑抽屉（R3 FormDrawer 规范） -->
    <FormDrawer
      v-model="drawerVisible"
      :title="form.id ? '编辑用户' : '新增用户'"
      size="sm"
      :loading="saving"
      @confirm="onSubmit"
      @closed="onDrawerClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <!-- R5 上级领导：复用 /api/users/options，排除自己 -->
        <el-form-item label="上级领导">
          <el-select
            v-model="form.leaderId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchLeaders"
            :loading="leaderLoading"
            placeholder="搜索并选择上级领导（可空）"
            style="width: 100%"
          >
            <el-option
              v-for="u in leaderOptions"
              :key="u.id"
              :label="u.realName || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/format'
import { pageUsers, createUser, updateUser, deleteUser, listUserOptions } from '@/api/user'
import { listRoles } from '@/api/user'
import FormDrawer from '@/components/FormDrawer.vue'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const roles = ref([])
const roleMap = ref({})
const drawerVisible = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  username: '',
  realName: '',
  email: '',
  phone: '',
  roleId: null,
  leaderId: null,
  status: 1,
  password: ''
})
const form = reactive(emptyForm())

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function roleName(roleId) {
  return roleMap.value[roleId] || '-'
}

async function loadRoles() {
  try {
    const data = await listRoles()
    roles.value = data || []
    const m = {}
    ;(data || []).forEach((r) => {
      m[r.id] = r.name
    })
    roleMap.value = m
  } catch (e) {
    roles.value = []
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await pageUsers({ page: page.value, size: size.value })
    list.value = (res && res.list) || []
    total.value = (res && res.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/* ---------------- 上级领导远程搜索（R5，排除自己） ---------------- */
const leaderOptions = ref([])
const leaderLoading = ref(false)
async function searchLeaders(query) {
  leaderLoading.value = true
  try {
    const params = {}
    if (query && query.trim()) params.keyword = query.trim()
    const data = await listUserOptions(params)
    const mapped = (data || [])
      .filter((u) => !form.id || u.id !== form.id) // 排除自己
      .map((u) => ({ id: u.id, realName: u.realName, username: u.username }))
    const merged = [...leaderOptions.value]
    mapped.forEach((m) => {
      if (!merged.find((x) => x.id === m.id)) merged.push(m)
    })
    leaderOptions.value = merged
  } catch (e) {
    leaderOptions.value = []
  } finally {
    leaderLoading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  leaderOptions.value = []
  drawerVisible.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName || '',
    email: row.email || '',
    phone: row.phone || '',
    roleId: row.roleId,
    leaderId: row.leaderId ?? null,
    status: row.status === 0 ? 0 : 1,
    password: ''
  })
  // 预填上级领导下拉，保证回显
  leaderOptions.value =
    row.leaderId != null ? [{ id: row.leaderId, realName: row.leaderName || '', username: '' }] : []
  drawerVisible.value = true
}
function onDrawerClosed() {
  formRef.value && formRef.value.resetFields()
  Object.assign(form, emptyForm())
}

function onSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id && form.leaderId === form.id) {
      ElMessage.warning('上级领导不能设置为自己')
      return
    }
    saving.value = true
    const payload = {
      username: form.username,
      realName: form.realName,
      email: form.email,
      phone: form.phone,
      roleId: form.roleId,
      leaderId: form.leaderId ?? null,
      status: form.status
    }
    if (!form.id) payload.password = form.password
    try {
      if (form.id) {
        await updateUser(form.id, payload)
        ElMessage.success('已更新')
      } else {
        await createUser(payload)
        ElMessage.success('已创建')
      }
      drawerVisible.value = false
      fetchData()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

function onDelete(row) {
  ElMessageBox.confirm(`确认删除用户 ${row.username}？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteUser(row.id)
      ElMessage.success('已删除')
      fetchData()
    })
    .catch(() => {})
}

onMounted(async () => {
  await loadRoles()
  fetchData()
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

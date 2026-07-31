<template>
  <div class="user-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('user.page.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('user.action.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="username" :label="t('user.col.username')" width="140" />
        <el-table-column prop="realName" :label="t('user.col.realName')" width="120" />
        <el-table-column prop="email" :label="t('user.col.email')" min-width="160" show-overflow-tooltip />
        <!-- Phase8 W3 #11：多角色，逐个 tag 展示（首个为主角色） -->
        <el-table-column :label="t('user.col.role')" min-width="160">
          <template #default="{ row }">
            <template v-if="rowRoleCodes(row).length">
              <el-tag
                v-for="code in rowRoleCodes(row)"
                :key="code"
                class="role-tag"
                size="small"
                effect="light"
              >
                {{ roleNameByCode(code) }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- Phase8 W2 #9：所属组织（UserVO.orgName，未归属时展示 -） -->
        <el-table-column :label="t('user.col.org')" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('user.col.leader')" width="120">
          <template #default="{ row }">{{ row.leaderName || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('user.col.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? t('common.status.enabled') : t('common.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.field.createdAt')" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.action.operation')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.action.edit') }}</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">{{ t('common.action.delete') }}</el-button>
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
      :title="form.id ? t('user.drawer.editTitle') : t('user.drawer.createTitle')"
      size="sm"
      :loading="saving"
      @confirm="onSubmit"
      @closed="onDrawerClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item :label="t('user.form.username')" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item :label="t('user.form.realName')">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item :label="t('user.col.email')">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="t('user.col.phone')">
          <el-input v-model="form.phone" />
        </el-form-item>
        <!-- Phase8 W3 #11：多角色多选，选项取 GET /api/roles 平铺的 role.code -->
        <el-form-item :label="t('user.form.roles')" prop="roles">
          <el-select
            v-model="form.roles"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :placeholder="t('user.placeholder.selectRoles')"
            style="width: 100%"
          >
            <el-option v-for="r in roles" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
          <div class="form-tip">{{ t('user.tip.primaryRole') }}</div>
        </el-form-item>
        <!-- Phase8 W2 #9：所属组织（可空，平铺下拉，来源 GET /api/organizations） -->
        <el-form-item :label="t('user.form.org')">
          <el-select
            v-model="form.orgId"
            filterable
            clearable
            :placeholder="t('user.placeholder.selectOrg')"
            style="width: 100%"
          >
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <!-- R5 上级领导：复用 /api/users/options，排除自己 -->
        <el-form-item :label="t('user.form.leader')">
          <el-select
            v-model="form.leaderId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchLeaders"
            :loading="leaderLoading"
            :placeholder="t('user.placeholder.selectLeader')"
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
        <el-form-item :label="t('user.form.status')">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
          />
        </el-form-item>
        <!-- Phase8 W2 #7：新增用户不再录入密码，服务端自动取「系统设置」的默认密码 -->
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@/utils/format'
import { pageUsers, createUser, updateUser, deleteUser, listUserOptions, listUserRoles } from '@/api/user'
import { listRoles } from '@/api/user'
import { listOrganizations } from '@/api/organization'
import FormDrawer from '@/components/FormDrawer.vue'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const roles = ref([])
/** Phase8 W3 #11：角色码 -> 角色名，用于列表多角色展示（原 roleId->名 映射随单角色列一并移除） */
const roleCodeMap = ref({})
/** Phase8 W2 #9：组织下拉（平铺列表，来源 GET /api/organizations） */
const orgOptions = ref([])
const drawerVisible = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  username: '',
  realName: '',
  email: '',
  phone: '',
  // Phase8 W3 #11：多角色，存角色码数组；主角色 roleId 提交时由首位推导
  roles: [],
  orgId: null,
  leaderId: null,
  status: 1
})
const form = reactive(emptyForm())

// Phase8 W2 #7：密码字段已从新增弹窗移除，故不再有 password 校验规则
// Phase8 W3 #11：角色校验由单选 roleId 改为多选 roles（数组非空）
const rules = computed(() => ({
  username: [{ required: true, message: t('login.msg.usernameRequired'), trigger: 'blur' }],
  roles: [
    { type: 'array', required: true, message: t('user.msg.rolesRequired'), trigger: 'change' }
  ]
}))

/** 角色码 -> 角色名（字典未加载或角色被删时退化展示角色码本身） */
function roleNameByCode(code) {
  return roleCodeMap.value[code] || code
}

/**
 * 行数据的角色码列表：优先取多角色 roles，回落单角色 roleCode，
 * 再回落 roleId 反查（兼容后端未回填 roles 的历史数据）。
 */
function rowRoleCodes(row) {
  if (Array.isArray(row.roles) && row.roles.length) return row.roles
  if (row.roleCode) return [row.roleCode]
  const hit = roles.value.find((r) => r.id === row.roleId)
  return hit ? [hit.code] : []
}

async function loadRoles() {
  try {
    const data = await listRoles()
    roles.value = data || []
    const cm = {}
    ;(data || []).forEach((r) => {
      cm[r.code] = r.name
    })
    roleCodeMap.value = cm
  } catch (e) {
    roles.value = []
  }
}

/** 由角色码推导主角色 id（首个选中角色，后端也会做同样对齐） */
function primaryRoleId(codes) {
  if (!Array.isArray(codes) || !codes.length) return null
  const hit = roles.value.find((r) => r.code === codes[0])
  return hit ? hit.id : null
}

/** 组织下拉：平铺全量启用组织，供「所属组织」选择（Phase8 W2 #9） */
async function loadOrganizations() {
  try {
    const data = await listOrganizations({ status: 1 })
    orgOptions.value = (data || []).map((o) => ({ id: o.id, name: o.name }))
  } catch (e) {
    orgOptions.value = []
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
async function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName || '',
    email: row.email || '',
    phone: row.phone || '',
    // Phase8 W3 #11：多角色回显，列表已带 roles 时直接用，避免多一次往返
    roles: rowRoleCodes(row).slice(),
    orgId: row.orgId ?? null,
    leaderId: row.leaderId ?? null,
    status: row.status === 0 ? 0 : 1
  })
  // 预填上级领导下拉，保证回显
  leaderOptions.value =
    row.leaderId != null ? [{ id: row.leaderId, realName: row.leaderName || '', username: '' }] : []
  drawerVisible.value = true
  // 列表未返回 roles（旧接口/历史数据）时兜底拉取 GET /api/users/{id}/roles
  if (!Array.isArray(row.roles) || !row.roles.length) {
    try {
      const codes = await listUserRoles(row.id)
      if (Array.isArray(codes) && codes.length && form.id === row.id) {
        form.roles = codes.slice()
      }
    } catch (e) {
      /* 回显兜底失败时保留已有推导结果，不打断编辑 */
    }
  }
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
      ElMessage.warning(t('user.msg.leaderSelf'))
      return
    }
    saving.value = true
    const payload = {
      username: form.username,
      realName: form.realName,
      email: form.email,
      phone: form.phone,
      // Phase8 W3 #11：下发全部角色码；roleId 为兼容字段，取首个角色对应的 id（主角色）
      roles: form.roles,
      roleId: primaryRoleId(form.roles),
      // Phase8 W2 #9：所属组织，未选择时下发 null 表示解除归属
      orgId: form.orgId ?? null,
      leaderId: form.leaderId ?? null,
      status: form.status
    }
    // Phase8 W2 #7：不再下发 password —— 新增时由服务端取 site.default_password
    try {
      if (form.id) {
        await updateUser(form.id, payload)
        ElMessage.success(t('user.msg.updateSuccess'))
      } else {
        await createUser(payload)
        ElMessage.success(t('user.msg.createSuccess'))
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
  ElMessageBox.confirm(t('user.msg.deleteConfirm', { name: row.username }), t('common.msg.tip'), { type: 'warning' })
    .then(async () => {
      await deleteUser(row.id)
      ElMessage.success(t('user.msg.deleteSuccess'))
      fetchData()
    })
    .catch(() => {})
}

onMounted(async () => {
  await loadRoles()
  await loadOrganizations()
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
/* Phase8 W3 #11：多角色 tag 间距与表单提示 */
.role-tag {
  margin-right: 4px;
}
.form-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>

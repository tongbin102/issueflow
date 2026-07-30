<template>
  <div class="project-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>项目管理</span>
          <div class="head-right">
            <el-popover placement="bottom-end" :width="220" trigger="click">
              <template #reference>
                <el-button text :icon="Setting">列设置</el-button>
              </template>
              <div class="col-settings">
                <div class="col-settings__actions">
                  <el-button link type="primary" size="small" @click="selectAllColumns">全选</el-button>
                  <el-button link type="primary" size="small" @click="clearAllColumns">全不选</el-button>
                  <el-button link type="primary" size="small" @click="resetColumns">重置默认</el-button>
                </div>
                <el-divider style="margin: 8px 0" />
                <div class="col-settings__list">
                  <el-checkbox
                    v-for="col in ALL_COLUMNS"
                    :key="col.key"
                    v-model="columnVisible[col.key]"
                    @change="saveColumns"
                  >{{ col.label }}</el-checkbox>
                </div>
                <div class="col-settings__tip">操作列始终显示</div>
              </div>
            </el-popover>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column v-if="columnVisible.name" prop="name" label="项目名称" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="columnVisible.description" prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="columnVisible.status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="!isAdmin"
              @change="(val) => onToggleStatus(row, val)"
            />
            <span class="status-text">{{ row.status === 1 ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible.leader" label="负责人" min-width="110">
          <template #default="{ row }">{{ row.leaderName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible.members" label="项目成员" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ formatMembers(row) }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible.createdAt" label="创建时间" width="170">
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

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑项目' : '新建项目'"
      width="460px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-model="form.leaderId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchUsers"
            :loading="userLoading"
            placeholder="搜索并选择负责人"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.realName || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目成员">
          <el-select
            v-model="form.memberIdsArray"
            multiple
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchUsers"
            :loading="userLoading"
            placeholder="搜索并选择成员"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
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
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Setting } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/format'
import {
  pageProjects,
  createProject,
  updateProject,
  deleteProject
} from '@/api/project'
import { listUserOptions } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const dialogVisible = ref(false)
const formRef = ref(null)

// 用户下拉选项（负责人 / 成员共用，远程搜索）
const userOptions = ref([])
const userLoading = ref(false)

// 列设置
const COLUMN_KEY = 'if_project_columns'
const ALL_COLUMNS = [
  { key: 'name', label: '项目名称' },
  { key: 'description', label: '描述' },
  { key: 'status', label: '状态' },
  { key: 'leader', label: '负责人' },
  { key: 'members', label: '项目成员' },
  { key: 'createdAt', label: '创建时间' }
]
const columnVisible = reactive({
  name: true,
  description: true,
  status: true,
  leader: true,
  members: true,
  createdAt: true
})

const emptyForm = () => ({
  id: null,
  name: '',
  description: '',
  status: 1,
  leaderId: null,
  memberIdsArray: []
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

/* ---------------- 列设置 ---------------- */
function loadColumns() {
  let saved = []
  try {
    const raw = localStorage.getItem(COLUMN_KEY)
    if (raw) saved = JSON.parse(raw)
  } catch (e) {
    saved = []
  }
  const keySet = new Set(ALL_COLUMNS.map((c) => c.key))
  ALL_COLUMNS.forEach((c) => {
    columnVisible[c.key] = false
  })
  if (Array.isArray(saved) && saved.length) {
    saved.forEach((k) => {
      if (keySet.has(k)) columnVisible[k] = true
    })
  } else {
    ALL_COLUMNS.forEach((c) => {
      columnVisible[c.key] = true
    })
  }
}
function saveColumns() {
  const visible = ALL_COLUMNS.filter((c) => columnVisible[c.key]).map((c) => c.key)
  try {
    localStorage.setItem(COLUMN_KEY, JSON.stringify(visible))
  } catch (e) {
    /* 忽略持久化异常 */
  }
}
function selectAllColumns() {
  ALL_COLUMNS.forEach((c) => {
    columnVisible[c.key] = true
  })
  saveColumns()
}
function clearAllColumns() {
  ALL_COLUMNS.forEach((c) => {
    columnVisible[c.key] = false
  })
  saveColumns()
}
function resetColumns() {
  ALL_COLUMNS.forEach((c) => {
    columnVisible[c.key] = true
  })
  saveColumns()
}

/* ---------------- 数据加载 ---------------- */
async function fetchData() {
  loading.value = true
  try {
    const res = await pageProjects({ page: page.value, size: size.value })
    list.value = (res && res.list) || []
    total.value = (res && res.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function parseIds(str) {
  if (!str) return []
  return String(str)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map(Number)
    .filter((n) => !Number.isNaN(n))
}

function formatMembers(row) {
  const members = row.members || []
  if (!members.length) return '-'
  const names = members.slice(0, 3).map((m) => m.realName || m.username)
  if (members.length > 3) return `${names.join('、')} 等 ${members.length} 人`
  return names.join('、')
}

/* ---------------- 用户远程搜索 ---------------- */
async function searchUsers(query) {
  userLoading.value = true
  try {
    const params = {}
    if (query && query.trim()) params.keyword = query.trim()
    const data = await listUserOptions(params)
    const mapped = (data || []).map((u) => ({
      id: u.id,
      realName: u.realName,
      username: u.username
    }))
    // 与已预填选项按 id 去重合并，保证已选 leader/members 始终可见
    const merged = [...userOptions.value]
    mapped.forEach((m) => {
      if (!merged.find((x) => x.id === m.id)) merged.push(m)
    })
    userOptions.value = merged
  } catch (e) {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

/* ---------------- 表单 ---------------- */
function openCreate() {
  Object.assign(form, emptyForm())
  userOptions.value = []
  dialogVisible.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name || '',
    description: row.description || '',
    status: row.status === 0 ? 0 : 1,
    leaderId: row.leaderId ?? null,
    memberIdsArray: parseIds(row.memberIds)
  })
  // 预填用户下拉，使已选负责人 / 成员正确回显
  const opts = []
  if (row.leaderId != null) {
    opts.push({ id: row.leaderId, realName: row.leaderName || '', username: '' })
  }
  if (Array.isArray(row.members)) {
    row.members.forEach((m) =>
      opts.push({ id: m.id, realName: m.realName || '', username: m.username || '' })
    )
  }
  const seen = new Set()
  userOptions.value = opts.filter((o) => {
    if (seen.has(o.id)) return false
    seen.add(o.id)
    return true
  })
  dialogVisible.value = true
}

/**
 * 切换项目状态（R4 高风险）：必须发送完整 payload，
 * 否则切状态会把负责人 / 成员清空。失败时回滚行状态。
 */
async function onToggleStatus(row, val) {
  const prev = val === 1 ? 0 : 1
  try {
    await updateProject(row.id, {
      name: row.name,
      description: row.description,
      status: val,
      leaderId: row.leaderId ?? null,
      memberIds: row.memberIds || ''
    })
    ElMessage.success('状态已更新')
  } catch (e) {
    row.status = prev
  }
}

function onSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    const payload = {
      name: form.name,
      description: form.description,
      status: form.status,
      leaderId: form.leaderId ?? null,
      memberIds: (form.memberIdsArray || []).join(',')
    }
    try {
      if (form.id) {
        await updateProject(form.id, payload)
        ElMessage.success('已更新')
      } else {
        await createProject(payload)
        ElMessage.success('已创建')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

function onDelete(row) {
  ElMessageBox.confirm(`确认删除项目 ${row.name}？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteProject(row.id)
      ElMessage.success('已删除')
      fetchData()
    })
    .catch(() => {})
}

onMounted(() => {
  loadColumns()
  fetchData()
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.col-settings__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.col-settings__tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.status-text {
  margin-left: 6px;
  font-size: 12px;
  color: #909399;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

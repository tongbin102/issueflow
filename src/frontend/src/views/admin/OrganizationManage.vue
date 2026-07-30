<template>
  <div class="org-manage">
    <!-- 筛选区（R4） -->
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="组织名称">
          <el-input
            v-model="filter.name"
            placeholder="按名称模糊搜索"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onResetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <div class="head-left">
            <span class="head-title">组织管理</span>
            <el-button-group>
              <el-button size="small" :icon="ArrowDown" @click="expandAll">展开全部</el-button>
              <el-button size="small" :icon="ArrowUp" @click="collapseAll">收缩全部</el-button>
            </el-button-group>
          </div>
          <div class="head-right">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
            <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
            <!-- 密度 -->
            <el-dropdown trigger="click" @command="onDensityChange">
              <el-button text :icon="Operation">密度</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="d in DENSITY_OPTIONS"
                    :key="d.value"
                    :command="d.value"
                    :class="{ 'is-active-density': density === d.value }"
                  >{{ d.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <!-- 列设置 -->
            <el-popover placement="bottom-end" :width="220" trigger="click">
              <template #reference>
                <el-button text :icon="Setting">列设置</el-button>
              </template>
              <div class="col-settings">
                <div class="col-settings__actions">
                  <el-button link type="primary" size="small" @click="selectAllColumns">全选</el-button>
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
                <div class="col-settings__tip">名称列与操作列始终显示</div>
              </div>
            </el-popover>
          </div>
        </div>
      </template>

      <!-- 树形表格（R4） -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="treeData"
        :size="density"
        border
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="true"
      >
        <el-table-column prop="name" label="组织名称" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="columnVisible.code" prop="code" label="组织编码" width="130" />
        <el-table-column v-if="columnVisible.leader" label="负责人" width="120">
          <template #default="{ row }">{{ row.leaderName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible.status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible.description" prop="description" label="描述" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible.sort" prop="sort" label="排序" width="80" align="center" />
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
    </el-card>

    <!-- 新增 / 编辑抽屉（R3 FormDrawer 规范） -->
    <FormDrawer
      v-model="drawerVisible"
      :title="form.id ? '编辑组织' : '新增组织'"
      size="sm"
      :loading="saving"
      @confirm="onSubmit"
      @closed="onDrawerClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="组织编码" prop="code">
          <el-input v-model="form.code" maxlength="50" show-word-limit placeholder="唯一编码，如 ORG001" />
        </el-form-item>
        <el-form-item label="组织名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="上级组织">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            node-key="id"
            value-key="id"
            :render-after-expand="false"
            check-strictly
            placeholder="选择上级组织（默认顶级）"
            style="width: 100%"
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
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Search,
  Refresh,
  RefreshLeft,
  Setting,
  Operation,
  ArrowDown,
  ArrowUp
} from '@element-plus/icons-vue'
import { formatDate } from '@/utils/format'
import {
  listOrganizations,
  createOrganization,
  updateOrganization,
  deleteOrganization
} from '@/api/organization'
import { listUserOptions } from '@/api/user'
import FormDrawer from '@/components/FormDrawer.vue'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const tableRef = ref(null)
const drawerVisible = ref(false)
const formRef = ref(null)

/* ---------------- 筛选（服务端） ---------------- */
const filter = reactive({ name: '', status: null })

/* ---------------- 密度（localStorage if_org_density） ---------------- */
const DENSITY_KEY = 'if_org_density'
const DENSITY_OPTIONS = [
  { value: 'large', label: '宽松' },
  { value: 'default', label: '默认' },
  { value: 'small', label: '紧凑' }
]
const density = ref('default')
function loadDensity() {
  const saved = localStorage.getItem(DENSITY_KEY)
  if (saved && DENSITY_OPTIONS.some((d) => d.value === saved)) density.value = saved
}
function onDensityChange(value) {
  density.value = value
  try {
    localStorage.setItem(DENSITY_KEY, value)
  } catch (e) {
    /* 忽略持久化异常 */
  }
}

/* ---------------- 列设置（localStorage if_org_columns） ---------------- */
const COLUMN_KEY = 'if_org_columns'
const ALL_COLUMNS = [
  { key: 'code', label: '组织编码' },
  { key: 'leader', label: '负责人' },
  { key: 'status', label: '状态' },
  { key: 'description', label: '描述' },
  { key: 'sort', label: '排序' },
  { key: 'createdAt', label: '创建时间' }
]
const columnVisible = reactive({
  code: true,
  leader: true,
  status: true,
  description: true,
  sort: false,
  createdAt: false
})
const DEFAULT_VISIBLE = ['code', 'leader', 'status', 'description']
function loadColumns() {
  let saved = []
  try {
    const raw = localStorage.getItem(COLUMN_KEY)
    if (raw) saved = JSON.parse(raw)
  } catch (e) {
    saved = []
  }
  const keySet = new Set(ALL_COLUMNS.map((c) => c.key))
  if (Array.isArray(saved) && saved.length) {
    ALL_COLUMNS.forEach((c) => {
      columnVisible[c.key] = false
    })
    saved.forEach((k) => {
      if (keySet.has(k)) columnVisible[k] = true
    })
  } else {
    ALL_COLUMNS.forEach((c) => {
      columnVisible[c.key] = DEFAULT_VISIBLE.includes(c.key)
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
function resetColumns() {
  ALL_COLUMNS.forEach((c) => {
    columnVisible[c.key] = DEFAULT_VISIBLE.includes(c.key)
  })
  saveColumns()
}

/* ---------------- 树数据 ---------------- */
const treeData = computed(() => buildTree(list.value))

function buildTree(flat) {
  const map = {}
  const roots = []
  ;(flat || []).forEach((o) => {
    map[o.id] = { ...o, children: [] }
  })
  ;(flat || []).forEach((o) => {
    if (o.parentId && o.parentId !== 0 && map[o.parentId]) {
      map[o.parentId].children.push(map[o.id])
    } else {
      roots.push(map[o.id])
    }
  })
  return roots
}

/** 编辑时禁用自身及全部子孙，防止父级成环（R4） */
const parentTreeOptions = computed(() => {
  const forbidden = new Set()
  if (form.id) {
    forbidden.add(form.id)
    collectDescendants(treeData.value, form.id, forbidden)
  }
  const mark = (nodes) =>
    (nodes || []).map((n) => ({
      id: n.id,
      name: n.name,
      disabled: forbidden.has(n.id),
      children: mark(n.children)
    }))
  return [{ id: 0, name: '顶级组织', disabled: false, children: mark(treeData.value) }]
})

function collectDescendants(nodes, targetId, out, inSubtree = false) {
  ;(nodes || []).forEach((n) => {
    const hit = inSubtree || n.id === targetId
    if (hit && n.id !== targetId) out.add(n.id)
    collectDescendants(n.children, targetId, out, hit)
  })
}

/* ---------------- 展开 / 收缩全部 ---------------- */
function toggleAll(rows, expanded) {
  ;(rows || []).forEach((row) => {
    if (row.children && row.children.length) {
      tableRef.value && tableRef.value.toggleRowExpansion(row, expanded)
      toggleAll(row.children, expanded)
    }
  })
}
function expandAll() {
  toggleAll(treeData.value, true)
}
function collapseAll() {
  toggleAll(treeData.value, false)
}

/* ---------------- 数据加载 ---------------- */
async function fetchData() {
  loading.value = true
  try {
    const params = {}
    if (filter.name && filter.name.trim()) params.name = filter.name.trim()
    if (filter.status !== null && filter.status !== undefined && filter.status !== '') {
      params.status = filter.status
    }
    const data = await listOrganizations(params)
    list.value = data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}
function onSearch() {
  fetchData()
}
function onResetFilter() {
  filter.name = ''
  filter.status = null
  fetchData()
}

/* ---------------- 负责人远程搜索 ---------------- */
const userOptions = ref([])
const userLoading = ref(false)
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
    // 与已预填选项按 id 去重合并，保证已选负责人始终可见
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
const emptyForm = () => ({
  id: null,
  code: '',
  name: '',
  parentId: 0,
  leaderId: null,
  status: 1,
  sort: 0,
  description: ''
})
const form = reactive(emptyForm())

const rules = {
  code: [{ required: true, message: '请输入组织编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, emptyForm())
  userOptions.value = []
  drawerVisible.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code || '',
    name: row.name || '',
    parentId: row.parentId || 0,
    leaderId: row.leaderId ?? null,
    status: row.status === 0 ? 0 : 1,
    sort: row.sort || 0,
    description: row.description || ''
  })
  // 预填负责人下拉，保证回显
  userOptions.value =
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
    saving.value = true
    const payload = {
      code: form.code,
      name: form.name,
      parentId: form.parentId || 0,
      leaderId: form.leaderId ?? null,
      status: form.status,
      sort: form.sort || 0,
      description: form.description
    }
    try {
      if (form.id) {
        await updateOrganization(form.id, payload)
        ElMessage.success('已更新')
      } else {
        await createOrganization(payload)
        ElMessage.success('已创建')
      }
      drawerVisible.value = false
      fetchData()
    } catch (e) {
      // 业务异常（编码重复 / 父级成环等）由响应拦截器统一提示
    } finally {
      saving.value = false
    }
  })
}

function onDelete(row) {
  ElMessageBox.confirm(`确认删除组织「${row.name}」？存在子节点时将被拒绝。`, '提示', {
    type: 'warning'
  })
    .then(async () => {
      try {
        await deleteOrganization(row.id)
        ElMessage.success('已删除')
        fetchData()
      } catch (e) {
        // 业务异常由响应拦截器统一提示
      }
    })
    .catch(() => {})
}

onMounted(() => {
  loadDensity()
  loadColumns()
  fetchData()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 12px;
}
.filter-card :deep(.el-card__body) {
  padding: 12px 16px 0;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.head-title {
  font-weight: 600;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.col-settings__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.col-settings__tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.is-active-density {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>

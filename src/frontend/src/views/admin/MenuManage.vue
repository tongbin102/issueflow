<template>
  <div class="menu-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>菜单管理</span>
          <div class="head-right">
            <el-radio-group v-model="menuType" @change="fetchData">
              <el-radio-button :value="2">后台端</el-radio-button>
              <el-radio-button :value="1">前台端</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建菜单</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="treeData"
        border
        stripe
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="path" label="路径" min-width="160" show-overflow-tooltip />
        <el-table-column prop="icon" label="图标" width="110" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="permission" label="权限标识" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑菜单' : '新建菜单'"
      width="480px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" placeholder="如 /admin/projects" maxlength="200" />
        </el-form-item>
        <el-form-item label="父级">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            value-key="id"
            :render-after-expand="false"
            check-strictly
            placeholder="选择父级（默认顶级）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="端">
          <el-radio-group v-model="form.type">
            <el-radio :value="2">后台端</el-radio>
            <el-radio :value="1">前台端</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.permission" placeholder="如 system:menu:list" maxlength="100" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名" maxlength="50" />
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
import { Plus } from '@element-plus/icons-vue'
import { listMenus, createMenu, updateMenu, deleteMenu } from '@/api/menu'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
/** 当前管理的端维度：2=后台端 / 1=前台端 */
const menuType = ref(2)

const treeData = computed(() => buildTree(list.value))
const parentTreeOptions = computed(() => [{ id: 0, name: '顶级菜单', children: treeData.value }])

const emptyForm = () => ({
  id: null,
  name: '',
  path: '',
  parentId: 0,
  sort: 0,
  permission: '',
  icon: '',
  type: 2
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }]
}

function buildTree(flat) {
  const map = {}
  const roots = []
  ;(flat || []).forEach((o) => {
    map[o.id] = {
      id: o.id,
      name: o.name,
      path: o.path,
      icon: o.icon,
      sort: o.sort,
      permission: o.permission,
      parentId: o.parentId,
      children: []
    }
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

async function fetchData() {
  loading.value = true
  try {
    const data = await listMenus(menuType.value)
    list.value = data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name || '',
    path: row.path || '',
    parentId: row.parentId || 0,
    sort: row.sort || 0,
    permission: row.permission || '',
    icon: row.icon || '',
    type: row.type || 2
  })
  dialogVisible.value = true
}

function onSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    const payload = {
      name: form.name,
      path: form.path,
      parentId: form.parentId || 0,
      sort: form.sort || 0,
      permission: form.permission,
      icon: form.icon,
      type: form.type || 2
    }
    try {
      if (form.id) {
        await updateMenu(form.id, payload)
        ElMessage.success('已更新')
      } else {
        await createMenu(payload)
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
  ElMessageBox.confirm(`确认删除菜单 ${row.name}？存在子节点时将被拒绝。`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deleteMenu(row.id)
        ElMessage.success('已删除')
        fetchData()
      } catch (e) {
        // 业务异常（如存在子节点）由响应拦截器统一提示
      }
    })
    .catch(() => {})
}

onMounted(fetchData)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>

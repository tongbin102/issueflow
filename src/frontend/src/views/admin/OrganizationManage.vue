<template>
  <div class="org-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>组织管理</span>
          <div>
            <el-button type="primary" :icon="Plus" @click="openCreateRoot">新建根组织</el-button>
            <el-button :icon="Plus" :disabled="!currentId" @click="openCreateChild">新建子组织</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="10">
          <el-tree
            ref="treeRef"
            class="org-tree"
            :data="treeData"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            highlight-current
            default-expand-all
            @node-click="onNodeClick"
          />
        </el-col>
        <el-col :span="14">
          <div v-if="currentId" class="sel-info">
            <div>已选择：<b>{{ currentName }}</b></div>
            <div style="margin-top: 12px">
              <el-button :icon="Edit" @click="openEdit">编辑</el-button>
              <el-button type="danger" :icon="Delete" @click="onDelete(currentId)">删除</el-button>
            </div>
          </div>
          <el-empty v-else description="请选择左侧组织节点" />
        </el-col>
      </el-row>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑组织' : '新建组织'"
      width="460px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit />
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
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
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
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  listOrganizations,
  createOrganization,
  updateOrganization,
  deleteOrganization
} from '@/api/organization'

const loading = ref(false)
const list = ref([])
const treeRef = ref(null)
const currentId = ref(null)
const currentName = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const treeData = computed(() => buildTree(list.value))
const parentTreeOptions = computed(() => [{ id: 0, name: '顶级组织', children: treeData.value }])

const emptyForm = () => ({ id: null, name: '', parentId: 0, sort: 0 })
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }]
}

function buildTree(flat) {
  const map = {}
  const roots = []
  ;(flat || []).forEach((o) => {
    map[o.id] = {
      id: o.id,
      name: o.name,
      sort: o.sort,
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

function findNode(arr, id) {
  for (const n of arr || []) {
    if (n.id === id) return n
    if (n.children && n.children.length) {
      const f = findNode(n.children, id)
      if (f) return f
    }
  }
  return null
}

async function fetchData() {
  loading.value = true
  try {
    const data = await listOrganizations()
    list.value = data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onNodeClick(node) {
  currentId.value = node.id
  currentName.value = node.name
}

function openCreateRoot() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}
function openCreateChild() {
  Object.assign(form, emptyForm())
  form.parentId = currentId.value || 0
  dialogVisible.value = true
}
function openEdit() {
  if (!currentId.value) return
  const node = findNode(list.value, currentId.value)
  if (!node) return
  Object.assign(form, {
    id: node.id,
    name: node.name || '',
    parentId: node.parentId || 0,
    sort: node.sort || 0
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
      parentId: form.parentId || 0,
      sort: form.sort || 0
    }
    try {
      if (form.id) {
        await updateOrganization(form.id, payload)
        ElMessage.success('已更新')
      } else {
        await createOrganization(payload)
        ElMessage.success('已创建')
      }
      dialogVisible.value = false
      currentId.value = null
      fetchData()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

function onDelete(id) {
  ElMessageBox.confirm('确认删除该组织？存在子节点时将被拒绝。', '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deleteOrganization(id)
        ElMessage.success('已删除')
        currentId.value = null
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
.org-tree {
  min-height: 200px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}
.sel-info {
  padding: 8px 4px;
  color: #606266;
}
</style>

<template>
  <div class="project-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>项目管理</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="项目名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
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
import { Plus } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/format'
import {
  pageProjects,
  createProject,
  updateProject,
  deleteProject
} from '@/api/project'
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

const emptyForm = () => ({
  id: null,
  name: '',
  description: '',
  status: 1
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

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

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name || '',
    description: row.description || '',
    status: row.status === 0 ? 0 : 1
  })
  dialogVisible.value = true
}

async function onToggleStatus(row, val) {
  try {
    await updateProject(row.id, { name: row.name, status: val })
    ElMessage.success('状态已更新')
  } catch (e) {
    row.status = val === 1 ? 0 : 1
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
      status: form.status
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

onMounted(fetchData)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

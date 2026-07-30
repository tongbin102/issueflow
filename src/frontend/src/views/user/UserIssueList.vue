<template>
  <div class="user-issue-list">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>我的问题</span>
          <el-button type="primary" :icon="Plus" @click="goCreate">提交新问题</el-button>
        </div>
      </template>

      <IssueTable
        ref="tableRef"
        scope="mine"
        @view="openDetail"
        @edit="openEdit"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <IssueDetailDrawer
      v-model="drawerVisible"
      :issue-id="currentId"
      :flow-config="flowConfig"
      @updated="refresh"
    />

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" title="编辑问题" width="680px" append-to-body>
      <IssueForm :initial="editRow" @submit="onEditSubmit" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import IssueTable from '@/components/IssueTable.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import IssueForm from '@/components/IssueForm.vue'
import { updateIssue } from '@/api/issue'

const router = useRouter()
const tableRef = ref(null)
const drawerVisible = ref(false)
const currentId = ref(null)
const editVisible = ref(false)
const editRow = ref(null)
const flowConfig = ref({ rejectEnabled: true, reopenEnabled: true })

function openDetail(row) {
  currentId.value = row.id
  drawerVisible.value = true
}
function openEdit(row) {
  editRow.value = row
  editVisible.value = true
}
function refresh() {
  if (tableRef.value) tableRef.value.fetchData()
}
function goCreate() {
  router.push('/user/submit-issue')
}
async function onEditSubmit({ data }) {
  if (!editRow.value) return
  try {
    await updateIssue(editRow.value.id, data)
    ElMessage.success('保存成功')
    editVisible.value = false
    refresh()
  } catch (e) {}
}
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

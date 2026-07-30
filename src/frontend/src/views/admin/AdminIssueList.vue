<template>
  <div class="admin-issue-list">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>问题管理</span>
        </div>
      </template>

      <IssueTable
        ref="tableRef"
        scope="all"
        @view="openDetail"
        @edit="openEdit"
      />
    </el-card>

    <IssueDetailDrawer
      v-model="drawerVisible"
      :issue-id="currentId"
      :flow-config="flowConfig"
      @updated="refresh"
    />

    <el-dialog v-model="editVisible" title="编辑问题" width="680px" append-to-body>
      <IssueForm :initial="editRow" @submit="onEditSubmit" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import IssueTable from '@/components/IssueTable.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import IssueForm from '@/components/IssueForm.vue'
import { updateIssue } from '@/api/issue'

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

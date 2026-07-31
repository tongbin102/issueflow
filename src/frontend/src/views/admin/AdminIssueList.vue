<template>
  <!-- Q5 决策：本页 0 功能改动，仅 T7 弹窗→抽屉 与 T8 i18n 替换 -->
  <div class="admin-issue-list">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <!-- Phase7 T4：本页已归属「业务管理」目录下的「问题列表」子菜单，页头随菜单改名对齐 -->
          <span>{{ t('menu.admin.issueList') }}</span>
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

    <!-- 编辑抽屉（T7：el-dialog → FormDrawer，支持全屏图标切换） -->
    <FormDrawer
      v-model="editVisible"
      :title="t('issue.drawer.editTitle')"
      size="lg"
      fullscreenable
      :loading="editLoading"
      @confirm="onEditConfirm"
      @closed="editRow = null"
    >
      <IssueForm
        v-if="editVisible && editRow"
        ref="editFormRef"
        :initial="editRow"
        @submit="onEditSubmit"
      />
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import IssueTable from '@/components/IssueTable.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import IssueForm from '@/components/IssueForm.vue'
import FormDrawer from '@/components/FormDrawer.vue'
import { updateIssue } from '@/api/issue'

const { t } = useI18n()
const tableRef = ref(null)
const drawerVisible = ref(false)
const currentId = ref(null)
const editVisible = ref(false)
const editLoading = ref(false)
const editRow = ref(null)
const editFormRef = ref(null)
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
function onEditConfirm() {
  if (editFormRef.value) editFormRef.value.submit()
}
async function onEditSubmit({ data }) {
  if (!editRow.value) return
  editLoading.value = true
  try {
    await updateIssue(editRow.value.id, data)
    ElMessage.success(t('issue.msg.updateSuccess'))
    editVisible.value = false
    refresh()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    editLoading.value = false
  }
}
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

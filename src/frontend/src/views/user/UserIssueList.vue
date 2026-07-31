<template>
  <div class="user-issue-list">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('issue.list.myTitle') }}</span>
          <!-- Phase6：提交入口收敛为本页抽屉（原 /user/submit-issue 已 redirect） -->
          <el-button type="primary" :icon="Plus" @click="openCreate">{{
            t('issue.action.submitNew')
          }}</el-button>
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

    <!-- 新建抽屉（T4/T7：FormDrawer + 全屏图标按钮 + 4 分区折叠表单） -->
    <FormDrawer
      v-model="createVisible"
      :title="t('issue.drawer.createTitle')"
      size="lg"
      fullscreenable
      :loading="createLoading"
      :confirm-text="t('common.action.submit')"
      @confirm="onCreateConfirm"
      @closed="onCreateClosed"
    >
      <IssueForm v-if="createMounted" ref="createFormRef" mode="submit" @submit="onCreateSubmit" />
    </FormDrawer>

    <!-- 编辑抽屉（T7：el-dialog → FormDrawer） -->
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
        mode="edit"
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
import { Plus } from '@element-plus/icons-vue'
import IssueTable from '@/components/IssueTable.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import IssueForm from '@/components/IssueForm.vue'
import FormDrawer from '@/components/FormDrawer.vue'
import { createIssue, updateIssue } from '@/api/issue'

const { t } = useI18n()
const tableRef = ref(null)
const drawerVisible = ref(false)
const currentId = ref(null)

const createVisible = ref(false)
const createMounted = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)

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

/* ---------------- 新建 ---------------- */
function openCreate() {
  createMounted.value = true
  createVisible.value = true
}
function onCreateConfirm() {
  if (createFormRef.value) createFormRef.value.submit()
}
function onCreateClosed() {
  // 关闭后销毁重建表单，保证下次打开为全新空表单
  createMounted.value = false
}
async function onCreateSubmit({ data, files }) {
  createLoading.value = true
  try {
    const fd = new FormData()
    // #3.4：后端 IssueController.create 要求 multipart 含名为 issue 的 JSON part
    // （@RequestPart("issue") @Valid IssueCreateReq）。原「扁平字段逐个 append」缺少该
    // part，会导致后端校验失败并由拦截器统一弹「系统错误」，此处与 IssueCreate.vue 对齐修复。
    fd.append('issue', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    ;(files || []).forEach((f) => fd.append('files', f))
    const res = await createIssue(fd)
    ElMessage.success(`${t('issue.msg.createSuccess')} ${res && res.issueNo ? res.issueNo : ''}`)
    createVisible.value = false
    refresh()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    createLoading.value = false
  }
}

/* ---------------- 编辑 ---------------- */
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

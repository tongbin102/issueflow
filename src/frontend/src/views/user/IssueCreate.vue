<template>
  <div class="issue-create">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('issue.action.new') }}</span>
          <el-button text @click="goList">{{ t('issue.action.backToList') }}</el-button>
        </div>
      </template>

      <el-alert
        v-if="createdNo"
        type="success"
        show-icon
        :closable="false"
        :title="t('issue.msg.createSuccessWithNo', { no: createdNo })"
        style="margin-bottom: 16px"
      />

      <IssueForm mode="submit" @submit="onSubmit" />
    </el-card>

    <!-- 创建成功后打开详情抽屉 -->
    <IssueDetailDrawer
      v-model="drawerVisible"
      :issue-id="createdId"
      :flow-config="flowConfig"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import IssueForm from '@/components/IssueForm.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { createIssue } from '@/api/issue'

const { t } = useI18n()
const router = useRouter()
const drawerVisible = ref(false)
const createdId = ref(null)
const createdNo = ref('')
const flowConfig = ref({ rejectEnabled: true, reopenEnabled: true })

function goList() {
  router.push('/user/my-issues')
}

async function onSubmit({ data, files }) {
  const fd = new FormData()
  // 后端 @RequestPart("issue") 要求整个 IssueCreateReq JSON 作为一个 part
  fd.append(
    'issue',
    new Blob([JSON.stringify(data)], { type: 'application/json' })
  )
  ;(files || []).forEach((f) => fd.append('files', f))
  try {
    const res = await createIssue(fd)
    createdNo.value = res.issueNo || ''
    createdId.value = res.id || null
    ElMessage.success(t('issue.msg.createSuccessWithNo', { no: createdNo.value }))
    drawerVisible.value = true
  } catch (e) {
    console.error('[IssueCreate] createIssue failed:', e)
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

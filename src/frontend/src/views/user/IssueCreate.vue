<template>
  <div class="issue-create">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>提交问题</span>
          <el-button text @click="goList">返回列表</el-button>
        </div>
      </template>

      <el-alert
        v-if="createdNo"
        type="success"
        show-icon
        :closable="false"
        :title="`提交成功，问题编号：${createdNo}`"
        style="margin-bottom: 16px"
      />

      <IssueForm @submit="onSubmit" />
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
import { ElMessage } from 'element-plus'
import IssueForm from '@/components/IssueForm.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { createIssue } from '@/api/issue'

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
  Object.keys(data).forEach((k) => {
    if (data[k] !== null && data[k] !== undefined) fd.append(k, data[k])
  })
  ;(files || []).forEach((f) => fd.append('files', f))
  try {
    const res = await createIssue(fd)
    createdNo.value = res.issueNo || ''
    createdId.value = res.id || null
    ElMessage.success(`提交成功，编号 ${createdNo.value}`)
    drawerVisible.value = true
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

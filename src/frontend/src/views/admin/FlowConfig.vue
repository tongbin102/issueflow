<template>
  <div class="flow-config">
    <el-card class="page-card" shadow="never">
      <template #header><span>流程配置</span></template>
      <el-form label-width="160px" label-position="right">
        <el-form-item label="允许回退（待验证→处理中）">
          <el-switch v-model="rejectEnabled" @change="save" />
        </el-form-item>
        <el-form-item label="允许重开（已关闭→待处理）">
          <el-switch v-model="reopenEnabled" @change="save" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        回退仅测试/管理员可触发且需填写原因；重开仅管理员可触发。
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFlowConfig, setFlowConfig } from '@/api/sysConfig'

const rejectEnabled = ref(true)
const reopenEnabled = ref(true)
const saving = ref(false)

async function load() {
  try {
    const data = await getFlowConfig()
    if (data) {
      rejectEnabled.value =
        data.rejectEnabled !== undefined ? !!data.rejectEnabled : true
      reopenEnabled.value =
        data.reopenEnabled !== undefined ? !!data.reopenEnabled : true
    }
  } catch (e) {}
}

function save() {
  if (saving.value) return
  saving.value = true
  setFlowConfig({
    rejectEnabled: rejectEnabled.value,
    reopenEnabled: reopenEnabled.value
  })
    .then(() => ElMessage.success('已保存'))
    .catch(() => {})
    .finally(() => {
      saving.value = false
    })
}

onMounted(load)
</script>

<style scoped>
.flow-config {
  max-width: 720px;
}
</style>

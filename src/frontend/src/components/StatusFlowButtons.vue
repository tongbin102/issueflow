<template>
  <div class="status-flow-buttons">
    <el-button
      v-for="btn in buttons"
      :key="btn.key"
      :type="btn.type"
      :plain="btn.plain"
      @click="onClick(btn)"
    >
      {{ btn.label }}
    </el-button>
    <el-empty
      v-if="!buttons.length"
      description="当前状态无可执行流转"
      :image-size="40"
    />

    <!-- 备注弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="`${activeBtn ? activeBtn.label : ''} - 填写备注`"
      width="420px"
      append-to-body
    >
      <el-input
        v-model="remark"
        type="textarea"
        :rows="3"
        :placeholder="remarkRequired ? '请填写原因（必填）' : '可选备注'"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirm"
          >确定</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { changeStatus, reopenIssue } from '@/api/issue'

const props = defineProps({
  // 当前状态数值
  status: { type: [Number, String], default: 0 },
  issueId: { type: [Number, String], default: null },
  // 流程开关 {rejectEnabled, reopenEnabled}
  flowConfig: { type: Object, default: () => ({ rejectEnabled: true, reopenEnabled: true }) }
})
const emit = defineEmits(['changed'])

const userStore = useUserStore()
const dialogVisible = ref(false)
const activeBtn = ref(null)
const remark = ref('')
const submitting = ref(false)

const roles = computed(() => userStore.roles || [])
const isDev = computed(() => roles.value.includes('DEVELOPER'))
const isTester = computed(() => roles.value.includes('TESTER'))
const isAdmin = computed(() => roles.value.includes('ADMIN'))

// 依据 当前状态 + 角色 + 流程开关 计算可用按钮
const buttons = computed(() => {
  const s = Number(props.status)
  const list = []
  if (s === 0) {
    // OPEN → IN_PROGRESS（D/A）
    if (isDev.value || isAdmin.value) {
      list.push({
        key: 'claim',
        label: '认领 / 处理中',
        type: 'primary',
        toStatus: 1,
        requireRemark: false,
        openId: false
      })
    }
  } else if (s === 1) {
    // IN_PROGRESS → PENDING_VERIFY（D/A）
    if (isDev.value || isAdmin.value) {
      list.push({
        key: 'submitFix',
        label: '提交修复',
        type: 'primary',
        toStatus: 2,
        requireRemark: false,
        openId: false
      })
    }
  } else if (s === 2) {
    // PENDING_VERIFY → VERIFIED（T/A）
    if (isTester.value || isAdmin.value) {
      list.push({
        key: 'verifyPass',
        label: '验证通过',
        type: 'success',
        toStatus: 3,
        requireRemark: false,
        openId: false
      })
    }
    // PENDING_VERIFY → IN_PROGRESS 回退（T/A 且 rejectEnabled）
    if ((isTester.value || isAdmin.value) && props.flowConfig.rejectEnabled) {
      list.push({
        key: 'reject',
        label: '回退',
        type: 'warning',
        plain: true,
        toStatus: 1,
        requireRemark: true,
        openId: false
      })
    }
  } else if (s === 3) {
    // VERIFIED → CLOSED（T/A）
    if (isTester.value || isAdmin.value) {
      list.push({
        key: 'close',
        label: '关闭',
        type: 'info',
        toStatus: 4,
        requireRemark: true,
        openId: false
      })
    }
  } else if (s === 4) {
    // CLOSED → OPEN 重开（A 且 reopenEnabled）
    if (isAdmin.value && props.flowConfig.reopenEnabled) {
      list.push({
        key: 'reopen',
        label: '重开',
        type: 'warning',
        toStatus: 0,
        requireRemark: false,
        openId: true
      })
    }
  }
  return list
})

function onClick(btn) {
  activeBtn.value = btn
  remark.value = ''
  if (btn.requireRemark) {
    dialogVisible.value = true
  } else {
    confirm()
  }
}

function confirm() {
  const btn = activeBtn.value
  if (!btn) return
  if (btn.requireRemark && !remark.value.trim()) {
    ElMessage.warning('请填写原因')
    return
  }
  submitting.value = true
  const run = btn.openId
    ? reopenIssue(props.issueId, remark.value)
    : changeStatus(props.issueId, { toStatus: btn.toStatus, remark: remark.value })
  run
    .then(() => {
      ElMessage.success('操作成功')
      dialogVisible.value = false
      emit('changed')
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false
    })
}
</script>

<style scoped>
.status-flow-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>

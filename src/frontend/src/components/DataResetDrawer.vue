<template>
  <!-- R7 数据初始化抽屉：强警告 + 清单 + RESET 确认输入 -->
  <FormDrawer
    v-model="visible"
    title="数据初始化"
    size="md"
    @closed="onClosed"
  >
    <el-alert
      type="error"
      :closable="false"
      show-icon
      title="高危操作：数据初始化不可撤销！"
      description="执行后以下业务数据将被永久清除且无法恢复，请务必确认已做好备份。"
      class="reset-alert"
    />

    <div class="reset-lists">
      <div class="reset-col reset-col--danger">
        <div class="reset-col__title">将被清除</div>
        <ul>
          <li v-for="item in CLEAR_ITEMS" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div class="reset-col reset-col--safe">
        <div class="reset-col__title">将被保留</div>
        <ul>
          <li v-for="item in KEEP_ITEMS" :key="item">{{ item }}</li>
        </ul>
      </div>
    </div>

    <div class="reset-confirm">
      <p class="reset-confirm__tip">
        请输入 <b>RESET</b> 以确认执行：
      </p>
      <el-input
        v-model="confirmInput"
        placeholder="请输入 RESET"
        maxlength="20"
        @keyup.enter="onConfirm"
      />
    </div>

    <template #footer>
      <div class="reset-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="danger"
          :disabled="confirmInput !== 'RESET'"
          :loading="submitting"
          @click="onConfirm"
        >确认清除</el-button>
      </div>
    </template>
  </FormDrawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import FormDrawer from '@/components/FormDrawer.vue'
import { resetSystemData } from '@/api/system'

const props = defineProps({
  /** v-model 显隐 */
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

/** 将被清除的数据清单 */
const CLEAR_ITEMS = [
  '问题',
  '问题历史',
  '附件',
  '问题关联',
  '标签关联',
  '项目',
  '模块',
  '模块依赖',
  '组织',
  '除 admin 外的用户'
]
/** 将被保留的数据清单 */
const KEEP_ITEMS = ['角色', '权限', '菜单', '系统配置', '流程定义', 'admin 账号']

const confirmInput = ref('')
const submitting = ref(false)

function onClosed() {
  confirmInput.value = ''
}

async function onConfirm() {
  // 双保险：输入不等于 RESET 时不提交（按钮本身已禁用）
  if (confirmInput.value !== 'RESET' || submitting.value) return
  submitting.value = true
  try {
    // 后端返回各表清理条数 Map<表名, 条数>
    const counts = await resetSystemData(confirmInput.value)
    ElMessage.success('数据初始化完成')
    emit('success', counts || {})
    visible.value = false
  } catch (e) {
    // 业务异常由响应拦截器统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.reset-alert {
  margin-bottom: 16px;
}
.reset-lists {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.reset-col {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
}
.reset-col__title {
  font-weight: 600;
  margin-bottom: 8px;
}
.reset-col--danger .reset-col__title {
  color: var(--el-color-danger);
}
.reset-col--safe .reset-col__title {
  color: var(--el-color-success);
}
.reset-col ul {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.9;
}
.reset-confirm__tip {
  margin: 0 0 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.reset-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

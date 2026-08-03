<template>
  <!-- R7 数据初始化抽屉：强警告 + 清单 + RESET 确认输入 -->
  <FormDrawer
    v-model="visible"
    :title="t('system.reset.title')"
    size="md"
    @closed="onClosed"
  >
    <el-alert
      type="error"
      :closable="false"
      show-icon
      :title="t('system.reset.alertTitle')"
      :description="t('system.reset.alertDesc')"
      class="reset-alert"
    />

    <div class="reset-lists">
      <div class="reset-col reset-col--danger">
        <div class="reset-col__title">{{ t('system.reset.clearTitle') }}</div>
        <ul>
          <li v-for="key in CLEAR_ITEM_KEYS" :key="key">
            {{ t(`system.reset.clearItems.${key}`) }}
          </li>
        </ul>
      </div>
      <div class="reset-col reset-col--safe">
        <div class="reset-col__title">{{ t('system.reset.keepTitle') }}</div>
        <ul>
          <li v-for="key in KEEP_ITEM_KEYS" :key="key">
            {{ t(`system.reset.keepItems.${key}`) }}
          </li>
        </ul>
      </div>
    </div>

    <div class="reset-confirm">
      <!-- 关键词用 <b> 强调，故拆成 i18n 插值 + 具名插槽两段渲染 -->
      <p class="reset-confirm__tip">
        <i18n-t keypath="system.reset.confirmTip" tag="span" scope="global">
          <template #keyword><b>{{ CONFIRM_KEYWORD }}</b></template>
        </i18n-t>
      </p>
      <el-input
        v-model="confirmInput"
        :placeholder="t('system.reset.confirmPlaceholder', { keyword: CONFIRM_KEYWORD })"
        maxlength="20"
        @keyup.enter="onConfirm"
      />
    </div>

    <template #footer>
      <div class="reset-footer">
        <el-button @click="visible = false">{{ t('common.action.cancel') }}</el-button>
        <el-button
          type="danger"
          :disabled="confirmInput !== CONFIRM_KEYWORD"
          :loading="submitting"
          @click="onConfirm"
        >{{ t('system.reset.confirmButton') }}</el-button>
      </div>
    </template>
  </FormDrawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import FormDrawer from '@/components/FormDrawer.vue'
import { resetSystemData } from '@/api/system'

const { t } = useI18n()

const props = defineProps({
  /** v-model 显隐 */
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

/**
 * 二次确认关键词。后端 resetSystemData 也校验同一字符串，
 * 因此这里是常量而非可翻译文案 —— 翻译它会直接导致英文环境提交被后端拒绝。
 */
const CONFIRM_KEYWORD = 'RESET'

/** 将被清除的数据清单（i18n key，文案见 system.reset.clearItems） */
const CLEAR_ITEM_KEYS = [
  'issue',
  'issueHistory',
  'attachment',
  'issueRelation',
  'tagRelation',
  'project',
  'module',
  'moduleDependency',
  'organization',
  'user'
]
/** 将被保留的数据清单（i18n key，文案见 system.reset.keepItems） */
const KEEP_ITEM_KEYS = ['role', 'permission', 'menu', 'config', 'flow', 'admin']

const confirmInput = ref('')
const submitting = ref(false)

/** 抽屉关闭后清空确认输入，避免下次打开直接可提交 */
function onClosed() {
  confirmInput.value = ''
}

/**
 * 提交数据初始化。
 *
 * 成功后向父组件抛出 success 事件，携带后端返回的各表清理条数。
 */
async function onConfirm() {
  // 双保险：输入不等于关键词时不提交（按钮本身已禁用）
  if (confirmInput.value !== CONFIRM_KEYWORD || submitting.value) return
  submitting.value = true
  try {
    // 后端返回各表清理条数 Map<表名, 条数>
    const counts = await resetSystemData(confirmInput.value)
    ElMessage.success(t('system.reset.success'))
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

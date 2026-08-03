<template>
  <!--
    备份保留策略对话框（Phase10 需求三）。

    三个参数直接决定磁盘会不会被备份文件撑爆，因此都设了下限：
    份数 / 天数最小为 1（填 0 等于「立刻清掉刚做的备份」，属于自伤操作），
    上传上限最小 1MB。上限值与后端 DataManagementConfigDTO 的 @Max 校验保持一致，
    避免前端放行、后端 400 的割裂体验。
  -->
  <el-dialog
    v-model="visible"
    :title="t('dataManagement.config.title')"
    width="480px"
    :close-on-click-modal="false"
  >
    <el-form label-width="150px" @submit.prevent>
      <el-form-item :label="t('dataManagement.config.maxCopies')">
        <el-input-number v-model="form.maxCopies" :min="1" :max="1000" :step="1" />
        <div class="dm-form-tip">{{ t('dataManagement.config.maxCopiesTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('dataManagement.config.defaultDays')">
        <el-input-number v-model="form.defaultDays" :min="1" :max="3650" :step="1" />
        <div class="dm-form-tip">{{ t('dataManagement.config.defaultDaysTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('dataManagement.config.sizeLimitMB')">
        <el-input-number v-model="form.sizeLimitMB" :min="1" :max="10240" :step="16" />
        <div class="dm-form-tip">{{ t('dataManagement.config.sizeLimitTip') }}</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ t('dataManagement.action.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ t('dataManagement.action.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  /** 弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 当前配置（DataManagementConfigDTO） */
  config: { type: Object, default: null },
  /** 保存中 */
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const { t } = useI18n()

/** 表单默认值与后端 DTO 默认值一致 */
const form = ref({
  maxCopies: 20,
  defaultDays: 30,
  sizeLimitMB: 512
})

/** 双向绑定的显隐 */
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/**
 * 每次打开时用最新配置回填。
 * 用 watch 而不是 props 直接绑定，避免用户改了输入框却没保存时，
 * 父组件的 config 对象被就地改脏。
 */
watch(
  () => [props.modelValue, props.config],
  ([open]) => {
    if (!open) return
    const source = props.config || {}
    form.value = {
      maxCopies: Number(source.maxCopies) > 0 ? Number(source.maxCopies) : 20,
      defaultDays: Number(source.defaultDays) > 0 ? Number(source.defaultDays) : 30,
      sizeLimitMB: Number(source.sizeLimitMB) > 0 ? Number(source.sizeLimitMB) : 512
    }
  },
  { immediate: true, deep: true }
)

/** 提交配置 */
function handleSubmit() {
  emit('submit', {
    maxCopies: form.value.maxCopies,
    defaultDays: form.value.defaultDays,
    sizeLimitMB: form.value.sizeLimitMB
  })
}
</script>

<style scoped>
.dm-form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>

<template>
  <!--
    新建备份对话框（Phase10 需求三）。

    备份类型与 BackupTypeEnum 对齐：FULL / DB_ONLY / CONFIG_ONLY。
    「包含配置文件」仅对 FULL 有意义 —— DB_ONLY 天然不含配置，
    CONFIG_ONLY 天然只有配置，两者都把开关禁用掉，避免用户提交自相矛盾的组合。
  -->
  <el-dialog
    v-model="visible"
    :title="t('dataManagement.create.title')"
    width="520px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" label-width="120px" @submit.prevent>
      <el-form-item :label="t('dataManagement.create.name')">
        <el-input
          v-model="form.name"
          maxlength="64"
          show-word-limit
          clearable
          :placeholder="t('dataManagement.create.namePlaceholder')"
        />
      </el-form-item>

      <el-form-item :label="t('dataManagement.create.type')">
        <el-radio-group v-model="form.type">
          <el-radio v-for="item in BACKUP_TYPES" :key="item" :value="item">
            {{ t(`dataManagement.type.${item}`) }}
          </el-radio>
        </el-radio-group>
        <div class="dm-form-tip">{{ t('dataManagement.create.typeTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('dataManagement.create.includeConfig')">
        <el-switch v-model="form.includeConfig" :disabled="includeConfigDisabled" />
        <div class="dm-form-tip">{{ t('dataManagement.create.includeConfigTip') }}</div>
      </el-form-item>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('dataManagement.create.hint')"
      />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ t('dataManagement.action.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ t('dataManagement.action.create') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BACKUP_TYPES } from '@/api/dataManagement'

const props = defineProps({
  /** 弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 提交中（由父组件控制，父组件负责调接口） */
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const { t } = useI18n()

const formRef = ref(null)

/** 表单数据；type 默认 FULL 与后端 CreateBackupReq 默认值一致 */
const form = ref({
  name: '',
  type: 'FULL',
  includeConfig: true
})

/** 双向绑定的显隐 */
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/** 仅 FULL 类型允许自行决定是否带配置 */
const includeConfigDisabled = computed(() => form.value.type !== 'FULL')

/**
 * 类型切换时同步 includeConfig，保证提交给后端的组合永远自洽：
 *   DB_ONLY     → false
 *   CONFIG_ONLY → true
 */
watch(
  () => form.value.type,
  (type) => {
    if (type === 'DB_ONLY') {
      form.value.includeConfig = false
    } else if (type === 'CONFIG_ONLY') {
      form.value.includeConfig = true
    }
  }
)

/** 关闭动画结束后重置表单，下次打开是干净状态 */
function handleClosed() {
  form.value = { name: '', type: 'FULL', includeConfig: true }
}

/** 提交给父组件，由父组件负责调用接口与错误处理 */
function handleSubmit() {
  emit('submit', {
    name: form.value.name.trim(),
    type: form.value.type,
    includeConfig: form.value.includeConfig
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

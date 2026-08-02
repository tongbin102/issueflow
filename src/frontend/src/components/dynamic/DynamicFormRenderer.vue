<template>
  <!-- 整表单渲染器：由 FieldSchemaVO 驱动，零硬编码字段名。
       T05 的 IssueForm.vue 直接消费本组件，对外契约（props/emits/expose）不得更改。 -->
  <el-form
    ref="formRef"
    :model="formModel"
    :rules="formRules"
    :disabled="disabled"
    :label-width="labelWidth"
    class="dynamic-form"
    @submit.prevent
  >
    <template v-for="section in visibleSections" :key="section.code">
      <div class="dynamic-form__section">
        <div v-if="showSectionTitle" class="dynamic-form__section-title">
          {{ sectionLabel(section) }}
        </div>
        <el-row :gutter="16">
          <el-col
            v-for="field in fieldsOf(section)"
            :key="field.code"
            :span="spanOf(field)"
            :xs="24"
          >
            <el-form-item :prop="field.code" :label="fieldLabel(field)">
              <DynamicField
                :field="field"
                :model-value="formModel[field.code]"
                :disabled="disabled"
                :parent-value="parentValueOf(field)"
                @update:model-value="(value) => onFieldChange(field.code, value)"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </template>

    <el-empty
      v-if="visibleSections.length === 0"
      :description="t('fieldConfig.tip.previewEmpty')"
    />
  </el-form>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import DynamicField from './DynamicField.vue'
import { buildRules } from '@/utils/fieldControls'

const props = defineProps({
  /** 渲染契约 FieldSchemaVO（GET /api/field-configs/schema 的 data） */
  schema: { type: Object, required: true },
  /** 扁平 kv 值模型：{ [field.code]: value } */
  modelValue: { type: Object, default: () => ({}) },
  /** 仅渲染指定区域（传 section.code）；不传则渲染全部区域 */
  sectionCode: { type: String, default: '' },
  /** 整表只读 */
  disabled: { type: Boolean, default: false },
  /** el-form label 宽度 */
  labelWidth: { type: String, default: '110px' },
  /** 是否展示区域标题（单区域渲染时通常由外层页签承担标题，可关闭） */
  showSectionTitle: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue'])

const { t, te } = useI18n()

const formRef = ref(null)

/**
 * 内部值模型。
 * <p>el-form 的 prop 校验要求 model 为一个「键即 prop」的响应式对象，
 * 故用内部 reactive 承接，并与外部 modelValue 双向同步。</p>
 */
const formModel = reactive({})

/** 待渲染的区域列表（sectionCode 命中则只保留该区域） */
const visibleSections = computed(() => {
  const sections = (props.schema && props.schema.sections) || []
  const list = Array.isArray(sections) ? sections : []
  if (!props.sectionCode) return list
  return list.filter((section) => section && section.code === props.sectionCode)
})

/** 当前渲染范围内的全部启用字段（扁平） */
const visibleFields = computed(() => {
  const result = []
  visibleSections.value.forEach((section) => {
    ;(section.fields || []).forEach((field) => {
      if (field && field.enabled !== false) result.push(field)
    })
  })
  return result
})

/** schema 内全部字段（含未渲染区域），用于跨区域依赖取值 */
const allFields = computed(() => {
  const result = []
  const sections = (props.schema && props.schema.sections) || []
  ;(Array.isArray(sections) ? sections : []).forEach((section) => {
    ;(section.fields || []).forEach((field) => {
      if (field) result.push(field)
    })
  })
  return result
})

/** 依赖映射：下游 code → 上游 code */
const dependsMap = computed(() => {
  const map = {}
  allFields.value.forEach((field) => {
    if (field.dependsOn) map[field.code] = field.dependsOn
  })
  return map
})

/** 由 schema 动态推导的校验规则（键为 field.code） */
const formRules = computed(() => {
  const rules = {}
  visibleFields.value.forEach((field) => {
    const fieldRules = buildRules(field, t)
    if (fieldRules.length > 0) rules[field.code] = fieldRules
  })
  return rules
})

/**
 * 取区域展示名：i18nKey 命中翻译则用翻译，否则回退 name。
 *
 * @param {object} section 区域节点
 * @returns {string}
 */
function sectionLabel(section) {
  if (!section) return ''
  if (section.i18nKey && te(section.i18nKey)) return t(section.i18nKey)
  return section.name || section.code || ''
}

/**
 * 取字段标签：i18nKey 命中翻译则用翻译，否则回退 name，再回退 code。
 *
 * @param {object} field 字段配置
 * @returns {string}
 */
function fieldLabel(field) {
  if (!field) return ''
  if (field.i18nKey && te(field.i18nKey)) return t(field.i18nKey)
  return field.name || field.code || ''
}

/**
 * 取该区域内需要渲染的字段（过滤停用字段）。
 *
 * @param {object} section 区域节点
 * @returns {Array} 字段数组
 */
function fieldsOf(section) {
  if (!section || !Array.isArray(section.fields)) return []
  return section.fields.filter((field) => field && field.enabled !== false)
}

/**
 * 取字段栅格宽度，缺省 12，并夹取到 1~24。
 *
 * @param {object} field 字段配置
 * @returns {number}
 */
function spanOf(field) {
  const raw = Number(field && field.span)
  if (!Number.isFinite(raw) || raw <= 0) return 12
  return Math.min(24, Math.max(1, Math.trunc(raw)))
}

/**
 * 取上游字段当前值（无依赖返回 null）。
 *
 * @param {object} field 字段配置
 * @returns {*}
 */
function parentValueOf(field) {
  const parentCode = field && dependsMap.value[field.code]
  if (!parentCode) return null
  const value = formModel[parentCode]
  return value === undefined ? null : value
}

/**
 * 单字段值变更：写入内部模型并向上冒泡整份扁平 kv。
 *
 * @param {string} code 字段编码
 * @param {*} value 新值
 */
function onFieldChange(code, value) {
  formModel[code] = value
  emit('update:modelValue', { ...formModel })
}

/**
 * 表单校验（失败 resolve(false)，不抛异常）。
 *
 * @returns {Promise<boolean>} 是否通过
 */
function validate() {
  if (!formRef.value) return Promise.resolve(true)
  return formRef.value
    .validate()
    .then(() => true)
    .catch(() => false)
}

/** 清除校验态 */
function clearValidate() {
  if (formRef.value) formRef.value.clearValidate()
}

/**
 * 重置为 schema 的 defaultValue（无默认值则置 null），并同步给父级。
 */
function resetFields() {
  visibleFields.value.forEach((field) => {
    formModel[field.code] = field.defaultValue != null ? field.defaultValue : null
  })
  clearValidate()
  emit('update:modelValue', { ...formModel })
}

// 外部值 → 内部模型（只做增量赋值，避免与本组件的 emit 形成回环）
watch(
  () => props.modelValue,
  (value) => {
    const next = value || {}
    Object.keys(next).forEach((key) => {
      if (formModel[key] !== next[key]) formModel[key] = next[key]
    })
  },
  { immediate: true, deep: true }
)

// schema 变化：为新出现的字段补齐键位（本地补齐，不触发 emit，避免打断父级受控流）
watch(
  () => props.schema,
  () => {
    allFields.value.forEach((field) => {
      if (!(field.code in formModel)) {
        formModel[field.code] = field.defaultValue != null ? field.defaultValue : null
      }
    })
  },
  { immediate: true, deep: true }
)

defineExpose({ validate, clearValidate, resetFields })
</script>

<style scoped>
.dynamic-form__section + .dynamic-form__section {
  margin-top: 8px;
}

.dynamic-form__section-title {
  margin: 0 0 12px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}
</style>

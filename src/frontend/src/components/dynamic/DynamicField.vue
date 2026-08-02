<template>
  <!-- 单字段渲染器：按 field.type 用 <component :is> 分发到具体控件。
       DICT/REF 的选项由内部缓存托管；dependsOn 非空时受上游 parentValue 门控。 -->
  <component
    :is="controlComponent"
    v-bind="controlProps"
    :model-value="modelValue"
    @update:model-value="onUpdate"
  >
    <template v-if="isPlainSelect">
      <el-option
        v-for="opt in flatOptions"
        :key="String(opt.value)"
        :label="opt.label"
        :value="opt.value"
      />
    </template>
  </component>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElInput,
  ElInputNumber,
  ElDatePicker,
  ElSelect,
  ElTreeSelect
} from 'element-plus'
import { getRefOptions, getRefSources } from '@/api/fieldConfig'
import { useDictStore } from '@/store/dict'
import { isTreeDisplay, isMultiSelect, isBlankParentValue } from '@/utils/fieldControls'

const props = defineProps({
  /** 字段配置（FieldConfigVO），必填 */
  field: { type: Object, required: true },
  /** 当前值 */
  modelValue: { type: [String, Number, Boolean, Array, Object, Date], default: null },
  /** 只读 */
  disabled: { type: Boolean, default: false },
  /** 上游字段（field.dependsOn）的当前值，用于联动过滤 */
  parentValue: { type: [String, Number, Array, null], default: null }
})

const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const dictStore = useDictStore()

/**
 * 模块级 REF 选项缓存：key = `${refSource}|${parentValue}`。
 * 同一 refSource + parentValue 组合只请求一次，避免整表单多字段重复打接口。
 * 关键字搜索（remote）不写入缓存，避免污染基础候选集。
 */
const refOptionCache = new Map()

/**
 * 模块级 ref-sources 缓存（整个应用只请求一次）。
 * 用于字段未显式配置 displayType 时，按 registry 的兜底值判定 select / tree。
 */
let refSourcesPromise = null

/**
 * 取引用源注册表（带进程内缓存）。
 *
 * @returns {Promise<Array>} RefSourceVO[]
 */
function loadRefSources() {
  if (!refSourcesPromise) {
    refSourcesPromise = getRefSources().catch(() => [])
  }
  return refSourcesPromise
}

const options = ref([])
const loading = ref(false)
/** 当前字段对应的引用源元数据（仅在 displayType 缺省时用于兜底） */
const refSourceMeta = ref(null)

/** 规范化后的字段类型 */
const fieldType = computed(() => String(props.field.type || '').trim().toUpperCase())

/** 该字段是否声明了上游依赖 */
const hasDepends = computed(() => !!props.field.dependsOn)

/** 上游值为空 → 门控：不发请求、清空选项、禁用控件 */
const parentBlocked = computed(() => hasDepends.value && isBlankParentValue(props.parentValue))

/** REF 是否走树形展示（字段 displayType 优先，缺省时按 registry 兜底） */
const treeMode = computed(
  () => fieldType.value === 'REF' && isTreeDisplay(props.field, refSourceMeta.value)
)

/** 是否为「需要 el-option 子节点」的普通下拉（DICT，或非树形 REF） */
const isPlainSelect = computed(
  () => fieldType.value === 'DICT' || (fieldType.value === 'REF' && !treeMode.value)
)

/** 最终禁用态 */
const isDisabled = computed(() => props.disabled || parentBlocked.value)

/** 占位文案：被上游门控时提示先选上游字段 */
const placeholderText = computed(() => {
  if (parentBlocked.value) return t('fieldConfig.tip.selectParentFirst')
  if (props.field.placeholder) return props.field.placeholder
  return isPlainSelect.value || treeMode.value
    ? t('common.placeholder.select')
    : t('common.placeholder.input')
})

/** 平铺选项（DICT / 非树形 REF 共用） */
const flatOptions = computed(() => (Array.isArray(options.value) ? options.value : []))

/** 控件组件：<component :is> 分发表 */
const controlComponent = computed(() => {
  switch (fieldType.value) {
    case 'NUMBER':
      return ElInputNumber
    case 'DATE':
    case 'DATETIME':
      return ElDatePicker
    case 'DICT':
      return ElSelect
    case 'REF':
      return treeMode.value ? ElTreeSelect : ElSelect
    case 'TEXT':
    default:
      return ElInput
  }
})

/** 控件属性：按类型拼装，统一注入 disabled / placeholder */
const controlProps = computed(() => {
  const field = props.field
  const base = { disabled: isDisabled.value, placeholder: placeholderText.value }

  switch (fieldType.value) {
    case 'TEXT': {
      const attrs = { ...base, clearable: true }
      if (field.multiline === true) {
        attrs.type = 'textarea'
        attrs.rows = 4
      }
      if (field.maxLength != null && Number(field.maxLength) > 0) {
        attrs.maxlength = Number(field.maxLength)
        attrs.showWordLimit = true
      }
      return attrs
    }
    case 'NUMBER': {
      const attrs = { disabled: isDisabled.value, placeholder: placeholderText.value }
      attrs.controlsPosition = 'right'
      attrs.style = 'width: 100%'
      if (field.minVal != null && field.minVal !== '') attrs.min = Number(field.minVal)
      if (field.maxVal != null && field.maxVal !== '') attrs.max = Number(field.maxVal)
      if (field.decimalScale != null && field.decimalScale !== '') {
        attrs.precision = Number(field.decimalScale)
      }
      return attrs
    }
    case 'DATE':
      return {
        ...base,
        type: 'date',
        valueFormat: 'YYYY-MM-DD',
        clearable: true,
        style: 'width: 100%'
      }
    case 'DATETIME':
      return {
        ...base,
        type: 'datetime',
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
        clearable: true,
        style: 'width: 100%'
      }
    case 'DICT':
      return {
        ...base,
        clearable: true,
        filterable: true,
        multiple: isMultiSelect(field),
        loading: loading.value,
        style: 'width: 100%'
      }
    case 'REF': {
      if (treeMode.value) {
        return {
          ...base,
          data: flatOptions.value,
          props: { label: 'label', children: 'children' },
          nodeKey: 'value',
          checkStrictly: true,
          clearable: true,
          filterable: true,
          multiple: isMultiSelect(field),
          defaultExpandAll: true,
          style: 'width: 100%'
        }
      }
      return {
        ...base,
        clearable: true,
        filterable: true,
        remote: true,
        remoteMethod: onRemoteSearch,
        multiple: isMultiSelect(field),
        loading: loading.value,
        style: 'width: 100%'
      }
    }
    default:
      return base
  }
})

/**
 * 拉取 DICT 选项（复用全站 dictStore 缓存，天然去重）。
 *
 * @returns {Promise<void>}
 */
async function loadDictOptions() {
  const dictCode = props.field.dictCode
  if (!dictCode) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const rows = (await dictStore.fetchOptions(dictCode)) || []
    // 全站口径：业务取值用 item_code，展示用 name
    options.value = rows.map((row) => ({ value: row.code, label: row.name }))
  } catch (e) {
    options.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 拉取 REF 选项（按 refSource + parentValue 做组件缓存）。
 *
 * @param {string} [keyword] 远程搜索关键字；传入时绕过缓存且不写缓存
 * @returns {Promise<void>}
 */
async function loadRefOptions(keyword) {
  const refSource = props.field.refSource
  if (!refSource) {
    options.value = []
    return
  }
  const parent = hasDepends.value ? props.parentValue : null
  const cacheKey = `${refSource}|${parent == null ? '' : parent}`

  if (!keyword && refOptionCache.has(cacheKey)) {
    options.value = refOptionCache.get(cacheKey)
    return
  }

  loading.value = true
  try {
    const rows = (await getRefOptions(refSource, parent, keyword)) || []
    options.value = rows
    if (!keyword) refOptionCache.set(cacheKey, rows)
  } catch (e) {
    options.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 按当前类型与门控状态装载选项。
 *
 * @returns {Promise<void>}
 */
async function loadOptions() {
  if (fieldType.value !== 'DICT' && fieldType.value !== 'REF') {
    options.value = []
    return
  }
  // REF 且未显式配置 displayType：先解析 registry 兜底，决定 select / tree
  if (fieldType.value === 'REF' && !props.field.displayType && props.field.refSource) {
    const sources = await loadRefSources()
    refSourceMeta.value =
      (sources || []).find((item) => item.code === props.field.refSource) || null
  } else {
    refSourceMeta.value = null
  }
  // 上游未选：不发请求，选项置空（控件已 disabled）
  if (parentBlocked.value) {
    options.value = []
    return
  }
  if (fieldType.value === 'DICT') {
    await loadDictOptions()
    return
  }
  await loadRefOptions()
}

/**
 * el-select remote-method：按关键字远程检索（仅非树形 REF 启用）。
 *
 * @param {string} keyword 输入的关键字
 */
function onRemoteSearch(keyword) {
  if (fieldType.value !== 'REF' || treeMode.value) return
  if (parentBlocked.value) return
  loadRefOptions(keyword ? String(keyword).trim() : '')
}

/**
 * 控件值变更 → 冒泡给父级。
 *
 * @param {*} value 新值
 */
function onUpdate(value) {
  emit('update:modelValue', value)
}

// 上游值变化：重新拉取选项 + 清空自身当前值，避免脏值残留
watch(
  () => props.parentValue,
  (next, prev) => {
    if (next === prev) return
    if (!isBlankParentValue(props.modelValue)) {
      emit('update:modelValue', isMultiSelect(props.field) ? [] : null)
    }
    loadOptions()
  }
)

// 字段配置变化（类型 / 数据源 / 展示形式切换）：重载选项
watch(
  () => [
    props.field.type,
    props.field.dictCode,
    props.field.refSource,
    props.field.displayType
  ],
  () => {
    loadOptions()
  },
  { immediate: true }
)
</script>

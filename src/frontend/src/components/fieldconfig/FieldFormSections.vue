<template>
  <!-- 【需求二】字段配置弹窗的三区块表单主体（Schema 驱动）。
       信息架构完全由 @/utils/fieldConfigSchema 决定，本组件只负责「怎么画」：
         · 区块顺序、字段顺序、字段归属 → FIELD_FORM_SECTIONS / fieldsOfSection()
         · 每个标签后的问号提示     → tipKeyOf(code)，经 te() 判存后才渲染
         · 显隐                     → visibleOf()（类型属性随 type 动态生效）
         · 禁用                     → disabledOf()（内置字段白名单 + code/type 创建后不可改）
       组件不含任何保存 / 校验 / 请求逻辑，全部留在父级 FieldConfigManage.vue。 -->
  <el-collapse v-model="activeSections" class="field-form-sections">
    <template v-for="section in visibleSections" :key="section.code">
      <el-collapse-item :name="section.code">
        <template #title>
          <span class="section-title">{{ t(section.titleKey) }}</span>
          <span class="section-desc">{{ t(section.descKey) }}</span>
        </template>

        <el-form-item
          v-for="item in visibleFieldsOf(section.code)"
          :key="item.code"
          :prop="item.prop"
          class="section-item"
        >
          <template #label>
            <FormLabelTip :label="labelOf(item.code)" :tip="tipOf(item.code)" />
          </template>

          <!-- ---------------- 基础属性 ---------------- -->
          <el-select
            v-if="item.code === 'sectionId'"
            v-model="model.sectionId"
            :placeholder="
              sectionOptions.length
                ? t('common.placeholder.select')
                : t('fieldConfig.tip.noSectionOption')
            "
            :disabled="disabledOf('sectionId')"
            style="width: 100%"
          >
            <el-option
              v-for="opt in sectionOptions"
              :key="opt.id"
              :label="opt.name"
              :value="opt.id"
            />
          </el-select>

          <el-input
            v-else-if="item.code === 'name'"
            v-model="model.name"
            :placeholder="t('common.placeholder.input')"
            maxlength="50"
            show-word-limit
            :disabled="disabledOf('name')"
          />

          <template v-else-if="item.code === 'code'">
            <!-- Q4 铁律：code 创建后不可改 -->
            <el-input
              v-model="model.code"
              :placeholder="t('common.placeholder.input')"
              maxlength="64"
              :disabled="disabledOf('code')"
            />
            <div v-if="isEdit" class="form-tip">{{ t('fieldConfig.tip.codeReadonly') }}</div>
          </template>

          <el-input
            v-else-if="item.code === 'i18nKey'"
            v-model="model.i18nKey"
            :placeholder="t('common.placeholder.input')"
            maxlength="100"
            :disabled="disabledOf('i18nKey')"
          />

          <template v-else-if="item.code === 'type'">
            <!-- Q4 铁律：type 创建后不可改（后端抛 FIELD_TYPE_IMMUTABLE） -->
            <el-select
              v-model="model.type"
              :placeholder="t('common.placeholder.select')"
              :disabled="disabledOf('type')"
              style="width: 100%"
              @change="emit('type-change', $event)"
            >
              <el-option
                v-for="opt in FIELD_TYPES"
                :key="opt"
                :label="t(`fieldConfig.type.${opt}`)"
                :value="opt"
              />
            </el-select>
            <div v-if="isEdit" class="form-tip">{{ t('fieldConfig.tip.typeReadonly') }}</div>
          </template>

          <el-input-number
            v-else-if="item.code === 'span'"
            v-model="model.span"
            :min="1"
            :max="24"
            :disabled="disabledOf('span')"
          />

          <el-input
            v-else-if="item.code === 'placeholder'"
            v-model="model.placeholder"
            :placeholder="t('common.placeholder.input')"
            maxlength="100"
            :disabled="disabledOf('placeholder')"
          />

          <el-input
            v-else-if="item.code === 'defaultValue'"
            v-model="model.defaultValue"
            :placeholder="t('common.placeholder.input')"
            maxlength="200"
            :disabled="disabledOf('defaultValue')"
          />

          <el-switch
            v-else-if="item.code === 'required'"
            v-model="model.required"
            :disabled="disabledOf('required')"
          />

          <!-- ---------------- 类型属性 ---------------- -->
          <el-switch
            v-else-if="item.code === 'multiline'"
            v-model="model.multiline"
            :disabled="disabledOf('multiline')"
          />

          <el-input-number
            v-else-if="item.code === 'maxLength'"
            v-model="model.maxLength"
            :min="1"
            :max="65535"
            :disabled="disabledOf('maxLength')"
          />

          <el-input-number
            v-else-if="item.code === 'minVal'"
            v-model="model.minVal"
            controls-position="right"
            :disabled="disabledOf('minVal')"
          />

          <el-input-number
            v-else-if="item.code === 'maxVal'"
            v-model="model.maxVal"
            controls-position="right"
            :disabled="disabledOf('maxVal')"
          />

          <el-input-number
            v-else-if="item.code === 'decimalScale'"
            v-model="model.decimalScale"
            :min="0"
            :max="6"
            :disabled="disabledOf('decimalScale')"
          />

          <el-select
            v-else-if="item.code === 'dictCode'"
            v-model="model.dictCode"
            :placeholder="t('common.placeholder.select')"
            filterable
            clearable
            :disabled="disabledOf('dictCode')"
            style="width: 100%"
          >
            <el-option
              v-for="opt in dictTypeOptions"
              :key="opt.code"
              :label="`${opt.name} (${opt.code})`"
              :value="opt.code"
            />
          </el-select>

          <el-select
            v-else-if="item.code === 'refSource'"
            v-model="model.refSource"
            :placeholder="t('common.placeholder.select')"
            filterable
            clearable
            :disabled="disabledOf('refSource')"
            style="width: 100%"
            @change="emit('ref-source-change', $event)"
          >
            <el-option
              v-for="opt in refSourceOptions"
              :key="opt.code"
              :label="`${opt.name} (${opt.code})`"
              :value="opt.code"
            />
          </el-select>

          <el-select
            v-else-if="item.code === 'displayType'"
            v-model="model.displayType"
            :placeholder="t('common.placeholder.select')"
            clearable
            :disabled="disabledOf('displayType')"
            style="width: 100%"
          >
            <el-option
              v-for="opt in DISPLAY_TYPES"
              :key="opt"
              :label="t(`fieldConfig.displayType.${opt}`)"
              :value="opt"
            />
          </el-select>

          <el-switch
            v-else-if="item.code === 'multiSelect'"
            v-model="model.multiSelect"
            :disabled="disabledOf('multiSelect')"
          />

          <template v-else-if="item.code === 'dependsOn'">
            <el-select
              v-model="model.dependsOn"
              :placeholder="t('common.placeholder.select')"
              clearable
              :disabled="disabledOf('dependsOn')"
              style="width: 100%"
            >
              <el-option
                v-for="opt in dependsCandidates"
                :key="opt.code"
                :label="`${opt.name} (${opt.code})`"
                :value="opt.code"
              />
            </el-select>
            <div class="form-tip">{{ t('fieldConfig.tip.dependsOnly') }}</div>
          </template>

          <template v-else-if="item.code === 'dependsParam'">
            <el-input
              v-model="model.dependsParam"
              :placeholder="t('common.placeholder.input')"
              maxlength="64"
              :disabled="disabledOf('dependsParam')"
            />
            <div class="form-tip">{{ t('fieldConfig.tip.dependsPair') }}</div>
          </template>

          <!-- ---------------- 高级属性 ---------------- -->
          <el-input-number
            v-else-if="item.code === 'sort'"
            v-model="model.sort"
            :min="0"
            :max="9999"
            :disabled="disabledOf('sort')"
          />

          <el-switch
            v-else-if="item.code === 'visibleInList'"
            v-model="model.visibleInList"
            :disabled="disabledOf('visibleInList')"
          />

          <el-switch
            v-else-if="item.code === 'searchable'"
            v-model="model.searchable"
            :disabled="disabledOf('searchable')"
          />

          <el-switch
            v-else-if="item.code === 'enabled'"
            v-model="model.enabled"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
            :disabled="disabledOf('enabled')"
          />
        </el-form-item>
      </el-collapse-item>
    </template>
  </el-collapse>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElCollapse,
  ElCollapseItem,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch
} from 'element-plus'
import FormLabelTip from './FormLabelTip.vue'
import {
  FIELD_FORM_SECTIONS,
  SECTION_TYPE,
  fieldsOfSection,
  labelKeyOf,
  tipKeyOf
} from '@/utils/fieldConfigSchema'
import {
  DISPLAY_TYPES,
  FIELD_TYPES,
  SYSTEM_FIELD_EDITABLE_ATTRS,
  attrsOfType
} from '@/utils/fieldControls'

const props = defineProps({
  /** 表单模型（父级 reactive 对象，双向绑定） */
  model: { type: Object, required: true },
  /** 模式：create 新增 / edit 编辑 */
  mode: { type: String, default: 'create' },
  /** 是否为内置字段（system=true），命中则走属性白名单 */
  systemField: { type: Boolean, default: false },
  /** 区域下拉数据源 */
  sectionOptions: { type: Array, default: () => [] },
  /** 字典类型下拉数据源 */
  dictTypeOptions: { type: Array, default: () => [] },
  /** 引用源下拉数据源 */
  refSourceOptions: { type: Array, default: () => [] },
  /** 依赖字段候选 */
  dependsCandidates: { type: Array, default: () => [] }
})

const emit = defineEmits(['type-change', 'ref-source-change'])

const { t, te } = useI18n()

/** 三区块默认全部展开：管理员多数情况下要通览全部属性 */
const activeSections = ref(FIELD_FORM_SECTIONS.map((section) => section.code))

/** 是否编辑态 */
const isEdit = computed(() => props.mode === 'edit')

/** 当前字段类型生效的专属属性 */
const activeTypeAttrs = computed(() => attrsOfType(props.model.type))

/**
 * 字段是否需要渲染。
 *
 * <p>类型属性随 type 动态显隐；其余字段恒显示。</p>
 *
 * @param {object} item schema 编排项
 * @returns {boolean}
 */
function visibleOf(item) {
  if (item.typeAttr === true) {
    return activeTypeAttrs.value.includes(item.code)
  }
  return true
}

/**
 * 取某区块下当前可见的字段编排项。
 *
 * @param {string} sectionCode 区块编码
 * @returns {Array<object>}
 */
function visibleFieldsOf(sectionCode) {
  return fieldsOfSection(sectionCode).filter(visibleOf)
}

/** 有可见字段的区块才渲染（类型属性为空时不留空壳区块） */
const visibleSections = computed(() =>
  FIELD_FORM_SECTIONS.filter((section) => {
    if (section.code !== SECTION_TYPE) return true
    return visibleFieldsOf(SECTION_TYPE).length > 0
  })
)

/**
 * 字段是否禁用。
 *
 * <p>三条来源：</p>
 * <ul>
 *   <li>code / type —— 创建后不可改（Q4 铁律）；</li>
 *   <li>内置字段 —— 仅放行 {@link SYSTEM_FIELD_EDITABLE_ATTRS} 白名单；</li>
 *   <li>其余一律可编辑。</li>
 * </ul>
 *
 * @param {string} code 属性名
 * @returns {boolean}
 */
function disabledOf(code) {
  if ((code === 'code' || code === 'type') && isEdit.value) {
    return true
  }
  if (props.systemField) {
    return !SYSTEM_FIELD_EDITABLE_ATTRS.includes(code)
  }
  return false
}

/**
 * 取属性标签文案。
 *
 * @param {string} code 属性名
 * @returns {string}
 */
function labelOf(code) {
  const key = labelKeyOf(code)
  return te(key) ? t(key) : code
}

/**
 * 取属性问号提示文案；未配置提示时返回空串（FormLabelTip 不渲染问号）。
 *
 * @param {string} code 属性名
 * @returns {string}
 */
function tipOf(code) {
  const key = tipKeyOf(code)
  return te(key) ? t(key) : ''
}
</script>

<style scoped>
.field-form-sections {
  border-top: none;
}

.field-form-sections :deep(.el-collapse-item__header) {
  height: 40px;
  line-height: 40px;
  font-size: 14px;
}

.field-form-sections :deep(.el-collapse-item__content) {
  padding-top: 12px;
  padding-bottom: 4px;
}

.section-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-desc {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.section-item {
  margin-bottom: 18px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .section-desc {
    display: none;
  }
}
</style>

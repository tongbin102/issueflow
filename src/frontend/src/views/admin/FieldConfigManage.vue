<template>
  <!-- T04：字段配置（系统管理 > 字段配置）
       树形表格：区域为父行 / 字段为子行；区域维护待后端补齐接口，当前只读。 -->
  <div class="field-config-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <div class="head-main">
            <span class="head-title">{{ t('fieldConfig.page.title') }}</span>
            <span class="head-sub">{{ t('fieldConfig.page.subtitle') }}</span>
          </div>
          <div class="head-ops">
            <el-button @click="openPreview">{{ t('fieldConfig.action.preview') }}</el-button>
            <el-button type="primary" :icon="Plus" @click="openFieldCreate">
              {{ t('fieldConfig.action.createField') }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 后端缺口提示：区域增删改接口未提供，区域行只读 -->
      <el-alert
        class="section-alert"
        type="info"
        :closable="false"
        show-icon
        :title="t('fieldConfig.tip.sectionReadonly')"
      />

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item :label="t('common.field.keyword')">
          <el-input
            v-model="keyword"
            :placeholder="t('common.placeholder.search')"
            clearable
            :style="isMobile ? 'width: 100%' : 'width: 220px'"
          />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="fetchAll">{{ t('common.action.refresh') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="tableLoading"
        :data="filteredTree"
        row-key="rowKey"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
        :empty-text="t('common.msg.noData')"
      >
        <el-table-column
          prop="name"
          :label="t('fieldConfig.col.name')"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="node-name" :class="{ 'is-section': row.nodeType === 'section' }">
              {{ row.name }}
            </span>
            <el-tag v-if="row.system" class="node-tag" size="small" type="info" effect="plain">
              {{ t('fieldConfig.tag.system') }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="code" :label="t('fieldConfig.col.code')" min-width="150" />

        <el-table-column :label="t('fieldConfig.col.type')" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.nodeType === 'field'" size="small" effect="light">
              {{ typeLabel(row.type) }}
            </el-tag>
            <span v-else class="muted">{{ t('fieldConfig.nodeType.section') }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('fieldConfig.col.refSource')" width="130">
          <template #default="{ row }">{{ row.refSource || '-' }}</template>
        </el-table-column>

        <el-table-column :label="t('fieldConfig.col.dependsOn')" width="130">
          <template #default="{ row }">{{ row.dependsOn || '-' }}</template>
        </el-table-column>

        <el-table-column prop="sort" :label="t('fieldConfig.col.sort')" width="80" align="center" />

        <el-table-column :label="t('fieldConfig.col.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">
              {{ row.enabled ? t('common.status.enabled') : t('common.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="t('fieldConfig.col.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.nodeType === 'field'">
              <el-button link type="primary" size="small" @click="openFieldEdit(row)">
                {{ t('common.action.edit') }}
              </el-button>
              <el-button
                link
                :type="row.enabled ? 'warning' : 'success'"
                size="small"
                @click="onToggleField(row)"
              >
                {{ row.enabled ? t('common.action.disable') : t('common.action.enable') }}
              </el-button>
              <el-tooltip
                v-if="row.system"
                :content="t('fieldConfig.tip.systemFieldDelete')"
                placement="top"
              >
                <span class="op-disabled-wrap">
                  <el-button link type="danger" size="small" disabled>
                    {{ t('common.action.delete') }}
                  </el-button>
                </span>
              </el-tooltip>
              <el-button v-else link type="danger" size="small" @click="onDeleteField(row)">
                {{ t('common.action.delete') }}
              </el-button>
            </template>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 字段新增 / 编辑抽屉 -->
    <FormDrawer
      v-model="fieldDrawerVisible"
      :title="fieldEditing ? t('fieldConfig.drawer.editField') : t('fieldConfig.drawer.createField')"
      size="md"
      :loading="fieldSaving"
      @confirm="onSaveField"
      @closed="resetFieldForm"
    >
      <el-form
        ref="fieldFormRef"
        v-loading="detailLoading"
        :model="fieldForm"
        :rules="fieldRules"
        label-width="112px"
      >
        <el-alert
          v-if="isSystemEditing"
          class="drawer-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="t('fieldConfig.tip.systemAttrLocked')"
        />

        <el-divider content-position="left">{{ t('fieldConfig.form.baseGroup') }}</el-divider>

        <el-form-item :label="t('fieldConfig.form.section')" prop="sectionId">
          <el-select
            v-model="fieldForm.sectionId"
            :placeholder="
              sectionOptions.length ? t('common.placeholder.select') : t('fieldConfig.tip.noSectionOption')
            "
            :disabled="attrLocked('sectionId')"
            style="width: 100%"
          >
            <el-option
              v-for="item in sectionOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.name')" prop="name">
          <el-input
            v-model="fieldForm.name"
            :placeholder="t('common.placeholder.input')"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.code')" prop="code">
          <!-- Q4 铁律：code 创建后不可改 -->
          <el-input
            v-model="fieldForm.code"
            :placeholder="t('common.placeholder.input')"
            maxlength="64"
            :disabled="!!fieldEditing"
          />
          <div v-if="fieldEditing" class="form-tip">{{ t('fieldConfig.tip.codeReadonly') }}</div>
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.i18nKey')" prop="i18nKey">
          <el-input
            v-model="fieldForm.i18nKey"
            :placeholder="t('common.placeholder.input')"
            maxlength="100"
          />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.type')" prop="type">
          <!-- Q4 铁律：type 创建后不可改（后端抛 FIELD_TYPE_IMMUTABLE） -->
          <el-select
            v-model="fieldForm.type"
            :placeholder="t('common.placeholder.select')"
            :disabled="!!fieldEditing"
            style="width: 100%"
            @change="onTypeChange"
          >
            <el-option
              v-for="item in FIELD_TYPES"
              :key="item"
              :label="typeLabel(item)"
              :value="item"
            />
          </el-select>
          <div v-if="fieldEditing" class="form-tip">{{ t('fieldConfig.tip.typeReadonly') }}</div>
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.required')" prop="required">
          <el-switch v-model="fieldForm.required" />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.placeholder')" prop="placeholder">
          <el-input
            v-model="fieldForm.placeholder"
            :placeholder="t('common.placeholder.input')"
            maxlength="100"
          />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.defaultValue')" prop="defaultValue">
          <el-input
            v-model="fieldForm.defaultValue"
            :placeholder="t('common.placeholder.input')"
            maxlength="200"
            :disabled="attrLocked('defaultValue')"
          />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.span')" prop="span">
          <el-input-number v-model="fieldForm.span" :min="1" :max="24" />
        </el-form-item>

        <!-- 类型专属属性：按 attrsOfType(type) 动态显隐 -->
        <template v-if="activeTypeAttrs.length > 0">
          <el-divider content-position="left">{{ t('fieldConfig.form.attrGroup') }}</el-divider>

          <el-form-item v-if="showAttr('multiline')" :label="t('fieldConfig.form.multiline')">
            <el-switch v-model="fieldForm.multiline" :disabled="attrLocked('multiline')" />
          </el-form-item>

          <el-form-item v-if="showAttr('maxLength')" :label="t('fieldConfig.form.maxLength')">
            <el-input-number
              v-model="fieldForm.maxLength"
              :min="1"
              :max="65535"
              :disabled="attrLocked('maxLength')"
            />
          </el-form-item>

          <el-form-item v-if="showAttr('minVal')" :label="t('fieldConfig.form.minVal')">
            <el-input-number
              v-model="fieldForm.minVal"
              :controls-position="'right'"
              :disabled="attrLocked('minVal')"
            />
          </el-form-item>

          <el-form-item v-if="showAttr('maxVal')" :label="t('fieldConfig.form.maxVal')">
            <el-input-number
              v-model="fieldForm.maxVal"
              :controls-position="'right'"
              :disabled="attrLocked('maxVal')"
            />
          </el-form-item>

          <el-form-item v-if="showAttr('decimalScale')" :label="t('fieldConfig.form.decimalScale')">
            <el-input-number
              v-model="fieldForm.decimalScale"
              :min="0"
              :max="6"
              :disabled="attrLocked('decimalScale')"
            />
          </el-form-item>

          <el-form-item
            v-if="showAttr('dictCode')"
            :label="t('fieldConfig.form.dictCode')"
            prop="dictCode"
          >
            <el-select
              v-model="fieldForm.dictCode"
              :placeholder="t('common.placeholder.select')"
              filterable
              clearable
              :disabled="attrLocked('dictCode')"
              style="width: 100%"
            >
              <el-option
                v-for="item in dictTypeOptions"
                :key="item.code"
                :label="`${item.name} (${item.code})`"
                :value="item.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item
            v-if="showAttr('refSource')"
            :label="t('fieldConfig.form.refSource')"
            prop="refSource"
          >
            <el-select
              v-model="fieldForm.refSource"
              :placeholder="t('common.placeholder.select')"
              filterable
              clearable
              :disabled="attrLocked('refSource')"
              style="width: 100%"
              @change="onRefSourceChange"
            >
              <el-option
                v-for="item in refSourceOptions"
                :key="item.code"
                :label="`${item.name} (${item.code})`"
                :value="item.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="showAttr('displayType')" :label="t('fieldConfig.form.displayType')">
            <el-select
              v-model="fieldForm.displayType"
              :placeholder="t('common.placeholder.select')"
              clearable
              :disabled="attrLocked('displayType')"
              style="width: 100%"
            >
              <el-option
                v-for="item in DISPLAY_TYPES"
                :key="item"
                :label="t(`fieldConfig.displayType.${item}`)"
                :value="item"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="showAttr('multiSelect')" :label="t('fieldConfig.form.multiSelect')">
            <el-switch v-model="fieldForm.multiSelect" :disabled="attrLocked('multiSelect')" />
          </el-form-item>

          <el-form-item
            v-if="showAttr('dependsOn')"
            :label="t('fieldConfig.form.dependsOn')"
            prop="dependsOn"
          >
            <el-select
              v-model="fieldForm.dependsOn"
              :placeholder="t('common.placeholder.select')"
              clearable
              :disabled="attrLocked('dependsOn')"
              style="width: 100%"
            >
              <el-option
                v-for="item in dependsCandidates"
                :key="item.code"
                :label="`${item.name} (${item.code})`"
                :value="item.code"
              />
            </el-select>
            <div class="form-tip">{{ t('fieldConfig.tip.dependsOnly') }}</div>
          </el-form-item>

          <el-form-item
            v-if="showAttr('dependsParam')"
            :label="t('fieldConfig.form.dependsParam')"
            prop="dependsParam"
          >
            <el-input
              v-model="fieldForm.dependsParam"
              :placeholder="t('common.placeholder.input')"
              maxlength="64"
              :disabled="attrLocked('dependsParam')"
            />
            <div class="form-tip">{{ t('fieldConfig.tip.dependsPair') }}</div>
          </el-form-item>
        </template>

        <el-divider content-position="left">{{ t('fieldConfig.form.advanceGroup') }}</el-divider>

        <el-form-item :label="t('fieldConfig.form.sort')" prop="sort">
          <el-input-number v-model="fieldForm.sort" :min="0" :max="9999" />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.visibleInList')">
          <el-switch v-model="fieldForm.visibleInList" :disabled="attrLocked('visibleInList')" />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.searchable')">
          <el-switch v-model="fieldForm.searchable" :disabled="attrLocked('searchable')" />
        </el-form-item>

        <el-form-item :label="t('fieldConfig.form.status')" prop="enabled">
          <el-switch
            v-model="fieldForm.enabled"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
          />
        </el-form-item>
      </el-form>
    </FormDrawer>

    <!-- 表单预览抽屉：直接挂载 DynamicFormRenderer，所见即所得 -->
    <FormDrawer
      v-model="previewVisible"
      :title="t('fieldConfig.drawer.preview')"
      size="lg"
      fullscreenable
      :confirm-text="t('common.action.close')"
      @confirm="previewVisible = false"
    >
      <div v-loading="previewLoading" class="preview-body">
        <DynamicFormRenderer
          v-if="previewSchema"
          v-model="previewModel"
          :schema="previewSchema"
          :disabled="false"
        />
        <el-empty v-else :description="t('fieldConfig.tip.previewEmpty')" />
      </div>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import DynamicFormRenderer from '@/components/dynamic/DynamicFormRenderer.vue'
import {
  getFieldTree,
  getFieldSchema,
  getFieldConfig,
  getRefSources,
  createFieldConfig,
  updateFieldConfig,
  deleteFieldConfig,
  toggleFieldConfig,
  normalizeFieldReq,
  buildFieldTree
} from '@/api/fieldConfig'
import { listDictTypes } from '@/api/dict'
import {
  FIELD_TYPES,
  DISPLAY_TYPES,
  SYSTEM_FIELD_EDITABLE_ATTRS,
  attrsOfType,
  isValidDependsCandidate
} from '@/utils/fieldControls'
import { useAppStore } from '@/store/app'

const { t } = useI18n()
const appStore = useAppStore()

const isMobile = computed(() => appStore.isMobile)

/* ------------------------------------------------------------ 列表与数据源 */
const tableLoading = ref(false)
const treeData = ref([])
const keyword = ref('')
const schema = ref(null)
const refSourceOptions = ref([])
const dictTypeOptions = ref([])

/** 区域下拉（来自 /tree 的 section 节点；后端暂无区域维护接口，仅供选择） */
const sectionOptions = computed(() =>
  treeData.value.filter((node) => node.nodeType === 'section')
)

/** schema 内全部字段，用于依赖候选（FieldNodeVO 缺 multiSelect，必须取 schema） */
const allSchemaFields = computed(() => {
  const sections = (schema.value && schema.value.sections) || []
  const result = []
  sections.forEach((section) => {
    ;(section.fields || []).forEach((field) => {
      if (field) result.push(field)
    })
  })
  return result
})

/** 关键字过滤：命中字段则保留其所属区域，命中区域则整段保留 */
const filteredTree = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return treeData.value
  const match = (node) =>
    String(node.name || '').toLowerCase().includes(kw) ||
    String(node.code || '').toLowerCase().includes(kw)

  const result = []
  treeData.value.forEach((node) => {
    if (node.nodeType !== 'section') {
      if (match(node)) result.push(node)
      return
    }
    const children = (node.children || []).filter(match)
    if (match(node)) {
      result.push(node)
    } else if (children.length > 0) {
      result.push({ ...node, children })
    }
  })
  return result
})

/**
 * 字段类型展示名（i18n）。
 *
 * @param {string} type 类型枚举名
 * @returns {string}
 */
function typeLabel(type) {
  if (!type) return '-'
  const key = String(type).trim().toUpperCase()
  return FIELD_TYPES.includes(key) ? t(`fieldConfig.type.${key}`) : key
}

/**
 * 拉取树形数据 + schema + 下拉数据源。
 *
 * @returns {Promise<void>}
 */
async function fetchAll() {
  tableLoading.value = true
  try {
    const [nodes, schemaData] = await Promise.all([
      getFieldTree().catch(() => []),
      getFieldSchema().catch(() => null)
    ])
    treeData.value = buildFieldTree(nodes)
    schema.value = schemaData
  } finally {
    tableLoading.value = false
  }
}

/**
 * 拉取配置抽屉需要的下拉数据源（引用源 + 字典类型）。
 *
 * @returns {Promise<void>}
 */
async function fetchLookups() {
  const [refs, dicts] = await Promise.all([
    getRefSources().catch(() => []),
    listDictTypes({}).catch(() => [])
  ])
  refSourceOptions.value = refs || []
  dictTypeOptions.value = dicts || []
}

/* ------------------------------------------------------------ 字段抽屉 */
const fieldDrawerVisible = ref(false)
const fieldSaving = ref(false)
const detailLoading = ref(false)
const fieldFormRef = ref(null)
/** 正在编辑的字段（null 表示新增） */
const fieldEditing = ref(null)

const DEFAULT_FIELD_FORM = {
  sectionId: null,
  code: '',
  name: '',
  i18nKey: '',
  type: 'TEXT',
  required: false,
  placeholder: '',
  defaultValue: '',
  span: 12,
  multiline: false,
  maxLength: null,
  minVal: null,
  maxVal: null,
  decimalScale: null,
  dictCode: '',
  refSource: '',
  displayType: '',
  multiSelect: false,
  dependsOn: '',
  dependsParam: '',
  typeScope: 'GLOBAL',
  sort: 0,
  enabled: true,
  visibleInList: false,
  searchable: false
}

const fieldForm = reactive({ ...DEFAULT_FIELD_FORM })

/** 是否正在编辑内置字段（属性白名单生效） */
const isSystemEditing = computed(() => !!(fieldEditing.value && fieldEditing.value.system))

/** 当前类型生效的专属属性 */
const activeTypeAttrs = computed(() => attrsOfType(fieldForm.type))

/** 依赖候选：排除自身 / 多选 / 已有上游 / 非 DICT|REF */
const dependsCandidates = computed(() =>
  allSchemaFields.value.filter((item) => isValidDependsCandidate(item, fieldForm.code))
)

/**
 * 该专属属性是否需要渲染。
 *
 * @param {string} attr 属性名
 * @returns {boolean}
 */
function showAttr(attr) {
  return activeTypeAttrs.value.includes(attr)
}

/**
 * 内置字段编辑时，非白名单属性一律置灰。
 *
 * @param {string} attr 属性名
 * @returns {boolean}
 */
function attrLocked(attr) {
  return isSystemEditing.value && !SYSTEM_FIELD_EDITABLE_ATTRS.includes(attr)
}

/** dependsOn / dependsParam 必须成对出现 */
function validateDependsPair(rule, value, callback) {
  const hasOn = !!(fieldForm.dependsOn && String(fieldForm.dependsOn).trim())
  const hasParam = !!(fieldForm.dependsParam && String(fieldForm.dependsParam).trim())
  if (hasOn !== hasParam) {
    callback(new Error(t('fieldConfig.tip.dependsPair')))
    return
  }
  callback()
}

const fieldRules = computed(() => ({
  sectionId: [
    { required: true, message: t('fieldConfig.rules.sectionRequired'), trigger: 'change' }
  ],
  name: [{ required: true, message: t('fieldConfig.rules.nameRequired'), trigger: 'blur' }],
  code: [
    { required: true, message: t('fieldConfig.rules.codeRequired'), trigger: 'blur' },
    { pattern: /^[a-z][a-zA-Z0-9]*$/, message: t('fieldConfig.rules.codePattern'), trigger: 'blur' }
  ],
  type: [{ required: true, message: t('fieldConfig.rules.typeRequired'), trigger: 'change' }],
  dictCode:
    fieldForm.type === 'DICT'
      ? [{ required: true, message: t('fieldConfig.rules.dictCodeRequired'), trigger: 'change' }]
      : [],
  refSource:
    fieldForm.type === 'REF'
      ? [{ required: true, message: t('fieldConfig.rules.refSourceRequired'), trigger: 'change' }]
      : [],
  dependsOn: [{ validator: validateDependsPair, trigger: 'change' }],
  dependsParam: [{ validator: validateDependsPair, trigger: 'blur' }]
}))

/** 类型切换：清空上一类型的专属属性，避免脏值提交 */
function onTypeChange() {
  const keep = new Set(activeTypeAttrs.value)
  const typeOnlyAttrs = [
    'multiline',
    'maxLength',
    'minVal',
    'maxVal',
    'decimalScale',
    'dictCode',
    'refSource',
    'displayType',
    'multiSelect',
    'dependsOn',
    'dependsParam'
  ]
  typeOnlyAttrs.forEach((attr) => {
    if (!keep.has(attr)) fieldForm[attr] = DEFAULT_FIELD_FORM[attr]
  })
}

/** 选择引用源后，displayType 默认填 registry 的兜底值 */
function onRefSourceChange(code) {
  if (!code) {
    fieldForm.displayType = ''
    return
  }
  const hit = refSourceOptions.value.find((item) => item.code === code)
  fieldForm.displayType = hit ? hit.displayType || '' : ''
}

function openFieldCreate() {
  fieldEditing.value = null
  Object.assign(fieldForm, DEFAULT_FIELD_FORM)
  if (sectionOptions.value.length > 0) {
    fieldForm.sectionId = sectionOptions.value[0].id
  }
  fieldDrawerVisible.value = true
}

/**
 * 打开编辑抽屉：按 id 拉详情回显（FieldNodeVO 信息不全）。
 *
 * @param {object} row 表格行（field 节点）
 * @returns {Promise<void>}
 */
async function openFieldEdit(row) {
  fieldEditing.value = row
  Object.assign(fieldForm, DEFAULT_FIELD_FORM)
  fieldDrawerVisible.value = true
  detailLoading.value = true
  try {
    const detail = await getFieldConfig(row.id)
    if (!detail) return
    fieldEditing.value = { ...row, system: detail.system === true }
    Object.keys(DEFAULT_FIELD_FORM).forEach((key) => {
      const value = detail[key]
      if (value === undefined || value === null) return
      fieldForm[key] = value
    })
    // Boolean 位显式归一，避免后端返回 null 时残留上一次的值
    fieldForm.required = detail.required === true
    fieldForm.multiline = detail.multiline === true
    fieldForm.multiSelect = detail.multiSelect === true
    fieldForm.enabled = detail.enabled !== false
    fieldForm.visibleInList = detail.visibleInList === true
    fieldForm.searchable = detail.searchable === true
  } catch (e) {
    // 拦截器已提示
  } finally {
    detailLoading.value = false
  }
}

function resetFieldForm() {
  fieldEditing.value = null
  Object.assign(fieldForm, DEFAULT_FIELD_FORM)
  if (fieldFormRef.value) fieldFormRef.value.clearValidate()
}

function onSaveField() {
  if (!fieldFormRef.value) return
  fieldFormRef.value.validate(async (valid) => {
    if (!valid) return
    fieldSaving.value = true
    const editing = fieldEditing.value
    try {
      const payload = normalizeFieldReq(fieldForm, {
        systemField: !!(editing && editing.system)
      })
      if (editing) {
        await updateFieldConfig(editing.id, payload)
        ElMessage.success(t('fieldConfig.msg.updateSuccess'))
      } else {
        await createFieldConfig(payload)
        ElMessage.success(t('fieldConfig.msg.createSuccess'))
      }
      fieldDrawerVisible.value = false
      await fetchAll()
    } catch (e) {
      // 业务异常由响应拦截器统一提示
    } finally {
      fieldSaving.value = false
    }
  })
}

/**
 * 启停切换。
 *
 * @param {object} row 字段行
 * @returns {Promise<void>}
 */
async function onToggleField(row) {
  try {
    await toggleFieldConfig(row.id, !row.enabled)
    ElMessage.success(t('fieldConfig.msg.toggleSuccess'))
    await fetchAll()
  } catch (e) {
    // 拦截器已提示
  }
}

/**
 * 删除字段（内置字段按钮已 disabled，此处仅处理自定义字段）。
 *
 * @param {object} row 字段行
 */
function onDeleteField(row) {
  ElMessageBox.confirm(
    t('fieldConfig.msg.deleteConfirm', { name: row.name }),
    t('common.msg.tip'),
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteFieldConfig(row.id)
        ElMessage.success(t('fieldConfig.msg.deleteSuccess'))
        await fetchAll()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

/* ------------------------------------------------------------ 预览抽屉 */
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewSchema = ref(null)
const previewModel = ref({})

/**
 * 打开预览：实时拉一次最新 schema，保证所见即为当前配置。
 *
 * @returns {Promise<void>}
 */
async function openPreview() {
  previewVisible.value = true
  previewLoading.value = true
  previewModel.value = {}
  try {
    previewSchema.value = await getFieldSchema()
  } catch (e) {
    previewSchema.value = null
  } finally {
    previewLoading.value = false
  }
}

fetchAll()
fetchLookups()
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.head-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.head-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.head-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.head-ops {
  flex-shrink: 0;
}

.section-alert {
  margin-bottom: 12px;
}

.filter-form {
  margin-bottom: 4px;
}

.node-name.is-section {
  font-weight: 600;
}

.node-tag {
  margin-left: 6px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.op-disabled-wrap {
  display: inline-block;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.drawer-alert {
  margin-bottom: 12px;
}

.preview-body {
  min-height: 200px;
  padding: 4px 8px;
}

@media (max-width: 768px) {
  .head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

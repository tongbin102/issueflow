<template>
  <!-- 问题表单（提交 / 编辑 / 查看共用）。
       T05a：硬编码表单整体下线，改为由 FieldSchemaVO 驱动的 DynamicFormRenderer 渲染。
       - 外层不再包 <el-form>：每个业务页签内各放一个 DynamicFormRenderer（自带 el-form），各自独立校验；
       - 业务页签（基本信息 / 详细描述 / 环境信息 …）由 schema.sections 动态生成，sectionCode 一一对应；
       - 系统页签（附件 / 关联 / 历史）仍走 IssueFormSections 的固定具名插槽，实现不变；
       - 内置字段（title/typeCode/source/severity/priority/projectId/moduleId/tags/description/
         reproduceSteps/envOs/envBrowser/envAppVersion/envDevice）平铺在提交体顶层；
         自定义字段（system=false）统一收进 customFields: { [code]: value }；
       - 项目→模块联动由 DynamicField 的 dependsOn 机制接管（种子里 moduleId.dependsOn=projectId）。
       自 Phase6 起不再内置提交按钮：由父级 FormDrawer 底部按钮触发 submit()（defineExpose）。 -->
  <IssueFormSections
    ref="sectionsRef"
    :schema="schemaStore.schema"
    :filled-tabs="filledTabs"
    @change="onTabChange"
  >
    <!-- ===== 业务页签：由 schema.sections 动态生成，每个页签内一个 DynamicFormRenderer ===== -->
    <template v-for="section in schemaSections" :key="section.code" #[section.code]>
      <DynamicFormRenderer
        :ref="(el) => setRendererRef(section.code, el)"
        :section-code="section.code"
        :schema="schemaStore.schema"
        :show-section-title="false"
        :model-value="model"
        :disabled="readonly"
        @update:model-value="onModelPatch"
      />
    </template>

    <!-- ===== 系统页签：附件上传 =====
         新建态：本地暂存，随表单 multipart 一起提交；
         编辑/查看态：带 issueId 走服务端即时上传/删除。 -->
    <template #attachment>
      <el-form-item :label="t('issue.form.attachment')">
        <AttachmentUploader
          v-if="!issueId"
          ref="uploaderRef"
          @change="onFilesChange"
        />
        <AttachmentUploader
          v-else
          :issue-id="issueId"
          :attachments="attachments"
          @uploaded="onAttachmentUploaded"
          @removed="onAttachmentRemoved"
        />
      </el-form-item>
    </template>

    <!-- ===== 系统页签：关联信息 ===== -->
    <template #relation>
      <IssueRelationPanel
        v-if="issueId"
        :issue-id="issueId"
        :can-edit="isEdit"
      />
      <el-empty v-else :description="t('issue.tabTip.relationPending')" :image-size="72" />
    </template>

    <!-- ===== 系统页签：操作历史 ===== -->
    <template #history>
      <div v-if="issueId" v-loading="historyLoading">
        <StatusTimeline :history="history" />
      </div>
      <el-empty v-else :description="t('issue.tabTip.historyPending')" :image-size="72" />
    </template>
  </IssueFormSections>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { DEFAULT_PRIORITY, DEFAULT_SOURCE_CODE } from '@/utils/i18nEnum'
import { getIssue, getHistory } from '@/api/issue'
import { useFieldSchemaStore } from '@/store/fieldSchema'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import IssueFormSections from '@/components/IssueFormSections.vue'
import IssueRelationPanel from '@/components/IssueRelationPanel.vue'
import StatusTimeline from '@/components/StatusTimeline.vue'
import DynamicFormRenderer from '@/components/dynamic/DynamicFormRenderer.vue'

const props = defineProps({
  // 编辑/查看回显对象（含 tags 逗号字符串 / typeCode / id / customFields）
  initial: { type: Object, default: null },
  /**
   * 表单模式：submit 提交 / edit 编辑 / view 只读查看。
   * 未显式传入时按 initial 有无自动推断，兼容既有调用点。
   */
  mode: {
    type: String,
    default: '',
    validator: (v) => ['', 'submit', 'edit', 'view'].includes(v)
  }
})
const emit = defineEmits(['submit', 'cancel'])

const { t } = useI18n()
const schemaStore = useFieldSchemaStore()

/** 生效模式：显式 mode 优先，其次按 initial 推断 */
const mode = computed(() => {
  if (props.mode) return props.mode
  return props.initial ? 'edit' : 'submit'
})
const isEdit = computed(() => mode.value === 'edit')
const readonly = computed(() => mode.value === 'view')
/** 已存在的问题 id（编辑/查看态才有，新建为 null） */
const issueId = computed(() => (props.initial && props.initial.id) || null)

const sectionsRef = ref(null)
const uploaderRef = ref(null)
/** 各业务区域 DynamicFormRenderer 实例（按 section.code 索引） */
const rendererRefs = {}
function setRendererRef(code, el) {
  if (el) rendererRefs[code] = el
  else delete rendererRefs[code]
}

/**
 * 扁平值模型（单一数据源）。内置字段在此预声明默认键，确保 DynamicFormRenderer
 * 的 v-model 绑定在 schema 异步就绪前就存在；自定义字段键在 schema 就绪后由
 * ensureModelKeys 补齐。提交时按 schema 把内置 / 自定义字段拆分到不同位置。
 */
const model = reactive({
  title: '',
  typeCode: '',
  source: DEFAULT_SOURCE_CODE,
  priority: DEFAULT_PRIORITY,
  severity: 2,
  projectId: null,
  moduleId: null,
  tags: [],
  description: '',
  reproduceSteps: '',
  envOs: '',
  envBrowser: '',
  envAppVersion: '',
  envDevice: ''
})

const schemaSections = computed(() => schemaStore.sections)

/* ---------------- 附件 / 历史（编辑、查看态懒加载） ---------------- */
const localFiles = ref([])
const attachments = ref([])
const attachmentsLoaded = ref(false)
const history = ref([])
const historyLoading = ref(false)
const historyLoaded = ref(false)

/**
 * 单个字段的「已填写」判定：值与 schema 默认值相等（或空 / 空数组）视为未填写。
 *
 * @param {object} field FieldConfigVO
 * @param {*} value 当前值
 * @returns {boolean}
 */
function isFilled(field, value) {
  if (Array.isArray(value)) return value.length > 0
  if (value == null) return false
  if (typeof value === 'string') return value.trim().length > 0
  const def = field && field.defaultValue != null ? field.defaultValue : null
  if (value === def) return false
  if (value === '' && def == null) return false
  return true
}

/**
 * #3.1：各标签「已填写」状态（驱动 IssueFormSections 标签红点），按 section.code 索引。
 * 业务页签：该区域任一字段值与默认值不同即亮；系统页签 attachment：有附件即亮。
 */
const filledTabs = computed(() => {
  const result = {}
  schemaStore.sections.forEach((section) => {
    const filled = (section.fields || []).some(
      (f) => f && isFilled(f, model[f.code])
    )
    result[section.code] = filled
  })
  result.attachment = issueId.value
    ? attachments.value && attachments.value.length > 0
    : localFiles.value && localFiles.value.length > 0
  return result
})

/**
 * schema 就绪后，为 model 补齐 schema 中声明、但 model 尚未存在的字段键
 * （主要是自定义字段），用 schema defaultValue 兜底，避免提交时丢字段。
 */
function ensureModelKeys() {
  schemaStore.sections.forEach((section) => {
    ;(section.fields || []).forEach((f) => {
      if (f && f.code != null && !(f.code in model)) {
        model[f.code] = f.defaultValue != null ? f.defaultValue : null
      }
    })
  })
}

/**
 * 字段默认值（schema 驱动，带少量内置语义兜底）。
 *
 * @param {object} field FieldConfigVO
 * @returns {*}
 */
function defaultForField(field) {
  if (field && field.defaultValue != null) return field.defaultValue
  if (field && field.code === 'tags') return []
  if (field && field.code === 'severity') return 2
  if (field && field.code === 'source') return DEFAULT_SOURCE_CODE
  if (field && field.code === 'priority') return DEFAULT_PRIORITY
  return null
}

/** 回显：内置字段按 code 从 initial 顶层取，自定义字段从 initial.customFields 取 */
function applyInitial() {
  if (!props.initial) return
  const src = props.initial
  model.title = src.title || ''
  // 问题类型改用 typeCode（String）；后端 typeId 已 @Deprecated，优先取 typeCode
  model.typeCode = src.typeCode != null ? src.typeCode : src.typeId != null ? src.typeId : ''
  model.source = src.source || DEFAULT_SOURCE_CODE
  model.priority = src.priority != null ? src.priority : DEFAULT_PRIORITY
  model.severity = src.severity != null ? src.severity : 2
  model.projectId = src.projectId || null
  model.moduleId = src.moduleId || null
  model.tags = Array.isArray(src.tags)
    ? src.tags.slice()
    : src.tags
      ? String(src.tags)
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      : []
  model.description = src.description || ''
  model.reproduceSteps = src.reproduceSteps || ''
  model.envOs = src.envOs || ''
  model.envBrowser = src.envBrowser || ''
  model.envAppVersion = src.envAppVersion || ''
  model.envDevice = src.envDevice || ''
  // 自定义字段：从 customFields 取（按 schema 顺序，仅覆盖已下发的键）
  const cf = src.customFields || {}
  schemaStore.customCodes.forEach((code) => {
    if (code in cf) model[code] = cf[code]
  })
  // 列表行对象自带附件时直接用，否则等切到附件标签再懒加载
  if (Array.isArray(src.attachments)) {
    attachments.value = src.attachments
    attachmentsLoaded.value = true
  }
}

/** DynamicFormRenderer 单字段变更回传：合并进 model（不整体替换，保住响应式引用） */
function onModelPatch(payload) {
  if (payload && typeof payload === 'object') {
    Object.assign(model, payload)
  }
}

function onFilesChange(files) {
  localFiles.value = files || []
}
function onAttachmentUploaded(att) {
  attachments.value = [...attachments.value, att]
}
function onAttachmentRemoved(id) {
  attachments.value = attachments.value.filter((a) => a.id !== id)
}

/* ---------------- 标签切换：懒加载（#3.2 起自由切换，不再离开校验） ---------------- */
function onTabChange(name) {
  if (!issueId.value) return
  if (name === 'attachment') loadAttachments()
  if (name === 'history') loadHistory()
}

async function loadAttachments() {
  if (attachmentsLoaded.value || !issueId.value) return
  attachmentsLoaded.value = true
  try {
    const res = await getIssue(issueId.value)
    attachments.value = (res && res.attachments) || []
  } catch (e) {
    attachments.value = []
  }
}

async function loadHistory() {
  if (historyLoaded.value || !issueId.value) return
  historyLoaded.value = true
  historyLoading.value = true
  try {
    const his = await getHistory(issueId.value, { page: 1, size: 50 })
    history.value = (his && his.list) || []
  } catch (e) {
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

/** 重置表单（父级抽屉 @closed 调用）：遍历 schema 把所有字段复位到默认值 */
function reset() {
  schemaStore.sections.forEach((section) => {
    ;(section.fields || []).forEach((field) => {
      if (field && field.code != null) model[field.code] = defaultForField(field)
    })
  })
  if (uploaderRef.value && uploaderRef.value.clear) {
    uploaderRef.value.clear()
  }
  localFiles.value = []
  attachments.value = []
  attachmentsLoaded.value = false
  history.value = []
  historyLoaded.value = false
  // 清掉各区域校验态并回到首个业务页签
  Object.keys(rendererRefs).forEach((code) => {
    const r = rendererRefs[code]
    if (r && r.clearValidate) r.clearValidate()
  })
  if (sectionsRef.value) {
    const first = schemaStore.sections[0]
    sectionsRef.value.expand(first ? first.code : 'attachment')
  }
}

/**
 * 组装提交数据：内置字段平铺顶层，自定义字段收进 customFields。
 *
 * @returns {object}
 */
function assembleData() {
  const data = {
    title: model.title || '',
    // 问题类型改用 typeCode（String）
    typeCode: model.typeCode || null,
    // 来源留空时不下发字段，由后端 DictService.defaultSourceCode() 填默认值
    source: model.source || null,
    priority: model.priority,
    severity: model.severity,
    projectId: model.projectId || null,
    moduleId: model.moduleId || null,
    tags: Array.isArray(model.tags) ? model.tags.join(',') : '',
    description: model.description || '',
    reproduceSteps: model.reproduceSteps || '',
    envOs: model.envOs || '',
    envBrowser: model.envBrowser || '',
    envAppVersion: model.envAppVersion || '',
    envDevice: model.envDevice || ''
  }
  const customFields = {}
  schemaStore.customCodes.forEach((code) => {
    customFields[code] = model[code] !== undefined ? model[code] : null
  })
  data.customFields = customFields
  return data
}

/**
 * 校验并提交：并发校验每个业务区域 DynamicFormRenderer，全部通过才组装数据；
 * 任一失败则切到首个失败的页签并提示，不发起请求。
 */
async function submit() {
  if (readonly.value) return
  const sections = schemaStore.sections
  const refs = sections.map((s) => rendererRefs[s.code]).filter(Boolean)
  const results = await Promise.all(refs.map((r) => r.validate()))
  const failedIdx = results.findIndex((ok) => !ok)
  if (failedIdx >= 0) {
    const failedSection = sections[failedIdx]
    if (failedSection && sectionsRef.value) sectionsRef.value.expand(failedSection.code)
    // 空表单点提交只会命中此分支，不会触碰后端而弹「系统错误」
    ElMessage.warning(t('issue.tabTip.submitInvalid'))
    return
  }
  const data = assembleData()
  // 编辑/查看态附件走服务端即时上传，不随表单再提交一次
  const files = issueId.value ? [] : localFiles.value
  emit('submit', { data, files })
}

defineExpose({ submit, reset })

onMounted(async () => {
  // schema 异步就绪（带缓存）：业务页签与字段依赖均来自它
  try {
    await schemaStore.loadSchema()
  } catch (e) {
    // schema 拉取失败时降级：仅系统页签可用，业务字段不可填（由后端/网络问题导致）
  }
  ensureModelKeys()
  applyInitial()
})
</script>

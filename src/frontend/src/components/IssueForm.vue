<template>
  <!-- 问题表单（提交 / 编辑 / 查看共用）。
       Phase8 W2 #12：容器由 4 分区折叠改为左侧竖形标签页（IssueFormSections），
       5 个标签：基本信息 / 问题描述 / 附件上传 / 关联信息 / 操作历史。
       - 「基本信息」→ 其他标签前会校验基本信息，不通过则阻止切换；
       - 问题描述改为非必填；所属项目改为必填（#6）；
       - 环境信息随复现步骤并入「问题描述」标签，字段一个不少。
       Phase6 起不再内置提交按钮：由父级 FormDrawer 底部按钮触发 submit()（defineExpose）。 -->
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    :disabled="readonly"
    label-width="96px"
    label-position="right"
  >
    <IssueFormSections
      ref="sectionsRef"
      :before-leave="onBeforeLeaveTab"
      @change="onTabChange"
    >
      <!-- ===== 标签 1：基本信息 ===== -->
      <template #basic>
        <el-form-item :label="t('issue.form.title')" prop="title">
          <el-input
            v-model="model.title"
            :placeholder="t('issue.placeholder.title')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <!-- Phase6：问题类型必选；下拉仅启用项（Q6），编辑回显停用项时追加只读展示项 -->
            <el-form-item :label="t('issue.form.type')" prop="typeId">
              <el-select
                v-model="model.typeId"
                :placeholder="t('issue.placeholder.selectType')"
                style="width: 100%"
              >
                <el-option
                  v-for="opt in typeOptions"
                  :key="opt.id"
                  :label="opt.label"
                  :value="opt.id"
                  :disabled="opt.disabled"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- Phase7 T3：来源（字典 ISSUE_SOURCE，value = item_code，与后端落库口径一致）。
                 新建留空由后端填默认来源；编辑回显停用来源时追加只读展示项。 -->
            <el-form-item :label="t('issue.form.source')" prop="source">
              <el-select
                v-model="model.source"
                :placeholder="t('issue.placeholder.selectSource')"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="opt in sourceSelectOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                  :disabled="opt.disabled"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- Phase7 T3（ARCH 硬要求）：严重等级与优先级同一 el-row 的两个 span=12，
             同为 el-select、同必填星号、同校验提示风格 -->
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('issue.form.severity')" prop="severity">
              <el-select
                v-model="model.severity"
                :placeholder="t('issue.placeholder.selectSeverity')"
                style="width: 100%"
              >
                <el-option
                  v-for="opt in severityOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('issue.form.priority')" prop="priority">
              <el-select
                v-model="model.priority"
                :placeholder="t('issue.placeholder.selectPriority')"
                style="width: 100%"
              >
                <el-option
                  v-for="opt in priorityOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- Phase8 W2 #6：所属项目必填（红星由 rules.projectId.required 渲染），不再可清空 -->
        <el-form-item :label="t('issue.form.project')" prop="projectId">
          <el-select
            v-model="model.projectId"
            :placeholder="t('issue.placeholder.selectProject')"
            filterable
            style="width: 100%"
          >
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('issue.form.module')" prop="moduleId">
          <el-tree-select
            v-model="model.moduleId"
            :data="moduleTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            :render-after-expand="false"
            filterable
            clearable
            :placeholder="t('issue.placeholder.selectModule')"
            style="width: 100%"
          >
            <template #default="{ data }">
              <span>{{ data.pathLabel }}</span>
            </template>
          </el-tree-select>
        </el-form-item>

        <el-form-item :label="t('issue.form.tags')" prop="tags">
          <el-select
            v-model="model.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('issue.placeholder.tags')"
            style="width: 100%"
          >
            <el-option v-for="tg in tagOptions" :key="tg.value" :label="tg.label" :value="tg.value" />
          </el-select>
        </el-form-item>
      </template>

      <!-- ===== 标签 2：问题描述（非必填）+ 复现步骤 + 环境信息 ===== -->
      <template #detail>
        <el-form-item :label="t('issue.form.description')" prop="description">
          <el-input
            v-model="model.description"
            type="textarea"
            :rows="5"
            :placeholder="t('issue.placeholder.description')"
          />
        </el-form-item>
        <el-form-item :label="t('issue.form.steps')" prop="reproduceSteps">
          <el-input
            v-model="model.reproduceSteps"
            type="textarea"
            :rows="3"
            :placeholder="t('issue.placeholder.steps')"
          />
        </el-form-item>

        <el-divider content-position="left">{{ t('issue.form.section.env') }}</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('issue.form.envOs')" prop="envOs">
              <el-input v-model="model.envOs" placeholder="Windows 11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('issue.form.envBrowser')" prop="envBrowser">
              <el-input v-model="model.envBrowser" placeholder="Chrome 120" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('issue.form.envAppVersion')" prop="envAppVersion">
              <el-input v-model="model.envAppVersion" placeholder="v1.2.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('issue.form.envDevice')" prop="envDevice">
              <el-input v-model="model.envDevice" placeholder="iPhone 14" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- ===== 标签 3：附件上传 =====
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

      <!-- ===== 标签 4：关联信息 ===== -->
      <template #relation>
        <IssueRelationPanel
          v-if="issueId"
          :issue-id="issueId"
          :can-edit="isEdit"
        />
        <el-empty v-else :description="t('issue.tabTip.relationPending')" :image-size="72" />
      </template>

      <!-- ===== 标签 5：操作历史 ===== -->
      <template #history>
        <div v-if="issueId" v-loading="historyLoading">
          <StatusTimeline :history="history" />
        </div>
        <el-empty v-else :description="t('issue.tabTip.historyPending')" :image-size="72" />
      </template>
    </IssueFormSections>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  useSeverityOptions,
  usePriorityOptions,
  useDictCodeOptions,
  dictCodeLabelI18n,
  issueTypeLabelI18n,
  DICT_TYPE,
  DEFAULT_PRIORITY,
  DEFAULT_SOURCE_CODE
} from '@/utils/i18nEnum'
import { useIssueTypeStore } from '@/store/issueType'
import { useDictStore } from '@/store/dict'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import { listModuleTree } from '@/api/module'
import { getIssue, getHistory } from '@/api/issue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import IssueFormSections from '@/components/IssueFormSections.vue'
import IssueRelationPanel from '@/components/IssueRelationPanel.vue'
import StatusTimeline from '@/components/StatusTimeline.vue'

const props = defineProps({
  // 编辑/查看回显对象（含 tags 逗号字符串 / typeId / id）
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
const issueTypeStore = useIssueTypeStore()
const dictStore = useDictStore()

/** 生效模式：显式 mode 优先，其次按 initial 推断 */
const mode = computed(() => {
  if (props.mode) return props.mode
  return props.initial ? 'edit' : 'submit'
})
const isEdit = computed(() => mode.value === 'edit')
const readonly = computed(() => mode.value === 'view')
/** 已存在的问题 id（编辑/查看态才有，新建为 null） */
const issueId = computed(() => (props.initial && props.initial.id) || null)

const uploaderRef = ref(null)
const sectionsRef = ref(null)
const formRef = ref(null)
const localFiles = ref([])

const severityOptions = useSeverityOptions()
const priorityOptions = usePriorityOptions()
/** 来源下拉：仅启用项（表单场景，Q6 同款口径） */
const sourceOptions = useDictCodeOptions(DICT_TYPE.ISSUE_SOURCE, false)

const model = reactive({
  title: '',
  typeId: null,
  // Phase7 T3：新建默认来源 SYSTEM、默认优先级 中（与后端兜底口径一致）
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

const tagOptions = ref([])
const projectOptions = ref([])
const moduleTree = ref([])
const moduleLoading = ref(false)
const lastProject = ref(null)
/** 编辑回显补充项：当前值为停用类型时追加的只读展示项 */
const extraTypeOption = ref(null)
/** 编辑回显补充项：当前来源已停用/已删除时追加的只读展示项（code + label） */
const extraSourceOption = ref(null)

/* ---------------- 附件 / 历史（编辑、查看态懒加载） ---------------- */
const attachments = ref([])
const attachmentsLoaded = ref(false)
const history = ref([])
const historyLoading = ref(false)
const historyLoaded = ref(false)

/**
 * 来源下拉最终数据源：启用项 + （编辑态）当前停用来源只读项。
 * 保证编辑旧数据时不会因来源被停用而把已有值清空。
 */
const sourceSelectOptions = computed(() => {
  const base = (sourceOptions.value || []).map((o) => ({
    value: o.value,
    label: o.label,
    disabled: false
  }))
  const extra = extraSourceOption.value
  if (extra && !base.some((o) => o.value === extra.value)) {
    base.push({ value: extra.value, label: extra.label, disabled: true })
  }
  return base
})

/** 类型下拉：启用项 + （编辑态）当前停用值只读展示项（Q6） */
const typeOptions = computed(() => {
  const base = (issueTypeStore.options || []).map((o) => ({
    id: o.id,
    label: o.name,
    disabled: false
  }))
  if (
    extraTypeOption.value &&
    !base.some((o) => o.id === extraTypeOption.value.id)
  ) {
    base.push({
      id: extraTypeOption.value.id,
      label: issueTypeLabelI18n(extraTypeOption.value),
      disabled: true
    })
  }
  return base
})

/**
 * 校验规则（i18n，语言切换时 computed 重建）。
 * Phase8 W2：#6 所属项目必填；#12 问题描述取消必填。
 */
const rules = computed(() => ({
  title: [{ required: true, message: t('issue.rules.titleRequired'), trigger: 'blur' }],
  typeId: [{ required: true, message: t('issue.rules.typeRequired'), trigger: 'change' }],
  severity: [{ required: true, message: t('issue.rules.severityRequired'), trigger: 'change' }],
  // Phase7 T3：优先级与严重等级同为必选，校验强度保持一致
  priority: [{ required: true, message: t('issue.rules.priorityRequired'), trigger: 'change' }],
  source: [{ required: true, message: t('issue.rules.sourceRequired'), trigger: 'change' }],
  projectId: [{ required: true, message: t('issue.rules.projectRequired'), trigger: 'change' }]
}))

/** 「基本信息」标签下参与校验的字段（切换标签前逐项校验） */
const BASIC_FIELDS = ['title', 'typeId', 'source', 'severity', 'priority', 'projectId']

/** 校验字段 → 所属标签映射（校验失败自动切标签 + 滚动定位） */
const SECTION_BY_FIELD = {
  title: 'basic',
  typeId: 'basic',
  source: 'basic',
  priority: 'basic',
  severity: 'basic',
  projectId: 'basic',
  moduleId: 'basic',
  tags: 'basic',
  description: 'detail',
  reproduceSteps: 'detail',
  envOs: 'detail',
  envBrowser: 'detail',
  envAppVersion: 'detail',
  envDevice: 'detail'
}

function applyInitial() {
  if (!props.initial) return
  const src = props.initial
  model.title = src.title || ''
  model.typeId = src.typeId ?? null
  model.source = src.source || DEFAULT_SOURCE_CODE
  model.priority = src.priority ?? DEFAULT_PRIORITY
  model.severity = src.severity ?? 2
  model.projectId = src.projectId || null
  model.moduleId = src.moduleId || null
  lastProject.value = model.projectId
  if (model.projectId) loadModules(model.projectId) // 编辑回显时保留 moduleId
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
  // 列表行对象自带附件时直接用，否则等切到附件标签再懒加载
  if (Array.isArray(src.attachments)) {
    attachments.value = src.attachments
    attachmentsLoaded.value = true
  }
}

/** 编辑态：当前 typeId 不在启用项内（已停用）时，从全量下拉补一条只读展示项 */
async function resolveExtraTypeOption() {
  extraTypeOption.value = null
  if (!props.initial || model.typeId == null) return
  const enabledHit = (issueTypeStore.options || []).some((o) => o.id === model.typeId)
  if (enabledHit) return
  try {
    const all = await issueTypeStore.fetchAllOptions()
    extraTypeOption.value = (all || []).find((o) => o.id === model.typeId) || null
  } catch (e) {
    extraTypeOption.value = null
  }
}

/**
 * 编辑态：当前 source 不在启用项内（已停用/已删除）时补一条只读展示项，
 * 名称优先取后端返回的 sourceDesc，再退回字典缓存 / code 原值。
 */
async function resolveExtraSourceOption() {
  extraSourceOption.value = null
  if (!props.initial || !model.source) return
  const enabledHit = (sourceOptions.value || []).some((o) => o.value === model.source)
  if (enabledHit) return
  try {
    await dictStore.fetchAllOptions(DICT_TYPE.ISSUE_SOURCE)
  } catch (e) {
    // 全量字典拉取失败时仍用 sourceDesc 兜底展示
  }
  const fallback = (props.initial && props.initial.sourceDesc) || model.source
  extraSourceOption.value = {
    value: model.source,
    label: dictCodeLabelI18n(DICT_TYPE.ISSUE_SOURCE, model.source, fallback)
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

/* ---------------- 标签切换：校验 + 懒加载 ---------------- */
/**
 * 离开「基本信息」前校验必填项，不通过则阻止切换并提示。
 * @returns {Promise<boolean>} false 阻止切换
 */
async function onBeforeLeaveTab() {
  if (readonly.value || !formRef.value) return true
  try {
    await formRef.value.validateField(BASIC_FIELDS)
    return true
  } catch (e) {
    ElMessage.warning(t('issue.tabTip.basicInvalid'))
    return false
  }
}

/** 切到附件 / 操作历史标签时按需拉取数据（仅编辑、查看态） */
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

/* ---------------- 模块树（R2：随项目联动） ---------------- */
function annotateModulePaths(nodes, parentPath) {
  ;(nodes || []).forEach((n) => {
    n.pathLabel = parentPath ? `${parentPath} > ${n.name}` : n.name
    annotateModulePaths(n.children, n.pathLabel)
  })
}
async function loadModules(projectId) {
  if (!projectId) {
    moduleTree.value = []
    return
  }
  moduleLoading.value = true
  try {
    const data = await listModuleTree(projectId)
    const tree = Array.isArray(data) ? data : []
    annotateModulePaths(tree, '')
    moduleTree.value = tree
  } catch (e) {
    moduleTree.value = []
  } finally {
    moduleLoading.value = false
  }
}
// 切换项目 → 清空已选模块并重新加载对应模块树
watch(
  () => model.projectId,
  (val) => {
    if (val === lastProject.value) return
    lastProject.value = val
    model.moduleId = null
    loadModules(val)
  }
)

/** 重置表单（父级抽屉 @closed 调用） */
function reset() {
  Object.assign(model, {
    title: '',
    typeId: null,
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
  if (uploaderRef.value && uploaderRef.value.clear) {
    uploaderRef.value.clear()
  }
  localFiles.value = []
  extraTypeOption.value = null
  extraSourceOption.value = null
  attachments.value = []
  attachmentsLoaded.value = false
  history.value = []
  historyLoaded.value = false
  if (sectionsRef.value) sectionsRef.value.expand('basic')
  if (formRef.value) formRef.value.clearValidate()
}

/**
 * 校验并提交：失败时自动切到首个错误字段所在标签并滚动定位；
 * 成功时 emit('submit', { data, files }) 交由父级发请求。
 */
function submit() {
  if (!formRef.value || readonly.value) return
  formRef.value.validate((valid, fields) => {
    if (!valid) {
      const firstField = fields ? Object.keys(fields)[0] : null
      if (firstField) {
        const section = SECTION_BY_FIELD[firstField]
        if (section && sectionsRef.value) sectionsRef.value.expand(section)
        nextTick(() => {
          if (formRef.value && formRef.value.scrollToField) {
            formRef.value.scrollToField(firstField)
          }
        })
      }
      return
    }
    const data = {
      title: model.title,
      typeId: model.typeId,
      // 来源留空时不下发字段，由后端 DictService.defaultSourceCode() 填默认值
      source: model.source || null,
      priority: model.priority,
      severity: model.severity,
      tags: model.tags.join(','),
      description: model.description,
      reproduceSteps: model.reproduceSteps,
      envOs: model.envOs,
      envBrowser: model.envBrowser,
      envAppVersion: model.envAppVersion,
      envDevice: model.envDevice,
      projectId: model.projectId,
      moduleId: model.moduleId || null
    }
    // 编辑/查看态附件走服务端即时上传，不随表单再提交一次
    const files = issueId.value ? [] : localFiles.value
    emit('submit', { data, files })
  })
}

defineExpose({ submit, reset })

onMounted(async () => {
  applyInitial()
  try {
    await issueTypeStore.fetchOptions()
  } catch (e) {
    // 下拉加载失败不阻塞表单其余部分
  }
  await resolveExtraTypeOption()
  await resolveExtraSourceOption()
  try {
    const tags = await listTags()
    tagOptions.value = (tags || []).map((tg) => ({ label: tg.name, value: tg.name }))
  } catch (e) {
    tagOptions.value = []
  }
  try {
    const projects = await listProjectOptions()
    projectOptions.value = projects || []
  } catch (e) {
    projectOptions.value = []
  }
})
</script>

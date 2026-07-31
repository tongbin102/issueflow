<template>
  <!-- T4：问题表单（新建/编辑共用），4 分区折叠 + 问题类型必选 + 校验失败自动展开并滚动定位。
       Phase6 起不再内置提交按钮：由父级 FormDrawer 底部按钮触发 submit()（defineExpose）。 -->
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    label-width="96px"
    label-position="right"
  >
    <IssueFormSections ref="sectionsRef" :show-attachment="!isEdit" :mode="isEdit ? 'edit' : 'create'">
      <!-- ===== 分区 1：基本信息 ===== -->
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
        </el-row>

        <el-form-item :label="t('issue.form.project')" prop="projectId">
          <el-select
            v-model="model.projectId"
            :placeholder="t('issue.placeholder.selectProject')"
            clearable
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

      <!-- ===== 分区 2：详细描述 ===== -->
      <template #detail>
        <el-form-item :label="t('issue.form.description')" prop="description">
          <el-input
            v-model="model.description"
            type="textarea"
            :rows="4"
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
      </template>

      <!-- ===== 分区 3：环境信息 ===== -->
      <template #env>
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

      <!-- ===== 分区 4：附件（仅新建）===== -->
      <template #attachment>
        <el-form-item :label="t('issue.form.attachment')">
          <AttachmentUploader ref="uploaderRef" @change="onFilesChange" />
        </el-form-item>
      </template>
    </IssueFormSections>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeverityOptions, issueTypeLabelI18n } from '@/utils/i18nEnum'
import { useIssueTypeStore } from '@/store/issueType'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import { listModuleTree } from '@/api/module'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import IssueFormSections from '@/components/IssueFormSections.vue'

const props = defineProps({
  // 编辑回显对象（含 tags 逗号字符串 / typeId）
  initial: { type: Object, default: null }
})
const emit = defineEmits(['submit', 'cancel'])

const { t } = useI18n()
const issueTypeStore = useIssueTypeStore()

const isEdit = computed(() => !!props.initial)
const uploaderRef = ref(null)
const sectionsRef = ref(null)
const formRef = ref(null)
const localFiles = ref([])

const severityOptions = useSeverityOptions()

const model = reactive({
  title: '',
  typeId: null,
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

/** 校验规则（i18n，语言切换时 computed 重建） */
const rules = computed(() => ({
  title: [{ required: true, message: t('issue.rules.titleRequired'), trigger: 'blur' }],
  typeId: [{ required: true, message: t('issue.rules.typeRequired'), trigger: 'change' }],
  severity: [{ required: true, message: t('issue.rules.severityRequired'), trigger: 'change' }],
  description: [{ required: true, message: t('issue.rules.descriptionRequired'), trigger: 'blur' }]
}))

/** 校验字段 → 所属折叠分区映射（校验失败自动展开 + 滚动定位） */
const SECTION_BY_FIELD = {
  title: 'basic',
  typeId: 'basic',
  severity: 'basic',
  projectId: 'basic',
  moduleId: 'basic',
  tags: 'basic',
  description: 'detail',
  reproduceSteps: 'detail',
  envOs: 'env',
  envBrowser: 'env',
  envAppVersion: 'env',
  envDevice: 'env'
}

function applyInitial() {
  if (!props.initial) return
  const src = props.initial
  model.title = src.title || ''
  model.typeId = src.typeId ?? null
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
}

/** 编辑态：当前 typeId 不在启用项内（已停用）时，从全量下拉补一条只读展示项 */
async function resolveExtraTypeOption() {
  extraTypeOption.value = null
  if (!isEdit.value || model.typeId == null) return
  const enabledHit = (issueTypeStore.options || []).some((o) => o.id === model.typeId)
  if (enabledHit) return
  try {
    const all = await issueTypeStore.fetchAllOptions()
    extraTypeOption.value = (all || []).find((o) => o.id === model.typeId) || null
  } catch (e) {
    extraTypeOption.value = null
  }
}

function onFilesChange(files) {
  localFiles.value = files || []
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
  if (formRef.value) formRef.value.clearValidate()
}

/**
 * 校验并提交：失败时自动展开首个错误字段所在分区并滚动定位；
 * 成功时 emit('submit', { data, files }) 交由父级发请求。
 */
function submit() {
  if (!formRef.value) return
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
      severity: model.severity,
      tags: model.tags.join(','),
      description: model.description,
      reproduceSteps: model.reproduceSteps,
      envOs: model.envOs,
      envBrowser: model.envBrowser,
      envAppVersion: model.envAppVersion,
      envDevice: model.envDevice,
      projectId: model.projectId || null,
      moduleId: model.moduleId || null
    }
    const files = isEdit.value ? [] : localFiles.value
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

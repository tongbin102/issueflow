<template>
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    label-width="96px"
    label-position="right"
  >
    <el-form-item label="标题" prop="title">
      <el-input v-model="model.title" placeholder="请输入问题标题" maxlength="200" show-word-limit />
    </el-form-item>

    <el-form-item label="严重等级" prop="severity">
      <el-select v-model="model.severity" placeholder="选择严重等级" style="width: 200px">
        <el-option
          v-for="opt in SEVERITY_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="关联项目">
      <el-select
        v-model="model.projectId"
        placeholder="选择项目（可选）"
        clearable
        filterable
        style="width: 100%"
      >
        <el-option
          v-for="p in projectOptions"
          :key="p.id"
          :label="p.status === 1 ? p.name : p.name + '（停用）'"
          :value="p.id"
          :disabled="p.status !== 1"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="分类标签">
      <el-select
        v-model="model.tags"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入标签"
        style="width: 100%"
      >
        <el-option
          v-for="t in tagOptions"
          :key="t.value"
          :label="t.label"
          :value="t.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="详细描述" prop="description">
      <el-input
        v-model="model.description"
        type="textarea"
        :rows="4"
        placeholder="请描述问题的现象与影响"
      />
    </el-form-item>

    <el-form-item label="复现步骤">
      <el-input
        v-model="model.reproduceSteps"
        type="textarea"
        :rows="3"
        placeholder="1. ... 2. ... 3. ..."
      />
    </el-form-item>

    <el-divider content-position="left">环境信息</el-divider>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="操作系统">
          <el-input v-model="model.envOs" placeholder="如 Windows 11" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="浏览器">
          <el-input v-model="model.envBrowser" placeholder="如 Chrome 120" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="应用版本">
          <el-input v-model="model.envAppVersion" placeholder="如 v1.2.0" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="设备型号">
          <el-input v-model="model.envDevice" placeholder="如 iPhone 14" />
        </el-form-item>
      </el-col>
    </el-row>

    <el-form-item v-if="!isEdit" label="附件">
      <AttachmentUploader ref="uploaderRef" @change="onFilesChange" />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="submitting" @click="onSubmit">{{ submitText }}</el-button>
      <el-button @click="onReset" v-if="!isEdit">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { SEVERITY_OPTIONS } from '@/utils/format'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import AttachmentUploader from '@/components/AttachmentUploader.vue'

const props = defineProps({
  // 编辑回显对象（含 tags 逗号字符串）
  initial: { type: Object, default: null }
})
const emit = defineEmits(['submit', 'cancel'])

const isEdit = computed(() => !!props.initial)
const submitText = computed(() => (isEdit.value ? '保存' : '提交'))
const submitting = ref(false)
const uploaderRef = ref(null)
const localFiles = ref([])

const model = reactive({
  title: '',
  severity: 2,
  projectId: null,
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

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重等级', trigger: 'change' }],
  description: [{ required: true, message: '请输入详细描述', trigger: 'blur' }]
}

function applyInitial() {
  if (!props.initial) return
  const src = props.initial
  model.title = src.title || ''
  model.severity = src.severity ?? 2
  model.projectId = src.projectId || null
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

function onFilesChange(files) {
  localFiles.value = files || []
}

function onReset() {
  Object.assign(model, {
    title: '',
    severity: 2,
    projectId: null,
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
  emit('cancel')
}

function onSubmit() {
  if (!formRef.value) return
  formRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const data = {
      title: model.title,
      severity: model.severity,
      tags: model.tags.join(','),
      description: model.description,
      reproduceSteps: model.reproduceSteps,
      envOs: model.envOs,
      envBrowser: model.envBrowser,
      envAppVersion: model.envAppVersion,
      envDevice: model.envDevice,
      projectId: model.projectId || null
    }
    const files = isEdit.value ? [] : localFiles.value
    emit('submit', { data, files })
    submitting.value = false
  })
}

const formRef = ref(null)

onMounted(async () => {
  applyInitial()
  try {
    const tags = await listTags()
    tagOptions.value = (tags || []).map((t) => ({
      label: t.name,
      value: t.name
    }))
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

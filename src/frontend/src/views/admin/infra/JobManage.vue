<template>
  <!-- Phase7 T7：基础设施 > 定时任务
       列表 + 新建/编辑抽屉(FormDrawer) + 启停 + 立即执行 + 执行日志抽屉。
       安全约束：执行目标只能从 GET /api/admin/jobs/options 白名单中选，不接受手填类名。 -->
  <div class="job-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('infra.job.title') }}</span>
          <div class="head__actions">
            <el-button :icon="Refresh" @click="fetchData">{{ t('common.action.refresh') }}</el-button>
            <el-button v-perm="'job:create'" type="primary" :icon="Plus" @click="openCreate">
              {{ t('infra.job.create') }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item :label="t('infra.job.filter.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('common.placeholder.search')"
            clearable
            class="filter-input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item :label="t('infra.job.filter.status')">
          <el-select
            v-model="query.status"
            :placeholder="t('common.status.all')"
            clearable
            class="filter-select"
          >
            <el-option :label="t('infra.job.state.running')" :value="1" />
            <el-option :label="t('infra.job.state.paused')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">
            {{ t('common.action.search') }}
          </el-button>
          <el-button @click="onReset">{{ t('common.action.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-wrap">
        <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
          <el-table-column
            prop="taskName"
            :label="t('infra.job.col.name')"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="taskGroup"
            :label="t('infra.job.col.group')"
            width="110"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.taskGroup || '-' }}</template>
          </el-table-column>
          <el-table-column
            :label="t('infra.job.col.jobKey')"
            min-width="180"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span>{{ row.jobName || row.jobKey || '-' }}</span>
              <div v-if="row.jobName" class="sub-text">{{ row.jobKey }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="cron" :label="t('infra.job.col.cron')" width="150">
            <template #default="{ row }">
              <span class="code-text">{{ row.cron || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.job.col.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
                {{ row.status === 1 ? t('infra.job.state.running') : t('infra.job.state.paused') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.job.col.lastExecTime')" width="170">
            <template #default="{ row }">{{ row.lastExecTime || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('infra.job.col.lastResult')" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="resultTagType(row.lastExecResult)" size="small" effect="plain">
                {{ resultLabel(row.lastExecResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.job.col.cost')" width="100" align="right">
            <template #default="{ row }">
              {{ row.lastCostMs === null || row.lastCostMs === undefined ? '-' : formatDuration(row.lastCostMs) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.job.col.nextExecTime')" width="170">
            <template #default="{ row }">{{ row.nextExecTime || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('infra.job.col.actions')" width="300" fixed="right">
            <template #default="{ row }">
              <el-button
                v-perm="'job:run'"
                link
                type="primary"
                size="small"
                @click="onRun(row)"
              >
                {{ t('infra.job.action.run') }}
              </el-button>
              <el-button
                v-perm="'job:update'"
                link
                :type="row.status === 1 ? 'warning' : 'success'"
                size="small"
                @click="onToggle(row)"
              >
                {{ row.status === 1 ? t('infra.job.action.pause') : t('infra.job.action.resume') }}
              </el-button>
              <el-button link type="primary" size="small" @click="openLogs(row)">
                {{ t('infra.job.action.logs') }}
              </el-button>
              <el-button
                v-perm="'job:update'"
                link
                type="primary"
                size="small"
                @click="openEdit(row)"
              >
                {{ t('common.action.edit') }}
              </el-button>
              <el-button
                v-perm="'job:delete'"
                link
                type="danger"
                size="small"
                @click="onDelete(row)"
              >
                {{ t('common.action.delete') }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="t('infra.job.empty')" :image-size="60" />
          </template>
        </el-table>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          :layout="pagerLayout"
          background
          @current-change="fetchData"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增 / 编辑抽屉 -->
    <FormDrawer
      v-model="drawerVisible"
      :title="isEdit ? t('infra.job.drawer.editTitle') : t('infra.job.drawer.createTitle')"
      size="md"
      :loading="saving"
      @confirm="onSave"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" label-position="right">
        <el-form-item :label="t('infra.job.form.name')" prop="taskName">
          <el-input
            v-model="form.taskName"
            :placeholder="t('infra.job.placeholder.name')"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('infra.job.form.group')" prop="taskGroup">
          <el-input
            v-model="form.taskGroup"
            :placeholder="t('infra.job.placeholder.group')"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item :label="t('infra.job.form.jobKey')" prop="jobKey">
          <el-select
            v-model="form.jobKey"
            :placeholder="t('infra.job.placeholder.jobKey')"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="option in jobOptions"
              :key="option.jobKey"
              :label="option.displayName || option.jobKey"
              :value="option.jobKey"
            >
              <span>{{ option.displayName || option.jobKey }}</span>
              <span class="option-key">{{ option.jobKey }}</span>
            </el-option>
          </el-select>
          <div class="form-tip">{{ t('infra.job.whitelistTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('infra.job.form.cron')" prop="cron">
          <el-input
            v-model="form.cron"
            :placeholder="t('infra.job.placeholder.cron')"
            maxlength="100"
          />
          <div class="form-tip">{{ t('infra.job.cronTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('infra.job.form.params')" prop="params">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="3"
            :placeholder="t('infra.job.placeholder.params')"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('infra.job.form.status')" prop="status">
          <el-switch
            v-model="form.enabled"
            :active-text="t('infra.job.state.running')"
            :inactive-text="t('infra.job.state.paused')"
          />
        </el-form-item>
        <el-form-item :label="t('infra.job.form.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            :placeholder="t('infra.job.placeholder.description')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </FormDrawer>

    <!-- 执行日志抽屉（只读，自带分页） -->
    <FormDrawer
      v-model="logVisible"
      :title="t('infra.job.drawer.logTitle', { name: logTaskName })"
      size="lg"
      @closed="resetLogs"
    >
      <div v-loading="logLoading" class="log-body">
        <el-table :data="logList" border stripe size="small" style="width: 100%">
          <el-table-column prop="startTime" :label="t('infra.job.log.startTime')" width="170">
            <template #default="{ row }">{{ row.startTime || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('infra.job.log.cost')" width="100" align="right">
            <template #default="{ row }">{{ formatDuration(row.costMs) }}</template>
          </el-table-column>
          <el-table-column :label="t('infra.job.log.trigger')" width="110" align="center">
            <template #default="{ row }">{{ triggerLabel(row.triggerType) }}</template>
          </el-table-column>
          <el-table-column :label="t('infra.job.log.result')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="plain">
                {{ row.success ? t('infra.job.result.success') : t('infra.job.result.fail') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('infra.job.log.message')" min-width="240">
            <template #default="{ row }">
              <div class="log-msg">{{ row.message || '-' }}</div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="t('infra.job.log.empty')" :image-size="60" />
          </template>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="logPage"
            :page-size="logSize"
            :total="logTotal"
            layout="total, prev, pager, next"
            background
            small
            @current-change="fetchLogs"
          />
        </div>
      </div>
      <template #footer>
        <div class="log-footer">
          <el-button @click="logVisible = false">{{ t('common.action.close') }}</el-button>
        </div>
      </template>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import {
  pageJobs,
  getJobOptions,
  createJob,
  updateJob,
  deleteJob,
  toggleJobStatus,
  runJobOnce,
  pageJobLogs
} from '@/api/job'
import { formatDuration } from '@/utils/format'
import { useAppStore } from '@/store/app'

const { t } = useI18n()
const appStore = useAppStore()

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const query = reactive({ keyword: '', status: null })

/** 后端 jobRegistry 白名单 */
const jobOptions = ref([])

const drawerVisible = ref(false)
const formRef = ref(null)
const editingId = ref(null)
const isEdit = computed(() => editingId.value != null)

const form = reactive({
  taskName: '',
  taskGroup: 'default',
  jobKey: '',
  cron: '',
  params: '',
  enabled: true,
  description: ''
})

/** 执行日志抽屉状态 */
const logVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(10)
const logTaskId = ref(null)
const logTaskName = ref('')

const pagerLayout = computed(() =>
  appStore.isMobile ? 'total, prev, next' : 'total, sizes, prev, pager, next, jumper'
)

const rules = computed(() => ({
  taskName: [{ required: true, message: t('infra.job.rules.nameRequired'), trigger: 'blur' }],
  jobKey: [{ required: true, message: t('infra.job.rules.jobKeyRequired'), trigger: 'change' }],
  cron: [
    { required: true, message: t('infra.job.rules.cronRequired'), trigger: 'blur' },
    { validator: validateCron, trigger: 'blur' }
  ]
}))

/**
 * cron 段数校验（Spring CronExpression 为 6 段：秒 分 时 日 月 周）。
 * 精确语义仍由后端 CronUtils.isValid 兜底，这里只做低成本前置拦截。
 */
function validateCron(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  const parts = String(value).trim().split(/\s+/)
  if (parts.length !== 6) {
    callback(new Error(t('infra.job.rules.cronPattern')))
    return
  }
  callback()
}

/**
 * 上次执行结果 → el-tag 类型。
 * @param {number|null} result 1 成功 / 0 失败 / null 未执行
 */
function resultTagType(result) {
  if (result === 1) return 'success'
  if (result === 0) return 'danger'
  return 'info'
}

/**
 * 上次执行结果 → 文案。
 * @param {number|null} result 1 成功 / 0 失败 / null 未执行
 */
function resultLabel(result) {
  if (result === 1) return t('infra.job.result.success')
  if (result === 0) return t('infra.job.result.fail')
  return t('infra.job.result.none')
}

/**
 * 触发方式 → 文案（未知值回退原值）。
 * @param {string} type CRON / MANUAL
 */
function triggerLabel(type) {
  if (type === 'CRON') return t('infra.job.trigger.CRON')
  if (type === 'MANUAL') return t('infra.job.trigger.MANUAL')
  return type || '-'
}

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (query.keyword) params.keyword = query.keyword.trim()
    if (query.status === 0 || query.status === 1) params.status = query.status
    const data = (await pageJobs(params)) || {}
    list.value = data.list || []
    total.value = Number(data.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    jobOptions.value = (await getJobOptions()) || []
  } catch (e) {
    jobOptions.value = []
  }
}

function onSearch() {
  page.value = 1
  fetchData()
}

function onSizeChange() {
  page.value = 1
  fetchData()
}

function onReset() {
  query.keyword = ''
  query.status = null
  page.value = 1
  fetchData()
}

function openCreate() {
  editingId.value = null
  drawerVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.taskName = row.taskName || ''
  form.taskGroup = row.taskGroup || 'default'
  form.jobKey = row.jobKey || ''
  form.cron = row.cron || ''
  form.params = row.params || ''
  form.enabled = row.status === 1
  form.description = row.description || ''
  drawerVisible.value = true
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    taskName: '',
    taskGroup: 'default',
    jobKey: '',
    cron: '',
    params: '',
    enabled: true,
    description: ''
  })
  if (formRef.value) formRef.value.clearValidate()
}

function onSave() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        taskName: form.taskName.trim(),
        taskGroup: (form.taskGroup || '').trim() || 'default',
        jobKey: form.jobKey,
        cron: form.cron.trim(),
        params: form.params || '',
        status: form.enabled ? 1 : 0,
        description: form.description || ''
      }
      if (isEdit.value) {
        await updateJob(editingId.value, payload)
        ElMessage.success(t('infra.job.msg.updateSuccess'))
      } else {
        await createJob(payload)
        ElMessage.success(t('infra.job.msg.createSuccess'))
      }
      drawerVisible.value = false
      fetchData()
    } catch (e) {
      // cron 非法 / jobKey 不在白名单等错误由 request 拦截器提示
    } finally {
      saving.value = false
    }
  })
}

async function onToggle(row) {
  const next = row.status !== 1
  try {
    await toggleJobStatus(row.id, next)
    ElMessage.success(
      next ? t('infra.job.msg.resumeSuccess') : t('infra.job.msg.pauseSuccess')
    )
    fetchData()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

function onRun(row) {
  ElMessageBox.confirm(
    t('infra.job.msg.runConfirm', { name: row.taskName }),
    t('common.msg.tip'),
    { type: 'info' }
  )
    .then(async () => {
      try {
        await runJobOnce(row.id)
        ElMessage.success(t('infra.job.msg.runSuccess'))
        fetchData()
      } catch (e) {
        // 并发互斥（任务执行中）等错误由 request 拦截器提示
      }
    })
    .catch(() => {})
}

function onDelete(row) {
  ElMessageBox.confirm(
    t('infra.job.msg.deleteConfirm', { name: row.taskName }),
    t('common.msg.warning'),
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteJob(row.id)
        ElMessage.success(t('infra.job.msg.deleteSuccess'))
        if (list.value.length === 1 && page.value > 1) page.value -= 1
        fetchData()
      } catch (e) {
        // 错误提示由 request 拦截器统一处理
      }
    })
    .catch(() => {})
}

function openLogs(row) {
  logTaskId.value = row.id
  logTaskName.value = row.taskName || ''
  logPage.value = 1
  logVisible.value = true
  fetchLogs()
}

async function fetchLogs() {
  if (logTaskId.value === null) return
  logLoading.value = true
  try {
    const data =
      (await pageJobLogs(logTaskId.value, { page: logPage.value, size: logSize.value })) || {}
    logList.value = data.list || []
    logTotal.value = Number(data.total || 0)
  } catch (e) {
    logList.value = []
    logTotal.value = 0
  } finally {
    logLoading.value = false
  }
}

function resetLogs() {
  logTaskId.value = null
  logTaskName.value = ''
  logList.value = []
  logTotal.value = 0
  logPage.value = 1
}

onMounted(() => {
  loadOptions()
  fetchData()
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.head__actions {
  display: flex;
  gap: 8px;
}

.filter-form {
  margin-bottom: 4px;
}

.filter-input {
  width: 220px;
}

.filter-select {
  width: 140px;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.sub-text {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.code-text {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  background: var(--if-code-bg, var(--el-fill-color-light));
  padding: 2px 6px;
  border-radius: 4px;
}

.option-key {
  float: right;
  color: var(--text-secondary);
  font-size: 12px;
}

.form-tip {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.log-body {
  min-height: 200px;
}

.log-msg {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  max-height: 120px;
  overflow: auto;
}

.log-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .head__actions {
    width: 100%;
  }
  .filter-input,
  .filter-select {
    width: 100%;
  }
  .filter-form :deep(.el-form-item) {
    display: block;
    margin-right: 0;
  }
  .pager {
    justify-content: center;
  }
}
</style>

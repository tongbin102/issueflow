<template>
  <div class="issue-table">
    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item :label="t('common.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="t('common.status.all')"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <!-- Phase6：问题类型筛选（全量含停用项，停用项追加「（已停用）」后缀，Q6） -->
        <el-form-item :label="t('issue.list.col.type')">
          <el-select
            v-model="filters.typeId"
            :placeholder="t('common.status.all')"
            clearable
            filterable
            style="width: 150px"
          >
            <el-option
              v-for="tp in typeFilterOptions"
              :key="tp.id"
              :label="tp.label"
              :value="tp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('issue.form.severity')">
          <el-select
            v-model="filters.severity"
            :placeholder="t('common.status.all')"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="s in severityOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('issue.form.project')">
          <el-select
            v-model="filters.projectId"
            :placeholder="t('common.status.all')"
            clearable
            filterable
            style="width: 160px"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.status === 1 ? p.name : p.name + t('issue.filter.typeDisabledSuffix')"
              :value="p.id"
              :disabled="p.status !== 1"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('issue.form.tags')">
          <el-select
            v-model="filters.tags"
            multiple
            collapse-tags
            :placeholder="t('common.status.all')"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="tg in tagOptions"
              :key="tg.value"
              :label="tg.label"
              :value="tg.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('issue.form.envAppVersion')">
          <el-input
            v-model="filters.version"
            placeholder="v1.2.0"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item :label="t('common.field.keyword')">
          <el-input
            v-model="filters.keyword"
            :placeholder="t('issue.list.filter.keyword')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('common.field.dateRange')">
          <el-date-picker
            v-model="timeRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="-"
            :start-placeholder="t('common.field.startDate')"
            :end-placeholder="t('common.field.endDate')"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">{{ t('common.action.search') }}</el-button>
          <el-button @click="onResetFilter">{{ t('common.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
      <el-table-column prop="issueNo" :label="t('issue.list.col.issueNo')" width="150" />
      <el-table-column prop="title" :label="t('issue.list.col.title')" min-width="200" show-overflow-tooltip />
      <!-- Phase6：类型列（停用类型仍正常回显名称） -->
      <el-table-column :label="t('issue.list.col.type')" width="110" align="center">
        <template #default="{ row }">
          <span>{{ row.typeName || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('issue.list.col.severity')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)" effect="light">
            {{ severityLabelI18n(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('issue.list.col.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="light">
            {{ statusLabelI18n(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="projectName" :label="t('issue.list.col.project')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="reporterName" :label="t('issue.list.col.reporter')" width="110" show-overflow-tooltip />
      <el-table-column prop="assigneeName" :label="t('issue.list.col.assignee')" width="110" show-overflow-tooltip />
      <el-table-column :label="t('issue.list.col.createdAt')" width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('issue.list.col.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="emit('view', row)">{{
            t('common.action.view')
          }}</el-button>
          <el-button
            v-if="canEdit(row)"
            link
            type="primary"
            size="small"
            @click="emit('edit', row)"
            >{{ t('common.action.edit') }}</el-button
          >
          <el-button link type="warning" size="small" @click="emit('view', row)">{{
            t('issue.action.flow')
          }}</el-button>
          <el-button
            v-if="canDelete(row)"
            link
            type="danger"
            size="small"
            @click="onDelete(row)"
            >{{ t('common.action.delete') }}</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="fetchData"
        @size-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { statusTagType, severityTagType, formatDate } from '@/utils/format'
import {
  statusLabelI18n,
  severityLabelI18n,
  useStatusOptions,
  useSeverityOptions,
  issueTypeLabelI18n
} from '@/utils/i18nEnum'
import { pageIssues, deleteIssue } from '@/api/issue'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import { useUserStore } from '@/store/user'
import { useIssueTypeStore } from '@/store/issueType'

const props = defineProps({
  scope: { type: String, default: 'all' }, // 'mine' | 'all'
  filters: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['view', 'edit'])

const { t } = useI18n()
const userStore = useUserStore()
const issueTypeStore = useIssueTypeStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const statusOptions = useStatusOptions()
const severityOptions = useSeverityOptions()

const filters = reactive({
  status: props.filters.status ?? '',
  typeId: props.filters.typeId ?? null,
  severity: props.filters.severity ?? '',
  projectId: props.filters.projectId ?? '',
  tags: props.filters.tags || [],
  version: props.filters.version || '',
  keyword: props.filters.keyword || '',
  startTime: props.filters.startTime || '',
  endTime: props.filters.endTime || ''
})
const timeRange = ref([])
const tagOptions = ref([])
const projectOptions = ref([])

/** 类型筛选下拉：全量含停用项，停用项追加 i18n「（已停用）」后缀（Q6，可选中用于查旧数据） */
const typeFilterOptions = computed(() =>
  (issueTypeStore.allOptions || []).map((o) => ({
    id: o.id,
    label: issueTypeLabelI18n(o)
  }))
)

const currentUserId = computed(() => userStore.userInfo && userStore.userInfo.id)

function canEdit(row) {
  return userStore.isAdmin || row.reporterId === currentUserId.value
}
function canDelete(row) {
  return userStore.isAdmin || row.reporterId === currentUserId.value
}

function buildParams() {
  const p = { page: page.value, size: size.value }
  if (filters.status !== '' && filters.status !== null && filters.status !== undefined)
    p.status = filters.status
  if (filters.typeId !== '' && filters.typeId !== null && filters.typeId !== undefined)
    p.typeId = filters.typeId
  if (
    filters.severity !== '' &&
    filters.severity !== null &&
    filters.severity !== undefined
  )
    p.severity = filters.severity
  if (filters.projectId !== '' && filters.projectId !== null && filters.projectId !== undefined)
    p.projectId = filters.projectId
  if (filters.tags && filters.tags.length) p.tag = filters.tags.join(',')
  if (filters.version) p.version = filters.version
  if (filters.keyword) p.keyword = filters.keyword
  if (filters.startTime) p.startDate = filters.startTime
  if (filters.endTime) p.endDate = filters.endTime
  if (props.scope) p.scope = props.scope
  return p
}

async function fetchData() {
  loading.value = true
  try {
    const res = await pageIssues(buildParams())
    list.value = (res && res.list) || []
    total.value = (res && res.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onResetFilter() {
  Object.assign(filters, {
    status: '',
    typeId: null,
    severity: '',
    projectId: '',
    tags: [],
    version: '',
    keyword: '',
    startTime: '',
    endTime: ''
  })
  timeRange.value = []
  page.value = 1
  fetchData()
}

function onDelete(row) {
  ElMessageBox.confirm(
    t('common.msg.deleteConfirm', { name: row.issueNo }),
    t('common.msg.tip'),
    { type: 'warning' }
  )
    .then(async () => {
      await deleteIssue(row.id)
      ElMessage.success(t('issue.msg.deleteSuccess'))
      fetchData()
    })
    .catch(() => {})
}

watch(timeRange, (val) => {
  filters.startTime = val && val[0] ? val[0] : ''
  filters.endTime = val && val[1] ? val[1] : ''
})

onMounted(async () => {
  try {
    await issueTypeStore.fetchAllOptions()
  } catch (e) {
    // 类型下拉加载失败不阻塞列表
  }
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
  fetchData()
})

defineExpose({ fetchData })
</script>

<style scoped>
.issue-table {
  width: 100%;
}
.filter-card {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

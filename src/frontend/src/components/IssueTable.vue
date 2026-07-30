<template>
  <div class="issue-table">
    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="s in STATUS_OPTIONS"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select
            v-model="filters.severity"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="s in SEVERITY_OPTIONS"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select
            v-model="filters.projectId"
            placeholder="全部"
            clearable
            filterable
            style="width: 160px"
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
        <el-form-item label="标签">
          <el-select
            v-model="filters.tags"
            multiple
            collapse-tags
            placeholder="全部"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="t in tagOptions"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input
            v-model="filters.version"
            placeholder="如 v1.2.0"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="标题/描述"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">查询</el-button>
          <el-button @click="onResetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
      <el-table-column prop="issueNo" label="编号" width="150" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="严重等级" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)" effect="light">
            {{ severityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="light">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="项目" min-width="120" show-overflow-tooltip />
      <el-table-column prop="reporterName" label="提交人" width="110" show-overflow-tooltip />
      <el-table-column prop="assigneeName" label="处理人" width="110" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="emit('view', row)"
            >查看</el-button
          >
          <el-button
            v-if="canEdit(row)"
            link
            type="primary"
            size="small"
            @click="emit('edit', row)"
            >编辑</el-button
          >
          <el-button
            link
            type="warning"
            size="small"
            @click="emit('view', row)"
            >流转</el-button
          >
          <el-button
            v-if="canDelete(row)"
            link
            type="danger"
            size="small"
            @click="onDelete(row)"
            >删除</el-button
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  STATUS_OPTIONS,
  SEVERITY_OPTIONS,
  statusLabel,
  severityLabel,
  statusTagType,
  severityTagType,
  formatDate
} from '@/utils/format'
import { pageIssues, deleteIssue } from '@/api/issue'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import { useUserStore } from '@/store/user'

const props = defineProps({
  scope: { type: String, default: 'all' }, // 'mine' | 'all'
  filters: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['view', 'edit'])

const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const filters = reactive({
  status: props.filters.status ?? '',
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
  ElMessageBox.confirm(`确认删除问题 ${row.issueNo}？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteIssue(row.id)
      ElMessage.success('已删除')
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
    const tags = await listTags()
    tagOptions.value = (tags || []).map((t) => ({ label: t.name, value: t.name }))
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

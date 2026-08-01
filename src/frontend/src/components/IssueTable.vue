<template>
  <div class="issue-table" :class="{ 'issue-table--mobile': isMobile }">
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
        <!-- Phase7 T3：优先级筛选（固定枚举 0高/1中/2低，不走字典） -->
        <el-form-item :label="t('issue.form.priority')">
          <el-select
            v-model="filters.priority"
            :placeholder="t('common.status.all')"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="p in priorityOptions"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <!-- Phase7 T3：来源筛选（字典 ISSUE_SOURCE，含停用项并追加「（已停用）」后缀） -->
        <el-form-item :label="t('issue.form.source')">
          <el-select
            v-model="filters.source"
            :placeholder="t('common.status.all')"
            clearable
            filterable
            style="width: 150px"
          >
            <el-option
              v-for="s in sourceFilterOptions"
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
          <!-- Phase7 T3：按当前筛选条件导出 Excel（后端上限 5000 行） -->
          <el-button
            v-if="exportable"
            :icon="Download"
            :loading="exporting"
            @click="onExport"
            >{{ t('issue.action.exportExcel') }}</el-button
          >
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ===== 移动端（<768px）：表格降级为卡片流（Phase9 T12）=====
         列表列数多，小屏横向滚动体验差，此处只保留核心 5 要素：
         编号 / 标题 / 状态 / 更新时间 / 查看 -->
    <div v-if="isMobile" class="issue-cards">
      <IfLoading :loading="loading" :rows="5">
        <IfEmptyState
          v-if="!list.length"
          :scene="emptyScene"
          :title="emptyTitle"
          :description="emptyDesc"
          :action-text="hasActiveFilter ? t('common.action.clearFilter') : ''"
          @action="onResetFilter"
        />
        <template v-else>
        <article
          v-for="row in list"
          :key="row.id"
          class="issue-card"
          tabindex="0"
          role="button"
          @click="emit('view', row)"
          @keydown.enter.prevent="emit('view', row)"
        >
          <header class="issue-card__head">
            <span class="issue-card__no">{{ row.issueNo }}</span>
            <IfTag
              :semantic="statusSemantic(row.status)"
              :label="statusLabelI18n(row.status)"
              size="small"
              dot
            />
          </header>
          <p class="issue-card__title">{{ row.title || t('issue.list.mobile.untitled') }}</p>
          <footer class="issue-card__foot">
            <span class="issue-card__time">
              {{ t('issue.list.mobile.updatedPrefix') }}
              {{ formatDate(row.updatedAt || row.createdAt, 'YYYY-MM-DD HH:mm') }}
            </span>
            <IfButton text type="primary" size="small" @click.stop="emit('view', row)">
              {{ t('common.action.view') }}
            </IfButton>
          </footer>
        </article>
        </template>
      </IfLoading>
    </div>

    <!-- 表格（桌面 / 平板保持原有全列展示，结构不变） -->
    <el-table v-else v-loading="loading" :data="list" border stripe style="width: 100%">
      <el-table-column prop="issueNo" :label="t('issue.list.col.issueNo')" width="150" />
      <el-table-column prop="title" :label="t('issue.list.col.title')" min-width="200" show-overflow-tooltip />
      <!-- Phase6：类型列（停用类型仍正常回显名称） -->
      <el-table-column :label="t('issue.list.col.type')" width="110" align="center">
        <template #default="{ row }">
          <span>{{ row.typeName || '-' }}</span>
        </template>
      </el-table-column>
      <!-- Phase7 T3：来源列（字典名，i18n 优先，回退后端 sourceDesc） -->
      <el-table-column :label="t('issue.list.col.source')" width="110" align="center">
        <template #default="{ row }">
          <span>{{ sourceText(row) }}</span>
        </template>
      </el-table-column>
      <!-- Phase7 T3：优先级列（带色 Tag，渲染风格与严重等级一致） -->
      <el-table-column :label="t('issue.list.col.priority')" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            v-if="row.priority !== null && row.priority !== undefined"
            :type="priorityTagType(row.priority)"
            effect="light"
          >
            {{ priorityLabelI18n(row.priority) }}
          </el-tag>
          <span v-else>-</span>
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

      <!-- Phase9 T12：统一空状态（区分「无数据」与「筛选无结果」） -->
      <template #empty>
        <IfEmptyState
          :scene="emptyScene"
          :title="emptyTitle"
          :description="emptyDesc"
          :action-text="hasActiveFilter ? t('common.action.clearFilter') : ''"
          @action="onResetFilter"
        />
      </template>
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
import { Search, Download } from '@element-plus/icons-vue'
import {
  statusTagType,
  severityTagType,
  priorityTagType,
  statusSemantic,
  formatDate
} from '@/utils/format'
import {
  statusLabelI18n,
  severityLabelI18n,
  priorityLabelI18n,
  useStatusOptions,
  useSeverityOptions,
  usePriorityOptions,
  useDictCodeOptions,
  dictCodeLabelI18n,
  issueTypeLabelI18n
} from '@/utils/i18nEnum'
import { pageIssues, deleteIssue, exportIssues } from '@/api/issue'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import { downloadBlob } from '@/utils/exportUtil'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useIssueTypeStore } from '@/store/issueType'
import IfTag from '@/components/base/IfTag.vue'
import IfButton from '@/components/base/IfButton.vue'
import IfLoading from '@/components/base/IfLoading.vue'
import IfEmptyState from '@/components/base/IfEmptyState.vue'

const props = defineProps({
  scope: { type: String, default: 'all' }, // 'mine' | 'all'
  filters: { type: Object, default: () => ({}) },
  /** 是否展示「导出 Excel」按钮（默认展示，可由父页面关闭） */
  exportable: { type: Boolean, default: true }
})
const emit = defineEmits(['view', 'edit'])

const { t } = useI18n()
const userStore = useUserStore()
const appStore = useAppStore()
const issueTypeStore = useIssueTypeStore()
/** Phase9 T12：<768px 走卡片流降级，桌面/平板保持表格 */
const isMobile = computed(() => appStore.isMobile)
const loading = ref(false)
const exporting = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

/** 来源字典类型编码（Phase7 种子：SYSTEM / API_IMPORT / EXCEL_IMPORT / EMAIL / OTHER） */
const SOURCE_DICT_CODE = 'ISSUE_SOURCE'

const statusOptions = useStatusOptions()
const severityOptions = useSeverityOptions()
const priorityOptions = usePriorityOptions()
/** 来源筛选下拉：全量含停用项（value = item_code，与 issue.source 落库口径一致） */
const sourceFilterOptions = useDictCodeOptions(SOURCE_DICT_CODE, true)

const filters = reactive({
  status: props.filters.status ?? '',
  typeId: props.filters.typeId ?? null,
  severity: props.filters.severity ?? '',
  priority: props.filters.priority ?? '',
  source: props.filters.source ?? '',
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

/**
 * 是否存在生效中的筛选条件（Phase9 T12）：
 * 用于区分空状态语义——「一条都没有」还是「筛选后没匹配到」。
 * 注意 status/priority/severity 的 0 是合法值，只能判空串 / null / undefined。
 */
const hasActiveFilter = computed(() => {
  const isSet = (v) => v !== '' && v !== null && v !== undefined
  return Boolean(
    isSet(filters.status) ||
      isSet(filters.typeId) ||
      isSet(filters.severity) ||
      isSet(filters.priority) ||
      isSet(filters.source) ||
      isSet(filters.projectId) ||
      (filters.tags && filters.tags.length) ||
      filters.version ||
      filters.keyword ||
      filters.startTime ||
      filters.endTime
  )
})

/** 空状态场景：有筛选 → noResult；无筛选 → empty */
const emptyScene = computed(() => (hasActiveFilter.value ? 'noResult' : 'empty'))
const emptyTitle = computed(() =>
  hasActiveFilter.value ? t('issue.list.empty.noResultTitle') : t('issue.list.empty.title')
)
const emptyDesc = computed(() =>
  hasActiveFilter.value ? t('issue.list.empty.noResultDesc') : t('issue.list.empty.desc')
)

/**
 * 来源列文案：i18n（dict.value.ISSUE_SOURCE.{code}）优先，
 * 回退后端 IssueVO.sourceDesc，再回退 '-'。
 * @param {{source?:string,sourceDesc?:string}} row 列表行
 * @returns {string}
 */
function sourceText(row) {
  if (!row || !row.source) return row && row.sourceDesc ? row.sourceDesc : '-'
  return dictCodeLabelI18n(SOURCE_DICT_CODE, row.source, row.sourceDesc) || '-'
}

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
  // Phase7 T3：优先级为整数 0/1/2，0 是合法值，故只排除空串 / null / undefined
  if (
    filters.priority !== '' &&
    filters.priority !== null &&
    filters.priority !== undefined
  )
    p.priority = filters.priority
  // Phase7 T3：来源为 dict_item.item_code 字符串
  if (filters.source !== '' && filters.source !== null && filters.source !== undefined)
    p.source = filters.source
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
    priority: '',
    source: '',
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

/**
 * 导出 Excel：沿用当前筛选条件，剔除分页参数（后端按 EXPORT_MAX_ROWS=5000 截断）。
 * 文件名带日期，便于多次导出区分。
 */
async function onExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const params = buildParams()
    delete params.page
    delete params.size
    const blob = await exportIssues(params)
    const stamp = new Date().toISOString().slice(0, 10)
    downloadBlob(blob, `issues-${stamp}.xlsx`)
    ElMessage.success(t('issue.msg.exportSuccess'))
  } catch (e) {
    ElMessage.error(t('issue.msg.exportFail'))
  } finally {
    exporting.value = false
  }
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

// BUG-07：父组件外部传入的 filters 变化（如工作台卡片点击跳转带 status）需即时反映并重新拉取
watch(
  () => props.filters,
  (nv) => {
    if (nv) {
      Object.assign(filters, {
        status: nv.status ?? '',
        typeId: nv.typeId ?? null,
        severity: nv.severity ?? '',
        priority: nv.priority ?? '',
        source: nv.source ?? '',
        projectId: nv.projectId ?? '',
        tags: nv.tags || [],
        version: nv.version || '',
        keyword: nv.keyword || '',
        startTime: nv.startTime || '',
        endTime: nv.endTime || ''
      })
    }
    page.value = 1
    fetchData()
  },
  { deep: true }
)

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
  margin-bottom: var(--if-space-sm);
  border-radius: var(--if-radius-sm);
}
.pager {
  margin-top: var(--if-space-sm);
  display: flex;
  justify-content: flex-end;
}

/* ===== Phase9 T12：移动端卡片流 ===== */
.issue-cards {
  display: flex;
  flex-direction: column;
  gap: var(--if-space-sm);
}

.issue-card {
  display: flex;
  flex-direction: column;
  gap: var(--if-space-xs);
  padding: var(--if-space-sm) var(--if-space-md);
  background: var(--bg-container);
  border: 1px solid var(--border-color);
  border-radius: var(--if-radius-sm);
  box-shadow: var(--if-shadow-sm);
  cursor: pointer;
  transition: box-shadow var(--if-transition-fast), border-color var(--if-transition-fast);
}

.issue-card:hover,
.issue-card:focus-visible {
  box-shadow: var(--if-shadow-md);
  outline: none;
  border-color: var(--theme-color);
}

.issue-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--if-space-sm);
}

.issue-card__no {
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.issue-card__title {
  margin: 0;
  font-size: var(--if-font-base);
  font-weight: var(--if-weight-medium);
  line-height: var(--if-line-base);
  color: var(--text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.issue-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--if-space-sm);
}

.issue-card__time {
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
}

/* 移动端筛选区：inline 表单改为整行堆叠，控件占满宽度便于点按 */
@media (max-width: 767px) {
  .issue-table--mobile .filter-card :deep(.el-form-item) {
    display: flex;
    width: 100%;
    margin-right: 0;
    margin-bottom: var(--if-space-sm);
  }

  .issue-table--mobile .filter-card :deep(.el-form-item__content) {
    flex: 1;
    min-width: 0;
  }

  .issue-table--mobile .filter-card :deep(.el-select),
  .issue-table--mobile .filter-card :deep(.el-input),
  .issue-table--mobile .filter-card :deep(.el-date-editor) {
    width: 100% !important;
  }

  .pager {
    justify-content: center;
  }

  .pager :deep(.el-pagination__jump),
  .pager :deep(.el-pagination__sizes) {
    display: none;
  }
}
</style>

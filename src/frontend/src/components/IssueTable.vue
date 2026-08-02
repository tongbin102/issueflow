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
        <!-- Phase9：问题类型筛选改走字典 ISSUE_TYPE（value = item_code，与 issue.type_code 落库口径一致）；
             全量含停用项，停用项追加「（已停用）」后缀——历史数据可能引用已停用类型，需可选中回查 -->
        <el-form-item :label="t('issue.list.col.type')">
          <el-select
            v-model="filters.typeCode"
            :placeholder="t('common.status.all')"
            clearable
            filterable
            style="width: 150px"
          >
            <el-option
              v-for="tp in typeFilterOptions"
              :key="tp.value"
              :label="tp.label"
              :value="tp.value"
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

    <!-- 表格（桌面 / 平板）：列由 displayColumns 动态渲染，支持列配置显隐与排序 -->
    <el-table v-else v-loading="loading" :data="list" border stripe style="width: 100%">
      <template v-for="col in displayColumns" :key="col.key">
        <!-- 编号 -->
        <el-table-column
          v-if="col.key === 'issueNo'"
          prop="issueNo"
          :label="t('issue.list.col.issueNo')"
          width="150"
        />
        <!-- 标题 -->
        <el-table-column
          v-else-if="col.key === 'title'"
          prop="title"
          :label="t('issue.list.col.title')"
          min-width="200"
          show-overflow-tooltip
        />
        <!-- Phase9：类型列走字典文案（i18n 优先，回退后端 typeName）；停用类型仍正常回显名称 -->
        <el-table-column
          v-else-if="col.key === 'type'"
          :label="t('issue.list.col.type')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ typeText(row) }}</span>
          </template>
        </el-table-column>
        <!-- Phase7 T3：来源列（字典名，i18n 优先，回退后端 sourceDesc） -->
        <el-table-column
          v-else-if="col.key === 'source'"
          :label="t('issue.list.col.source')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ sourceText(row) }}</span>
          </template>
        </el-table-column>
        <!-- Phase7 T3：优先级列（带色 Tag，渲染风格与严重等级一致） -->
        <el-table-column
          v-else-if="col.key === 'priority'"
          :label="t('issue.list.col.priority')"
          width="100"
          align="center"
        >
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
        <el-table-column
          v-else-if="col.key === 'severity'"
          :label="t('issue.list.col.severity')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="severityTagType(row.severity)" effect="light">
              {{ severityLabelI18n(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-else-if="col.key === 'status'"
          :label="t('issue.list.col.status')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ statusLabelI18n(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-else-if="col.key === 'project'"
          prop="projectName"
          :label="t('issue.list.col.project')"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column
          v-else-if="col.key === 'reporter'"
          prop="reporterName"
          :label="t('issue.list.col.reporter')"
          width="110"
          show-overflow-tooltip
        />
        <el-table-column
          v-else-if="col.key === 'assignee'"
          prop="assigneeName"
          :label="t('issue.list.col.assignee')"
          width="110"
          show-overflow-tooltip
        />
        <el-table-column
          v-else-if="col.key === 'createdAt'"
          :label="t('issue.list.col.createdAt')"
          width="170"
        >
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <!-- 自定义列（来自 field_config，visibleInList=true 的非系统字段） -->
        <el-table-column
          v-else
          :label="col.label"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ formatCustomValue(row, col) }}</template>
        </el-table-column>
      </template>

      <!-- 操作列（固定右侧，不受列配置影响） -->
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
  DICT_TYPE
} from '@/utils/i18nEnum'
import { pageIssues, deleteIssue, exportIssues } from '@/api/issue'
import { listTags } from '@/api/tag'
import { listProjectOptions } from '@/api/project'
import { downloadBlob } from '@/utils/exportUtil'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useFieldSchemaStore } from '@/store/fieldSchema'
import {
  useColumnPreferences,
  BUILTIN_COLUMN_DEFS
} from '@/composables/useColumnPreferences'
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
/** Phase9 T12：<768px 走卡片流降级，桌面/平板保持表格 */
const isMobile = computed(() => appStore.isMobile)
const loading = ref(false)
const exporting = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

/** 来源字典类型编码（Phase7 种子：SYSTEM / API_IMPORT / EXCEL_IMPORT / EMAIL / OTHER） */
const SOURCE_DICT_CODE = DICT_TYPE.ISSUE_SOURCE
/** 问题类型字典编码（Phase9：由 issue_type 表迁入字典，dict_code = ISSUE_TYPE） */
const TYPE_DICT_CODE = DICT_TYPE.ISSUE_TYPE

const statusOptions = useStatusOptions()
const severityOptions = useSeverityOptions()
const priorityOptions = usePriorityOptions()
/** 来源筛选下拉：全量含停用项（value = item_code，与 issue.source 落库口径一致） */
const sourceFilterOptions = useDictCodeOptions(SOURCE_DICT_CODE, true)
/**
 * 类型筛选下拉：全量含停用项（value = item_code，与 issue.type_code 落库口径一致）。
 */
const typeFilterOptions = useDictCodeOptions(TYPE_DICT_CODE, true)

const filters = reactive({
  status: props.filters.status ?? '',
  typeCode: props.filters.typeCode ?? '',
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

// ============================ 列配置（Phase9 动态字段）============================

/** 字段渲染契约 store（缓存 /field-configs/schema，供表单与列表共享） */
const fieldSchemaStore = useFieldSchemaStore()
/** 列偏好（单例 composable，与 UserIssueList / ColumnConfigDrawer 共享） */
const columnPrefs = useColumnPreferences()

/**
 * 自定义列定义：从 field_config schema 中提取 visibleInList=true 的非系统字段。
 * 降级策略：schema 未加载 / 接口不可用时返回空数组，仅展示内置列。
 */
const customColumnDefs = computed(() => {
  const fields = fieldSchemaStore.customFields
  if (!fields || fields.length === 0) return []
  return fields
    .filter((f) => f.visibleInList === true && f.enabled !== false)
    .map((f) => ({
      key: `cf_${f.code}`,
      label: f.name || f.code,
      isCustom: true,
      fieldCode: f.code,
      fieldType: f.type
    }))
})

/** 全部可用列（内置 + 自定义），用于排序与渲染 */
const allColumnDefs = computed(() => [...BUILTIN_COLUMN_DEFS, ...customColumnDefs.value])

/**
 * 实际渲染的列：按偏好排序 + 过滤可见列。
 * 偏好为空时全部可见、按默认顺序（内置列在前、自定义列在后）。
 */
const displayColumns = computed(() => {
  const all = allColumnDefs.value
  const allKeys = all.map((c) => c.key)
  const orderedKeys = columnPrefs.getOrderedKeys(allKeys)

  return orderedKeys
    .map((key) => all.find((c) => c.key === key))
    .filter((col) => col && columnPrefs.isColumnVisible(col.key))
})

/**
 * 格式化自定义列的单元格值。
 * 降级策略：优先取 row.customFields[code]，回退 row[code]（兼容后端可能扁平化返回）。
 *
 * @param {object} row 列表行数据
 * @param {object} col 列定义（含 fieldCode / fieldType）
 * @returns {string}
 */
function formatCustomValue(row, col) {
  if (!row || !col || !col.fieldCode) return '-'
  const raw = row.customFields
    ? row.customFields[col.fieldCode]
    : row[col.fieldCode]
  if (raw === null || raw === undefined || raw === '') return '-'
  if (col.fieldType === 'DATE') {
    return formatDate(raw, 'YYYY-MM-DD')
  }
  if (col.fieldType === 'DATETIME') {
    return formatDate(raw, 'YYYY-MM-DD HH:mm')
  }
  if (col.fieldType === 'NUMBER') {
    return String(raw)
  }
  if (typeof raw === 'boolean') {
    return raw ? t('common.status.enabled') : t('common.status.disabled')
  }
  if (Array.isArray(raw)) {
    return raw.join(', ')
  }
  if (typeof raw === 'object') {
    return JSON.stringify(raw)
  }
  return String(raw)
}

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
      isSet(filters.typeCode) ||
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

/**
 * 类型列文案：i18n（dict.value.ISSUE_TYPE.{code}）优先，
 * 回退后端 IssueVO.typeName（由 dictService.itemNameMap 批量回填，无 N+1），再回退 '-'。
 * @param {{typeCode?:string,typeName?:string}} row 列表行
 * @returns {string}
 */
function typeText(row) {
  if (!row || !row.typeCode) return row && row.typeName ? row.typeName : '-'
  return dictCodeLabelI18n(TYPE_DICT_CODE, row.typeCode, row.typeName) || '-'
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
  // Phase9：类型筛选为 dict_item.item_code 字符串，命中后端 idx_issue_type_code 索引
  if (filters.typeCode !== '' && filters.typeCode !== null && filters.typeCode !== undefined)
    p.typeCode = filters.typeCode
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
    typeCode: '',
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
        typeCode: nv.typeCode ?? '',
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
  // 类型 / 来源下拉由 useDictCodeOptions 在 setup 阶段自行触发字典分片加载，此处无需再拉
  // 列配置：加载字段渲染契约（含自定义字段定义），失败时降级为仅内置列
  fieldSchemaStore.loadSchema().catch(() => {})
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

defineExpose({ fetchData, allColumnDefs })
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

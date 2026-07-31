<template>
  <!-- Tab3 活动记录：类型分段器 + 时间线（桌面）/卡片（移动）+ 分页（ARCH §2.5-99）
       LOGIN 段走 /api/profile/login-logs 表格；ALL / ISSUE 段走 /api/profile/activities 时间线 -->
  <div class="profile-activity">
    <div class="act-toolbar">
      <el-radio-group v-model="type" size="default" @change="onTypeChange">
        <el-radio-button value="ALL">{{ t('profile.activity.typeAll') }}</el-radio-button>
        <el-radio-button value="LOGIN">{{ t('profile.activity.typeLogin') }}</el-radio-button>
        <el-radio-button value="ISSUE">{{ t('profile.activity.typeIssue') }}</el-radio-button>
      </el-radio-group>

      <div class="act-toolbar__right">
        <el-date-picker
          v-if="type !== 'LOGIN'"
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          :start-placeholder="t('profile.activity.startDate')"
          :end-placeholder="t('profile.activity.endDate')"
          :range-separator="'~'"
          class="act-date"
          @change="onFilterChange"
        />
        <el-button :icon="Refresh" :loading="loading" @click="fetchData">
          {{ t('common.action.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- ===== 登录日志表格（桌面） ===== -->
    <template v-if="type === 'LOGIN'">
      <el-table
        v-if="!isMobile"
        v-loading="loading"
        :data="logRows"
        border
        stripe
        size="default"
        :empty-text="t('profile.activity.empty')"
      >
        <el-table-column prop="time" :label="t('profile.activity.time')" min-width="170" />
        <el-table-column prop="ip" :label="t('profile.activity.ip')" min-width="130" />
        <el-table-column prop="browser" :label="t('profile.activity.browser')" min-width="130" />
        <el-table-column prop="os" :label="t('profile.activity.os')" min-width="130" />
        <el-table-column :label="t('profile.activity.result')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="light">
              {{ row.success ? t('profile.activity.success') : t('profile.activity.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="failReason"
          :label="t('profile.activity.failReason')"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.failReason || '-' }}</template>
        </el-table-column>
      </el-table>

      <!-- 登录日志卡片（移动端，表格横向滚动体验差） -->
      <div v-else v-loading="loading" class="act-cards">
        <el-empty v-if="!logRows.length" :description="t('profile.activity.empty')" />
        <div v-for="row in logRows" :key="row.id" class="act-card">
          <div class="act-card__head">
            <span class="act-card__time">{{ row.time }}</span>
            <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="light">
              {{ row.success ? t('profile.activity.success') : t('profile.activity.failed') }}
            </el-tag>
          </div>
          <div class="act-card__line">{{ t('profile.activity.ip') }}: {{ row.ip || '-' }}</div>
          <div class="act-card__line">
            {{ t('profile.activity.browser') }}: {{ row.browser || '-' }}
          </div>
          <div class="act-card__line">{{ t('profile.activity.os') }}: {{ row.os || '-' }}</div>
          <div v-if="row.failReason" class="act-card__line act-card__line--warn">
            {{ t('profile.activity.failReason') }}: {{ row.failReason }}
          </div>
        </div>
      </div>
    </template>

    <!-- ===== 归并时间线（ALL / ISSUE） ===== -->
    <template v-else>
      <div v-loading="loading" class="act-timeline-wrap">
        <el-empty v-if="!activityRows.length" :description="t('profile.activity.empty')" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(row, index) in activityRows"
            :key="`${row.type}-${row.time}-${index}`"
            :timestamp="row.time"
            placement="top"
            :type="timelineType(row)"
            :hollow="row.type === 'LOGIN'"
          >
            <div class="act-item">
              <div class="act-item__head">
                <el-tag :type="row.type === 'LOGIN' ? 'info' : 'primary'" size="small" effect="plain">
                  {{
                    row.type === 'LOGIN'
                      ? t('profile.activity.loginEvent')
                      : t('profile.activity.issueEvent')
                  }}
                </el-tag>
                <span class="act-item__title">{{ row.title || '-' }}</span>
                <el-tag
                  v-if="row.type === 'LOGIN'"
                  :type="row.success ? 'success' : 'danger'"
                  size="small"
                  effect="light"
                >
                  {{ row.success ? t('profile.activity.success') : t('profile.activity.failed') }}
                </el-tag>
              </div>
              <div v-if="row.detail" class="act-item__detail">{{ row.detail }}</div>
              <div class="act-item__meta">
                <span v-if="row.ip">{{ t('profile.activity.ip') }}: {{ row.ip }}</span>
                <span v-if="row.device">{{ t('profile.activity.device') }}: {{ row.device }}</span>
                <el-button
                  v-if="row.type === 'ISSUE' && row.issueId"
                  link
                  type="primary"
                  size="small"
                  @click="openIssue(row.issueId)"
                >
                  {{ t('profile.activity.viewIssue') }}
                  <span v-if="row.issueNo">&nbsp;({{ row.issueNo }})</span>
                </el-button>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </template>

    <div class="act-pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        :small="isMobile"
        background
        @size-change="onSizeChange"
        @current-change="fetchData"
      />
    </div>

    <!-- 问题详情抽屉：时间线 ISSUE 行点击复用（ARCH T5 实现要点 5） -->
    <IssueDetailDrawer
      v-model="detailVisible"
      :issue-id="currentIssueId"
      :flow-config="flowConfig"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { pageActivities, pageLoginLogs } from '@/api/profile'

const { t } = useI18n()
const appStore = useAppStore()

const isMobile = computed(() => appStore.isMobile)

/** 分段类型：ALL / LOGIN / ISSUE */
const type = ref('ALL')
const dateRange = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

/** LOGIN 段数据源（LoginLogVO） */
const logRows = ref([])
/** ALL / ISSUE 段数据源（ActivityVO） */
const activityRows = ref([])

const detailVisible = ref(false)
const currentIssueId = ref(null)
const flowConfig = ref({ rejectEnabled: true, reopenEnabled: true })

/** 时间线节点色：登录失败标红，登录成功灰，问题操作走主色 */
function timelineType(row) {
  if (row.type === 'LOGIN') {
    return row.success ? 'info' : 'danger'
  }
  return 'primary'
}

function openIssue(issueId) {
  currentIssueId.value = issueId
  detailVisible.value = true
}

/**
 * 拉取当前分段数据。
 * LOGIN 段用精确分页端点 /login-logs；ALL / ISSUE 段用归并端点 /activities。
 */
async function fetchData() {
  loading.value = true
  try {
    if (type.value === 'LOGIN') {
      const res = await pageLoginLogs({ page: page.value, size: size.value })
      logRows.value = (res && res.list) || []
      total.value = (res && Number(res.total)) || 0
      activityRows.value = []
    } else {
      const range = Array.isArray(dateRange.value) ? dateRange.value : []
      const res = await pageActivities({
        page: page.value,
        size: size.value,
        type: type.value,
        startDate: range[0] || undefined,
        endDate: range[1] || undefined
      })
      activityRows.value = (res && res.list) || []
      total.value = (res && Number(res.total)) || 0
      logRows.value = []
    }
  } catch (e) {
    logRows.value = []
    activityRows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onTypeChange() {
  page.value = 1
  fetchData()
}

function onFilterChange() {
  page.value = 1
  fetchData()
}

function onSizeChange() {
  page.value = 1
  fetchData()
}

onMounted(fetchData)

defineExpose({ fetchData })
</script>

<style scoped>
.act-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.act-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.act-date {
  width: 260px;
}

.act-timeline-wrap {
  min-height: 160px;
}

.act-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.act-item__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  word-break: break-all;
}

.act-item__detail {
  font-size: 13px;
  color: var(--text-regular);
  margin-top: 4px;
  word-break: break-all;
}

.act-item__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.act-cards {
  min-height: 160px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.act-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  background: var(--bg-container);
}

.act-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.act-card__time {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.act-card__line {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.8;
  word-break: break-all;
}

.act-card__line--warn {
  /* T8：走主题变量，避免暗色/蓝色主题下失真 */
  color: var(--el-color-danger);
}

.act-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .act-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .act-toolbar__right {
    flex-direction: column;
    align-items: stretch;
  }

  .act-date {
    width: 100%;
  }

  .act-pager {
    justify-content: center;
  }
}
</style>

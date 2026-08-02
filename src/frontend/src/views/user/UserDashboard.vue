<template>
  <div class="user-dashboard" :class="{ 'if-mobile-scope': appStore.isMobile }">
    <!-- ===== 页头：问候语 + 快捷提交 ===== -->
    <IfPageHeader :title="greeting" :subtitle="t('dashboard.user.statsTitle')">
      <template #actions>
        <IfButton type="primary" :icon="Plus" @click="goCreate">
          {{ t('issue.action.submitNew') }}
        </IfButton>
      </template>
    </IfPageHeader>

    <!-- ===== 分区 1：数据概览（状态卡，点击带 status 跳「我的问题」）===== -->
    <section class="dash-section">
      <h2 class="if-section-title">{{ t('dashboard.section.overview') }}</h2>
      <!-- BUG-06 保留：状态卡整行 v-loading -->
      <el-row :gutter="16" v-loading="loading" class="dash-section__body">
        <el-col v-for="card in cards" :key="card.status" :xs="12" :sm="8" :md="4">
          <!-- BUG-02 保留：点击跳转 + cursor:pointer -->
          <IfCard
            class="stat-card"
            hoverable
            clickable
            body-padding="16px"
            :aria-label="`${card.label} ${t('dashboard.card.clickHint')}`"
            @click="goList(card.status)"
            style="cursor: pointer"
          >
            <div class="stat-label">
              <span class="dot" :style="{ background: statusColor(card.status) }"></span>
              {{ card.label }}
            </div>
            <div class="stat-value" :style="{ color: statusColor(card.status) }">
              {{ card.count }}
            </div>
          </IfCard>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <IfCard class="stat-card total" hoverable body-padding="16px">
            <div class="stat-label">{{ t('dashboard.user.submittedTotal') }}</div>
            <div class="stat-value">{{ total }}</div>
          </IfCard>
        </el-col>
      </el-row>
    </section>

    <!-- ===== 分区 2：快捷入口 ===== -->
    <section class="dash-section">
      <h2 class="if-section-title">{{ t('dashboard.section.quickEntry') }}</h2>
      <el-row :gutter="16" class="dash-section__body">
        <el-col v-for="entry in quickEntries" :key="entry.key" :xs="12" :sm="12" :md="6">
          <IfCard
            class="quick-card"
            hoverable
            clickable
            body-padding="16px"
            @click="entry.handler"
          >
            <div class="quick-card__inner">
              <span class="quick-card__icon">
                <el-icon :size="20"><component :is="entry.icon" /></el-icon>
              </span>
              <span class="quick-card__text">
                <span class="quick-card__title">{{ entry.title }}</span>
                <span class="quick-card__desc">{{ entry.desc }}</span>
              </span>
              <el-icon class="quick-card__arrow"><ArrowRight /></el-icon>
            </div>
          </IfCard>
        </el-col>
      </el-row>
    </section>

    <!-- ===== 分区 3：我的最近问题 ===== -->
    <section class="dash-section">
      <h2 class="if-section-title">{{ t('dashboard.section.myRecent') }}</h2>
      <IfCard class="dash-section__body" body-padding="0">
        <template #extra>
          <IfButton text type="primary" @click="goList()">
            {{ t('dashboard.recent.viewAll') }}
          </IfButton>
        </template>

        <IfLoading :loading="recentLoading" :rows="4">
          <IfEmptyState
            v-if="!recentList.length"
            scene="empty"
            :title="t('dashboard.recent.empty')"
            :description="t('dashboard.recent.emptyDesc')"
            :action-text="t('dashboard.recent.emptyAction')"
            @action="goCreate"
          />
          <ul v-else class="recent-list">
            <li
              v-for="row in recentList"
              :key="row.id"
              class="recent-item if-clickable"
              tabindex="0"
              role="button"
              @click="openDetail(row)"
              @keydown.enter.prevent="openDetail(row)"
            >
              <span class="recent-item__no">{{ row.issueNo }}</span>
              <span class="recent-item__title if-text-ellipsis">
                {{ row.title || t('issue.list.mobile.untitled') }}
              </span>
              <IfTag
                class="recent-item__tag"
                :semantic="statusSemantic(row.status)"
                :label="statusLabelI18n(row.status)"
                size="small"
                dot
              />
              <span class="recent-item__time">
                {{ formatDate(row.updatedAt || row.createdAt, 'YYYY-MM-DD HH:mm') }}
              </span>
            </li>
          </ul>
        </IfLoading>
      </IfCard>
    </section>

    <!-- ===== 分区 4：提交趋势 ===== -->
    <!-- BUG-06 保留：趋势图卡片 v-loading -->
    <el-card class="page-card trend-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="card-head">
          <span class="if-text-h3">{{ t('dashboard.user.myTrend') }}</span>
          <!-- BUG-02 保留：无参调用必须带括号，避免把事件对象当 status 传入 -->
          <el-button text type="primary" @click="goList()">{{ t('dashboard.user.viewMy') }}</el-button>
        </div>
      </template>
      <TrendChart :data="trend" :title="t('dashboard.user.submitTrend')" />
    </el-card>

    <!-- 详情抽屉：最近问题点击直接查看，避免跳页丢失上下文 -->
    <IssueDetailDrawer v-model="detailVisible" :issue-id="detailId" @updated="loadRecent" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  ArrowRight,
  DataAnalysis,
  Document,
  Plus,
  User as UserIcon
} from '@element-plus/icons-vue'
import { statusColor, statusSemantic, formatDate } from '@/utils/format'
import { useStatusOptions, statusLabelI18n } from '@/utils/i18nEnum'
import { overview } from '@/api/dashboard'
import { pageIssues } from '@/api/issue'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import TrendChart from '@/components/charts/TrendChart.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import IfPageHeader from '@/components/base/IfPageHeader.vue'
import IfCard from '@/components/base/IfCard.vue'
import IfButton from '@/components/base/IfButton.vue'
import IfTag from '@/components/base/IfTag.vue'
import IfLoading from '@/components/base/IfLoading.vue'
import IfEmptyState from '@/components/base/IfEmptyState.vue'

const { t } = useI18n()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const statusOptions = useStatusOptions()

const countMap = ref({})
const cards = computed(() =>
  statusOptions.value.map((s) => ({
    status: s.value,
    label: s.label,
    count: countMap.value[s.value] || 0
  }))
)
const total = ref(0)
const loading = ref(false)
const trend = ref([])

/** 我的最近问题（Phase9 T10 新增分区） */
const recentList = ref([])
const recentLoading = ref(false)
const detailVisible = ref(false)
const detailId = ref(null)

/** 按当前时段生成问候语：<12 上午 / <18 下午 / 其余晚上。 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  const key = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'
  const name = userStore.displayName || userStore.realName || ''
  return name ? `${t(`dashboard.greeting.${key}`)}，${name}` : t(`dashboard.greeting.${key}`)
})

/** 快捷入口配置（Phase9 T10 新增分区，文案全部走 i18n）。 */
const quickEntries = computed(() => [
  {
    key: 'create',
    icon: Plus,
    title: t('dashboard.quick.create'),
    desc: t('dashboard.quick.createDesc'),
    handler: goCreate
  },
  {
    key: 'myIssues',
    icon: Document,
    title: t('dashboard.quick.myIssues'),
    desc: t('dashboard.quick.myIssuesDesc'),
    handler: () => goList()
  },
  {
    key: 'stats',
    icon: DataAnalysis,
    title: t('dashboard.quick.stats'),
    desc: t('dashboard.quick.statsDesc'),
    handler: () => router.push({ path: '/user/stats' })
  },
  {
    key: 'profile',
    icon: UserIcon,
    title: t('dashboard.quick.profile'),
    desc: t('dashboard.quick.profileDesc'),
    handler: () => router.push({ path: '/user/profile' })
  }
])

// BUG-02：统计卡片点击跳转到「我的问题」并带上 status 筛选；无 status 时跳全量列表
// 回归修复：加数值守卫，避免调用方误把事件对象（MouseEvent/PointerEvent）当 status 传入，
// 导致 URL ?status=[object PointerEvent] → 后端 Number 解析 NaN → 400。
function goList(status) {
  if (status == null || Number.isNaN(Number(status))) {
    router.push({ path: '/user/my-issues' })
    return
  }
  router.push({ path: '/user/my-issues', query: { status: Number(status) } })
}

/**
 * 快捷提交：跳「我的问题」并带 create=1，由列表页自动打开新建抽屉。
 * （Phase6 起提交入口已收敛到列表页抽屉，无独立提交路由）
 */
function goCreate() {
  router.push({ path: '/user/my-issues', query: { create: 1 } })
}

/**
 * 打开问题详情抽屉。
 * @param {{id:number|string}} row 最近问题行
 */
function openDetail(row) {
  if (!row || row.id == null) return
  detailId.value = row.id
  detailVisible.value = true
}

// BUG-06：加载态以 loading.value = true 起始、finally 收尾，配合模板 <el-row v-loading>
async function load() {
  loading.value = true
  try {
    const data = await overview({})
    const dist = (data && data.statusDistribution) || []
    const map = {}
    dist.forEach((d) => {
      map[Number(d.status)] = Number(d.count) || 0
    })
    countMap.value = map
    total.value = Object.values(map).reduce((a, b) => a + b, 0)
    trend.value = (data && (data.trendByDay || data.trend)) || []
  } catch (e) {
    const code = e && e.code
    // 401/403 由 request.js 统一跳转登录页 / 403 页，这里不再重复提示
    // 注：catch 内 return 仍会执行下方 finally，loading 态不会泄漏
    if (code === 401 || code === 403) return
    console.error('[UserDashboard] load failed:', e)
    // request.js 已就网络错误/业务错误弹过消息（shown=true）时不再重复弹，
    // 避免部署后后端未就绪时出现两条红色提示
    if (e && e.shown) return
    ElMessage.error((e && e.message) || t('dashboard.admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 加载「我的最近问题」前 5 条（scope=mine，与列表页口径一致）。 */
async function loadRecent() {
  recentLoading.value = true
  try {
    const res = await pageIssues({ page: 1, size: 5, scope: 'mine' })
    recentList.value = (res && res.list) || []
  } catch (e) {
    recentList.value = []
  } finally {
    recentLoading.value = false
  }
}

onMounted(() => {
  load()
  loadRecent()
})
</script>

<style scoped>
.user-dashboard {
  display: flex;
  flex-direction: column;
}

.dash-section {
  margin-bottom: var(--if-space-lg);
}

.dash-section__body {
  margin-top: var(--if-space-sm);
}

/* ===== 统计卡 ===== */
.stat-card {
  margin-bottom: var(--if-space-sm);
}

.stat-label {
  font-size: var(--if-font-sm);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.stat-value {
  font-size: var(--if-font-h1);
  font-weight: var(--if-weight-bold);
  margin-top: var(--if-space-sm);
  line-height: var(--if-line-tight);
}

.stat-card.total .stat-value {
  color: var(--theme-color);
}

/* ===== 快捷入口 ===== */
.quick-card {
  margin-bottom: var(--if-space-sm);
}

.quick-card__inner {
  display: flex;
  align-items: center;
  gap: var(--if-space-sm);
}

.quick-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--if-radius-sm);
  color: var(--theme-color);
  background: var(--if-active-bg);
}

.quick-card__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.quick-card__title {
  font-size: var(--if-font-base);
  font-weight: var(--if-weight-medium);
  color: var(--text-primary);
}

.quick-card__desc {
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-card__arrow {
  color: var(--text-secondary);
  flex-shrink: 0;
}

/* ===== 我的最近问题 ===== */
.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: var(--if-space-sm);
  padding: var(--if-space-sm) var(--if-space-md);
  border-bottom: 1px solid var(--border-color);
  transition: background-color var(--if-transition-fast);
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item:hover {
  background: var(--if-hover-bg);
}

.recent-item__no {
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.recent-item__title {
  flex: 1;
  min-width: 0;
  font-size: var(--if-font-base);
  color: var(--text-primary);
}

.recent-item__tag {
  flex-shrink: 0;
}

.recent-item__time {
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
  flex-shrink: 0;
}

/* ===== 趋势 ===== */
.trend-card {
  margin-bottom: var(--if-space-md);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 平板：快捷入口两列 */
@media (max-width: 1279px) {
  .quick-card__desc {
    display: none;
  }
}

/* 移动端：最近问题改两行紧凑排布，隐藏编号列，保证标题不被挤压 */
@media (max-width: 767px) {
  .dash-section {
    margin-bottom: var(--if-space-md);
  }

  .recent-item {
    flex-wrap: wrap;
    row-gap: var(--if-space-xs);
    min-height: var(--if-touch-size);
  }

  .recent-item__title {
    flex: 1 0 100%;
    order: 1;
  }

  .recent-item__no {
    order: 2;
  }

  .recent-item__tag {
    order: 3;
  }

  .recent-item__time {
    order: 4;
    margin-left: auto;
  }

  .stat-value {
    font-size: var(--if-font-h2);
  }
}
</style>
